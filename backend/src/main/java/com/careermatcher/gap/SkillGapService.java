package com.careermatcher.gap;

import com.careermatcher.aiclient.AiAnalysisException;
import com.careermatcher.aiclient.AiRoadmapClient;
import com.careermatcher.aiclient.dto.AiRoadmap;
import com.careermatcher.aiclient.dto.RoadmapFacts;
import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.ratelimit.RateLimitProperties;
import com.careermatcher.common.ratelimit.RateLimiter;
import com.careermatcher.gap.dto.GapDtos.GapView;
import com.careermatcher.gap.dto.GapDtos.JobRef;
import com.careermatcher.gap.dto.GapDtos.Progress;
import com.careermatcher.gap.dto.GapDtos.ReportPoint;
import com.careermatcher.gap.dto.GapDtos.ReportView;
import com.careermatcher.gap.dto.GapDtos.ResourceView;
import com.careermatcher.gap.dto.GapDtos.RoadmapView;
import com.careermatcher.gap.dto.GapDtos.StepView;
import com.careermatcher.gap.dto.GapDtos.StrengthView;
import com.careermatcher.gap.engine.GapAnalyzer;
import com.careermatcher.gap.engine.GapReport;
import com.careermatcher.gap.engine.GapReport.Priority;
import com.careermatcher.matching.MatchService;
import com.careermatcher.matching.MatchService.Ranking;
import com.careermatcher.matching.engine.MatchingEngine;
import com.careermatcher.skill.CandidateSkillService;
import com.careermatcher.skill.SkillNormalizer;
import com.careermatcher.skill.dto.CandidateSkillResponse;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Skill gap reports for the authenticated candidate: deterministic analysis of their top matches,
 * stored as snapshots, with an optional validated learning roadmap.
 */
@Service
public class SkillGapService {

  private static final Logger log = LoggerFactory.getLogger(SkillGapService.class);
  static final int TARGET_JOBS = 10;
  static final int ROADMAP_SKILLS = 6;
  static final int RESOURCES_PER_SKILL = 3;

  private final MatchService matches;
  private final GapAnalyzer analyzer;
  private final SkillGapReportRepository reports;
  private final SkillResourceRepository resources;
  private final CandidateSkillService candidateSkills;
  private final SkillNormalizer catalog;
  private final AiRoadmapClient aiClient;
  private final RateLimiter rateLimiter;
  private final RateLimitProperties limits;
  private final JsonMapper json;
  private final Clock clock;

  public SkillGapService(
      MatchService matches, MatchingEngine engine, SkillGapReportRepository reports,
      SkillResourceRepository resources, CandidateSkillService candidateSkills, SkillNormalizer catalog,
      AiRoadmapClient aiClient, RateLimiter rateLimiter, RateLimitProperties limits, JsonMapper json, Clock clock) {
    this.matches = matches;
    this.analyzer = new GapAnalyzer(engine);
    this.reports = reports;
    this.resources = resources;
    this.candidateSkills = candidateSkills;
    this.catalog = catalog;
    this.aiClient = aiClient;
    this.rateLimiter = rateLimiter;
    this.limits = limits;
    this.json = json;
    this.clock = clock;
  }

  /** Analyzes the candidate's current top matches and stores the result as a new report. */
  @Transactional
  public ReportView generate(UUID userId) {
    rateLimiter.consume("gap-report", userId.toString(), limits.gapReportPerUser());
    Ranking ranking = matches.rank(userId, TARGET_JOBS);
    Map<UUID, String> categories = new HashMap<>();
    ranking.jobs().forEach(j -> j.job().skills().keySet()
        .forEach(id -> catalog.byId(id).ifPresent(s -> categories.put(id, s.category().name()))));
    GapReport report =
        analyzer.analyze(
            ranking.candidate(),
            ranking.jobs().stream()
                .map(j -> new GapAnalyzer.Target(j.job(), j.company(), j.dataOrigin(), j.result()))
                .toList(),
            categories,
            YearMonth.now(clock.withZone(ZoneOffset.UTC)));
    SkillGapReportEntity entity =
        reports.save(new SkillGapReportEntity(
            userId, report.algorithmVersion(), report.matchAlgorithmVersion(), report.targets().size(),
            report.averageScore(), report.requiredCoverage(), count(report, Priority.CRITICAL),
            count(report, Priority.HIGH), json.writeValueAsString(report), clock.instant()));
    return view(entity, mySkills(userId));
  }

