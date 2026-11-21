package com.careermatcher.matching;

import com.careermatcher.aiclient.AiAnalysisException;
import com.careermatcher.aiclient.AiMatchExplanationClient;
import com.careermatcher.aiclient.dto.AiMatchExplanation;
import com.careermatcher.aiclient.dto.MatchFacts;
import com.careermatcher.common.error.ApiException;
import com.careermatcher.common.ratelimit.RateLimitProperties;
import com.careermatcher.common.ratelimit.RateLimiter;
import com.careermatcher.job.Job;
import com.careermatcher.job.JobEnums.DataOrigin;
import com.careermatcher.job.JobService;
import com.careermatcher.matching.MatchInputMapper.CandidateData;
import com.careermatcher.matching.dto.MatchDtos.CandidateDataStatus;
import com.careermatcher.matching.dto.MatchDtos.ComponentSummary;
import com.careermatcher.matching.dto.MatchDtos.Explanation;
import com.careermatcher.matching.dto.MatchDtos.MatchDetail;
import com.careermatcher.matching.dto.MatchDtos.MatchSummary;
import com.careermatcher.matching.dto.MatchDtos.PageInfo;
import com.careermatcher.matching.dto.MatchDtos.Recommendations;
import com.careermatcher.matching.engine.MatchInputs;
import com.careermatcher.matching.engine.MatchResult;
import com.careermatcher.matching.engine.MatchingEngine;
import com.careermatcher.matching.engine.MatchingWeights;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Computes, versions and serves match results for the authenticated candidate. Every operation
 * takes the candidate id from the verified token; results of other users are never addressable.
 */
@Service
public class MatchService {

  private static final Logger log = LoggerFactory.getLogger(MatchService.class);

  /** A computed result together with its stored version. */
  private record Computed(Job job, MatchResult result, String hash) {}

  private final MatchingEngine engine;
  private final MatchInputMapper inputs;
  private final JobService jobs;
  private final JobMatchRepository matches;
  private final MatchExplanationRepository explanations;
  private final AiMatchExplanationClient aiClient;
  private final RateLimiter rateLimiter;
  private final RateLimitProperties limits;
  private final JsonMapper json;
  private final Clock clock;

  public MatchService(
      MatchingWeights weights,
      MatchInputMapper inputs,
      JobService jobs,
      JobMatchRepository matches,
      MatchExplanationRepository explanations,
      AiMatchExplanationClient aiClient,
      RateLimiter rateLimiter,
      RateLimitProperties limits,
      JsonMapper json,
      Clock clock) {
    this.engine = new MatchingEngine(weights);
    this.inputs = inputs;
    this.jobs = jobs;
    this.matches = matches;
    this.explanations = explanations;
    this.aiClient = aiClient;
    this.rateLimiter = rateLimiter;
    this.limits = limits;
    this.json = json;
    this.clock = clock;
  }

  /** Scores every published job for the candidate, best first, storing new result versions. */
  @Transactional
  public Recommendations recommendations(UUID userId, DataOrigin origin, int page, int size) {
    CandidateData candidate = inputs.candidate(userId);
    YearMonth today = YearMonth.now(clock.withZone(ZoneOffset.UTC));
    Set<String> stored = new HashSet<>(matches.findKeys(userId, MatchingEngine.VERSION));

    List<Computed> computed = new ArrayList<>();
    for (Job job : jobs.visibleJobs()) {
      if (origin != null && job.getDataOrigin() != origin) {
        continue;
      }
      MatchResult result = engine.score(candidate.candidate(), inputs.job(job), today);
      Computed c = new Computed(job, result, hash(job.getId(), result));
      if (!stored.contains(job.getId() + ":" + c.hash())) {
        store(userId, candidate, c);
      }
      computed.add(c);
    }
    computed.sort(
        Comparator.comparingDouble((Computed c) -> c.result().totalScore()).reversed()
            .thenComparing(c -> c.job().getPostedOn(), Comparator.reverseOrder())
            .thenComparing(c -> c.job().getId()));

    int from = Math.min(page * size, computed.size());
    int to = Math.min(from + size, computed.size());
    List<MatchSummary> content = computed.subList(from, to).stream().map(this::summary).toList();
    int totalPages = (int) Math.ceil((double) computed.size() / size);
    return new Recommendations(content, new PageInfo(page, size, computed.size(), totalPages), status(candidate));
  }

  /** Compatibility of one published job. */
  @Transactional
  public MatchDetail detail(UUID userId, UUID jobId) {
    CandidateData candidate = inputs.candidate(userId);
    Computed c = compute(candidate, jobs.visibleJob(jobId));
    JobMatch row = store(userId, candidate, c);
    Explanation explanation = explanations.findByMatchId(row.getId()).map(this::toView).orElse(null);
    return new MatchDetail(row.getId(), jobs.summarize(c.job()), c.result(), row.getComputedAt(), status(candidate), explanation);
  }

