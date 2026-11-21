package com.careermatcher.matching.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Plain, persistence-free inputs of the matching engine. */
public final class MatchInputs {

  private MatchInputs() {}

  public enum Requirement { REQUIRED, PREFERRED }

  public enum RemoteType { ONSITE, HYBRID, REMOTE }

  public enum RemotePreference { ONSITE, HYBRID, REMOTE, ANY }

  /** Education levels from CVs and jobs, ordered by {@link #rank}. */
  public enum EducationLevel {
    OTHER(0), HIGH_SCHOOL(1), VOCATIONAL(2), BOOTCAMP(2), ASSOCIATE(3), BACHELOR(4), MASTER(5), DOCTORATE(6);

    public final int rank;

    EducationLevel(int rank) {
      this.rank = rank;
    }
  }

  public record JobSkill(String name, Requirement requirement) {}

  public record Job(
      UUID id,
      String title,
      Map<UUID, JobSkill> skills,
      int minExperienceMonths,
      Integer maxExperienceMonths,
      EducationLevel educationLevel,
      List<String> educationFields,
      String employmentType,
      RemoteType remoteType,
      String countryCode,
      String city,
      Integer salaryMin,
      Integer salaryMax,
      String salaryCurrency,
      /** "MONTH" or "YEAR"; null when no salary. */
      String salaryPeriod) {}

  /** Dates are "YYYY" or "YYYY-MM" as stored by the CV analysis; null when unknown. */
  public record Experience(String startDate, String endDate, boolean current) {}

  public record Education(EducationLevel level, String fieldOfStudy, boolean ongoing) {}

  /**
   * Facts from the candidate's latest completed CV analysis.
   *
   * @param experienceAndProjectsExtracted false when the analysis could not extract experience and
   *     projects (basic extraction mode); they then count as missing, not as "none".
   * @param projectSkills catalog skill ids named in each project
   */
  public record CvFacts(
      List<Experience> experience,
      List<Set<UUID>> projectSkills,
      List<Education> education,
      boolean experienceAndProjectsExtracted) {}

  public record Preferences(
      List<String> desiredRoles,
      List<String> preferredLocations,
      Set<String> employmentTypes,
      RemotePreference remotePreference,
      boolean willingToRelocate,
      /** Monthly amount. */
      Integer expectedMinSalary,
      String salaryCurrency) {

    public static final Preferences NONE =
        new Preferences(List.of(), List.of(), Set.of(), null, false, null, null);
  }

  /**
   * @param skillIds confirmed catalog skills
   * @param cv null when the candidate has no completed CV analysis
   */
  public record Candidate(
      Set<UUID> skillIds, CvFacts cv, Preferences preferences, String countryCode, String city) {}
}