  @Transactional(readOnly = true)
  public Progress progress(UUID userId) {
    List<SkillGapReportEntity> history = reports.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    Set<UUID> mine = mySkills(userId);
    List<ReportPoint> points =
        history.stream()
            .sorted(Comparator.comparing(SkillGapReportEntity::getCreatedAt))
            .map(e -> new ReportPoint(e.getId(), e.getCreatedAt(), e.getAverageScore(), e.getRequiredCoverage(),
                e.getCriticalCount(), e.getHighCount(), parse(e).gaps().size(), e.getRoadmap() != null))
            .toList();
    List<GapReport.SkillGap> important =
        reports.findFirstByUserIdOrderByCreatedAtAsc(userId).map(this::parse).map(GapReport::gaps).orElse(List.of())
            .stream()
            .filter(g -> g.priority() == Priority.CRITICAL || g.priority() == Priority.HIGH)
            .toList();
    return new Progress(
        points,
        history.isEmpty() ? null : history.get(0).getId(),
        important.stream().filter(g -> mine.contains(g.skillId())).map(GapReport.SkillGap::name).toList(),
        important.stream().filter(g -> !mine.contains(g.skillId())).map(GapReport.SkillGap::name).toList(),
        mine.size());
  }

  @Transactional(readOnly = true)
  public ReportView get(UUID userId, UUID reportId) {
    return view(load(userId, reportId), mySkills(userId));
  }

  @Transactional(readOnly = true)
  public ReportView latest(UUID userId) {
    return reports.findFirstByUserIdOrderByCreatedAtDesc(userId)
        .map(e -> view(e, mySkills(userId)))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_GAP_REPORT", "No skill gap analysis yet."));
  }

