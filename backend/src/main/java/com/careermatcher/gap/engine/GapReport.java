package com.careermatcher.gap.engine;

import java.util.List;
import java.util.UUID;

/**
 * Deterministic skill gap analysis over a candidate's target jobs.
 *
 * @param averageScore mean match score of the target jobs (0-100, one decimal)
 * @param requiredCoverage share of the target jobs' required skills the candidate has (0-100)
 */
public record GapReport(
    String algorithmVersion,
    String matchAlgorithmVersion,
    List<TargetJob> targets,
    double averageScore,
    double requiredCoverage,
    List<SkillGap> gaps,
    List<Strength> strengths) {

  public enum Priority {
    /** Required by many target jobs. */
    CRITICAL,
    /** Required by at least one target job. */
    HIGH,
    /** Preferred by several target jobs. */
    MEDIUM,
    /** Preferred by one target job. */
    LOW
  }

  public record TargetJob(UUID jobId, String title, String company, String dataOrigin, double score) {}

  /**
   * A skill the candidate lacks.
   *
   * @param requiredIn target jobs listing it as required
   * @param preferredIn target jobs listing it as preferred
   * @param averageGain measured mean score increase across all target jobs if the candidate had it
   * @param jobIds target jobs that list the skill
   */
  public record SkillGap(
      UUID skillId,
      String name,
      String category,
      Priority priority,
      int requiredIn,
      int preferredIn,
      double averageGain,
      List<UUID> jobIds) {}

  /** A skill the candidate has that target jobs require. */
  public record Strength(UUID skillId, String name, int requiredIn) {}
}