  /**
   * Returns the explanation of the current result, generating it if needed. Explanations are
   * derived only from the calculated breakdown and public job fields, and are rejected unless every
   * skill and number they cite appears in those facts.
   */
  @Transactional
  public Explanation explain(UUID userId, UUID jobId) {
    CandidateData candidate = inputs.candidate(userId);
    Computed c = compute(candidate, jobs.visibleJob(jobId));
    JobMatch row = store(userId, candidate, c);
    var existing = explanations.findByMatchId(row.getId());
    if (existing.isPresent()) {
      return toView(existing.get());
    }
    rateLimiter.consume("match-explain", userId.toString(), limits.matchExplanationPerUser());

    MatchFacts facts = facts(c);
    AiMatchExplanation e;
    try {
      e = aiClient.explain(facts);
    } catch (AiAnalysisException ex) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "EXPLANATION_UNAVAILABLE",
          "The explanation could not be generated right now. Your score breakdown is still accurate.",
          Map.of("reason", ex.getCode()));
    }
    List<String> problems = ExplanationGuard.violations(facts, e, json);
    if (!problems.isEmpty()) {
      log.warn("Rejected ungrounded explanation for match {}: {}", row.getId(), problems);
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "EXPLANATION_UNAVAILABLE",
          "The explanation could not be verified against your score, so it was not shown. Please try again.",
          Map.of("reason", "AI_INVALID_OUTPUT"));
    }
    Explanation view =
        new Explanation(e.provider(), e.model(), e.summary(), points(e.strengths()), points(e.gaps()),
            points(e.nextSteps()), clock.instant());
    explanations.insertIfAbsent(
        UUID.randomUUID(), row.getId(), e.provider(), e.model(), e.promptVersion(),
        json.writeValueAsString(new StoredExplanation(view.summary(), view.strengths(), view.gaps(), view.nextSteps())),
        clock.instant());
    return explanations.findByMatchId(row.getId()).map(this::toView).orElse(view);
  }

  // ------------------------------------------------------------------ helpers

  private record StoredExplanation(
      String summary, List<Explanation.Point> strengths, List<Explanation.Point> gaps, List<Explanation.Point> nextSteps) {}

  private Computed compute(CandidateData candidate, Job job) {
    MatchResult result =
        engine.score(candidate.candidate(), inputs.job(job), YearMonth.now(clock.withZone(ZoneOffset.UTC)));
    return new Computed(job, result, hash(job.getId(), result));
  }

  private JobMatch store(UUID userId, CandidateData candidate, Computed c) {
    matches.insertIfAbsent(
        UUID.randomUUID(), userId, c.job().getId(), MatchingEngine.VERSION,
        BigDecimal.valueOf(c.result().totalScore()), json.writeValueAsString(c.result()),
        json.writeValueAsString(c.result().weights()), candidate.cvAnalysisId(), c.hash(), clock.instant());
    return matches
        .findByUserIdAndJobIdAndAlgorithmVersionAndResultHash(userId, c.job().getId(), MatchingEngine.VERSION, c.hash())
        .orElseThrow();
  }

  private String hash(UUID jobId, MatchResult result) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((jobId + "|" + json.writeValueAsString(result)).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private MatchSummary summary(Computed c) {
    MatchResult r = c.result();
    long required =
        c.job().getSkills().stream().filter(s -> s.getRequirement().name().equals("REQUIRED")).count();
    List<String> missing = MatchingEngine.missingRequired(r);
    return new MatchSummary(
        jobs.summarize(c.job()),
        r.totalScore(),
        r.algorithmVersion(),
        r.components().stream()
            .map(s -> new ComponentSummary(s.component().name(), s.status().name(), s.score(), s.weight(), s.contribution()))
            .toList(),
        missing,
        (int) required,
        (int) required - missing.size());
  }

  private static CandidateDataStatus status(CandidateData c) {
    MatchInputs.Preferences p = c.candidate().preferences();
    boolean prefs =
        !p.employmentTypes().isEmpty() || p.remotePreference() != null || !p.desiredRoles().isEmpty()
            || !p.preferredLocations().isEmpty() || p.expectedMinSalary() != null;
    return new CandidateDataStatus(
        !c.candidate().skillIds().isEmpty(), c.candidate().cv() != null, c.cvExtractionLimited(), prefs);
  }

  private MatchFacts facts(Computed c) {
    Job job = c.job();
    MatchResult r = c.result();
    return new MatchFacts(
        r.algorithmVersion(),
        new MatchFacts.JobFacts(job.getTitle(), job.getCompanyName(), job.getEmploymentType().name(),
            job.getRemoteType().name(), job.getSeniority().name(), job.getDataOrigin().name()),
        r.totalScore(),
        r.components().stream()
            .map(s -> new MatchFacts.ComponentFacts(
                s.component().name(), (int) Math.round(s.weight() * 100), (int) Math.round(s.score() * 100),
                s.status().name(),
                s.factors().stream().map(f -> new MatchFacts.FactorFacts(f.code(), f.skills(), f.values())).toList()))
            .toList(),
        r.missingData());
  }

  private static List<Explanation.Point> points(List<AiMatchExplanation.Point> points) {
    return points.stream().map(p -> new Explanation.Point(p.text(), List.copyOf(p.skills()))).toList();
  }

  private Explanation toView(MatchExplanation e) {
    StoredExplanation s = json.readValue(e.getContent(), StoredExplanation.class);
    return new Explanation(e.getProvider(), e.getModel(), s.summary(), s.strengths(), s.gaps(), s.nextSteps(), e.getCreatedAt());
  }
}