  /**
   * Returns the report's learning roadmap, generating it once. The roadmap covers the top gaps
   * only, may reference only curated resources, and is rejected if it cites unsupported numbers
   * or makes claims about qualifications or employment outcomes.
   */
  @Transactional
  public ReportView roadmap(UUID userId, UUID reportId) {
    SkillGapReportEntity entity = load(userId, reportId);
    if (entity.getRoadmap() != null) {
      return view(entity, mySkills(userId));
    }
    GapReport report = parse(entity);
    if (report.gaps().isEmpty()) {
      throw new ApiException(HttpStatus.CONFLICT, "NO_GAPS", "There are no skill gaps to build a roadmap for.");
    }
    rateLimiter.consume("roadmap", userId.toString(), limits.roadmapPerUser());
    RoadmapFacts facts = facts(userId, report);
    AiRoadmap roadmap;
    try {
      roadmap = aiClient.roadmap(facts);
    } catch (AiAnalysisException e) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ROADMAP_UNAVAILABLE",
          "The learning roadmap could not be generated right now. Your skill gaps are still accurate.",
          Map.of("reason", e.getCode()));
    }
    List<String> problems = RoadmapGuard.violations(facts, roadmap, json);
    if (!problems.isEmpty()) {
      log.warn("Rejected roadmap for report {}: {}", reportId, problems);
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ROADMAP_UNAVAILABLE",
          "The roadmap could not be verified against your skill gaps, so it was not shown. Please try again.",
          Map.of("reason", "AI_INVALID_OUTPUT"));
    }
    entity.attachRoadmap(json.writeValueAsString(roadmap), roadmap.provider(), roadmap.model(), roadmap.promptVersion(),
        clock.instant());
    reports.saveAndFlush(entity);
    return view(entity, mySkills(userId));
  }

  // ------------------------------------------------------------------ helpers

  private RoadmapFacts facts(UUID userId, GapReport report) {
    List<GapReport.SkillGap> top = report.gaps().stream().limit(ROADMAP_SKILLS).toList();
    Map<UUID, String> titles = report.targets().stream()
        .collect(Collectors.toMap(GapReport.TargetJob::jobId, GapReport.TargetJob::title, (a, b) -> a));
    Map<UUID, List<SkillResource>> res = resourcesFor(top.stream().map(GapReport.SkillGap::skillId).toList());
    return new RoadmapFacts(
        report.targets().size(),
        report.averageScore(),
        report.requiredCoverage(),
        top.stream()
            .map(g -> new RoadmapFacts.Gap(g.name(), g.category(), g.priority().name(), g.requiredIn(), g.preferredIn(),
                g.averageGain(), g.jobIds().stream().map(titles::get).limit(2).toList()))
            .toList(),
        candidateSkills.list(userId).stream().map(CandidateSkillResponse::name).sorted().limit(30).toList(),
        top.stream()
            .flatMap(g -> res.getOrDefault(g.skillId(), List.of()).stream()
                .map(r -> new RoadmapFacts.Resource(r.getId().toString(), g.name(), r.getTitle(), r.getProvider(), r.getResourceType())))
            .toList());
  }

  private Map<UUID, List<SkillResource>> resourcesFor(List<UUID> skillIds) {
    if (skillIds.isEmpty()) return Map.of();
    return resources.findBySkillIdInOrderByResourceTypeAscTitleAsc(skillIds).stream()
        .collect(Collectors.groupingBy(SkillResource::getSkillId,
            Collectors.collectingAndThen(Collectors.toList(), l -> l.stream().limit(RESOURCES_PER_SKILL).toList())));
  }

  private static ResourceView resource(SkillResource r) {
    return new ResourceView(r.getId(), r.getTitle(), r.getProvider(), r.getResourceType(), r.getUrl(), r.getVerifiedOn());
  }

  private ReportView view(SkillGapReportEntity e, Set<UUID> mine) {
    GapReport report = parse(e);
    Map<UUID, GapReport.TargetJob> targets = report.targets().stream()
        .collect(Collectors.toMap(GapReport.TargetJob::jobId, t -> t, (a, b) -> a));
    Map<UUID, List<SkillResource>> res = resourcesFor(report.gaps().stream().map(GapReport.SkillGap::skillId).toList());
    List<GapView> gaps = report.gaps().stream()
        .map(g -> new GapView(g.skillId(), g.name(), g.category(), g.priority().name(), g.requiredIn(), g.preferredIn(),
            g.averageGain(),
            g.jobIds().stream().map(targets::get).filter(java.util.Objects::nonNull).map(SkillGapService::jobRef).toList(),
            mine.contains(g.skillId()),
            res.getOrDefault(g.skillId(), List.of()).stream().map(SkillGapService::resource).toList()))
        .toList();
    RoadmapView roadmap = null;
    if (e.getRoadmap() != null) {
      AiRoadmap r = json.readValue(e.getRoadmap(), AiRoadmap.class);
      Map<String, SkillResource> byId = resources.findAllById(
              r.steps().stream().flatMap(s -> s.resourceIds().stream()).map(UUID::fromString).toList())
          .stream().collect(Collectors.toMap(x -> x.getId().toString(), x -> x));
      Map<String, UUID> skillIdByName = report.gaps().stream()
          .collect(Collectors.toMap(GapReport.SkillGap::name, GapReport.SkillGap::skillId, (a, b) -> a));
      roadmap = new RoadmapView(e.getRoadmapProvider(), e.getRoadmapModel(), r.summary(),
          r.steps().stream()
              .map(s -> new StepView(s.skill(), s.title(), s.why(), s.activities(), s.estimatedWeeks(),
                  s.resourceIds().stream().map(byId::get).filter(java.util.Objects::nonNull).map(SkillGapService::resource).toList(),
                  skillIdByName.containsKey(s.skill()) && mine.contains(skillIdByName.get(s.skill()))))
              .toList(),
          e.getRoadmapCreatedAt());
    }
    return new ReportView(e.getId(), e.getCreatedAt(), e.getAlgorithmVersion(), e.getMatchAlgorithmVersion(),
        e.getTargetJobCount(), e.getAverageScore(), e.getRequiredCoverage(),
        report.targets().stream().map(SkillGapService::jobRef).toList(), gaps,
        report.strengths().stream().map(s -> new StrengthView(s.name(), s.requiredIn())).toList(), roadmap);
  }

  private static JobRef jobRef(GapReport.TargetJob t) {
    return new JobRef(t.jobId(), t.title(), t.company(), t.dataOrigin(), t.score());
  }

  private GapReport parse(SkillGapReportEntity e) {
    return json.readValue(e.getReport(), GapReport.class);
  }

  private SkillGapReportEntity load(UUID userId, UUID reportId) {
    return reports.findByIdAndUserId(reportId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GAP_REPORT_NOT_FOUND", "Report not found."));
  }

  private Set<UUID> mySkills(UUID userId) {
    return candidateSkills.list(userId).stream().map(CandidateSkillResponse::skillId).collect(Collectors.toSet());
  }

  private static int count(GapReport r, Priority p) {
    return (int) r.gaps().stream().filter(g -> g.priority() == p).count();
  }
}
