package com.careermatcher.matching;

import com.careermatcher.cv.CvService;
import com.careermatcher.cv.CvService.CompletedAnalysis;
import com.careermatcher.cv.dto.AnalysisContent;
import com.careermatcher.job.Job;
import com.careermatcher.job.JobSkillRequirement;
import com.careermatcher.matching.engine.MatchInputs;
import com.careermatcher.matching.engine.MatchInputs.Candidate;
import com.careermatcher.matching.engine.MatchInputs.CvFacts;
import com.careermatcher.matching.engine.MatchInputs.EducationLevel;
import com.careermatcher.matching.engine.MatchInputs.JobSkill;
import com.careermatcher.matching.engine.MatchInputs.RemotePreference;
import com.careermatcher.matching.engine.MatchInputs.RemoteType;
import com.careermatcher.matching.engine.MatchInputs.Requirement;
import com.careermatcher.profile.ProfileService;
import com.careermatcher.profile.dto.PreferencesResponse;
import com.careermatcher.profile.dto.ProfileResponse;
import com.careermatcher.skill.CandidateSkillService;
import com.careermatcher.skill.SkillNormalizer;
import com.careermatcher.skill.SkillRef;
import com.careermatcher.skill.dto.CandidateSkillResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Assembles engine inputs from stored, validated data only (nothing is inferred). */
@Component
class MatchInputMapper {

  /** Candidate inputs plus where they came from. */
  record CandidateData(Candidate candidate, UUID cvAnalysisId, boolean cvExtractionLimited) {}

  private final CandidateSkillService candidateSkills;
  private final CvService cvService;
  private final ProfileService profiles;
  private final SkillNormalizer catalog;

  MatchInputMapper(
      CandidateSkillService candidateSkills, CvService cvService, ProfileService profiles, SkillNormalizer catalog) {
    this.candidateSkills = candidateSkills;
    this.cvService = cvService;
    this.profiles = profiles;
    this.catalog = catalog;
  }

  CandidateData candidate(UUID userId) {
    Set<UUID> skills =
        candidateSkills.list(userId).stream().map(CandidateSkillResponse::skillId).collect(Collectors.toSet());
    Optional<CompletedAnalysis> analysis = cvService.latestCompleted(userId);
    CvFacts cv = analysis.map(a -> cvFacts(a)).orElse(null);
    boolean limited = analysis.map(a -> a.warnings().contains("LIMITED_EXTRACTION")).orElse(false);
    ProfileResponse profile = profiles.getProfile(userId);
    PreferencesResponse p = profiles.getPreferences(userId);
    MatchInputs.Preferences prefs =
        new MatchInputs.Preferences(
            p.desiredRoles(),
            p.preferredLocations(),
            p.employmentTypes().stream().map(Enum::name).collect(Collectors.toSet()),
            p.remotePreference() == null ? null : RemotePreference.valueOf(p.remotePreference().name()),
            p.willingToRelocate(),
            p.expectedMinSalary(),
            p.salaryCurrency());
    return new CandidateData(
        new Candidate(skills, cv, prefs, profile.countryCode(), profile.city()),
        analysis.map(CompletedAnalysis::analysisId).orElse(null),
        limited);
  }

  private CvFacts cvFacts(CompletedAnalysis a) {
    AnalysisContent c = a.content();
    boolean extracted = !a.warnings().contains("LIMITED_EXTRACTION");
    return new CvFacts(
        c.experience().stream()
            .map(x -> new MatchInputs.Experience(x.startDate(), x.endDate(), x.current()))
            .toList(),
        c.projects().stream()
            .map(p -> p.skills().stream()
                .map(catalog::resolve)
                .flatMap(Optional::stream)
                .map(SkillRef::id)
                .collect(Collectors.toSet()))
            .toList(),
        c.education().stream()
            .map(e -> new MatchInputs.Education(level(e.level()), e.fieldOfStudy(), e.ongoing()))
            .toList(),
        extracted);
  }

  private static EducationLevel level(String name) {
    try {
      return EducationLevel.valueOf(name);
    } catch (IllegalArgumentException e) {
      return EducationLevel.OTHER;
    }
  }

  MatchInputs.Job job(Job job) {
    Map<UUID, JobSkill> skills = new LinkedHashMap<>();
    for (JobSkillRequirement s : job.getSkills()) {
      SkillRef ref = catalog.byId(s.getSkillId()).orElseThrow();
      skills.put(ref.id(), new JobSkill(ref.name(), Requirement.valueOf(s.getRequirement().name())));
    }
    return new MatchInputs.Job(
        job.getId(),
        job.getTitle(),
        skills,
        job.getMinExperienceMonths(),
        job.getMaxExperienceMonths(),
        job.getEducationLevel() == null ? null : EducationLevel.valueOf(job.getEducationLevel().name()),
        job.getEducationFields(),
        job.getEmploymentType().name(),
        RemoteType.valueOf(job.getRemoteType().name()),
        job.getCountryCode(),
        job.getCity(),
        job.getSalaryMin(),
        job.getSalaryMax(),
        job.getSalaryCurrency(),
        job.getSalaryPeriod() == null ? null : job.getSalaryPeriod().name());
  }
}
