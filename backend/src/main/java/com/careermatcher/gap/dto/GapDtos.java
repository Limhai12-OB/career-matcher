package com.careermatcher.gap.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API DTOs of the skill gap module. */
public final class GapDtos {

  private GapDtos() {}

  public record ResourceView(UUID id, String title, String provider, String type, String url, LocalDate verifiedOn) {}

  public record JobRef(UUID jobId, String title, String company, String dataOrigin, double score) {}

  /**
   * @param closed true if the candidate has confirmed this skill since the report was created
   */
  public record GapView(
      UUID skillId,
      String name,
      String category,
      String priority,
      int requiredIn,
      int preferredIn,
      double averageGain,
      List<JobRef> jobs,
      boolean closed,
      List<ResourceView> resources) {}

  public record StrengthView(String name, int requiredIn) {}

  /**
   * @param done true once the step's skill is confirmed in the candidate's profile
   */
  public record StepView(
      String skill, String title, String why, List<String> activities, int estimatedWeeks,
      List<ResourceView> resources, boolean done) {}

  public record RoadmapView(String provider, String model, String summary, List<StepView> steps, Instant createdAt) {}

  public record ReportView(
      UUID id,
      Instant createdAt,
      String algorithmVersion,
      String matchAlgorithmVersion,
      int targetJobCount,
      double averageScore,
      double requiredCoverage,
      List<JobRef> targets,
      List<GapView> gaps,
      List<StrengthView> strengths,
      RoadmapView roadmap) {}

  public record ReportPoint(
      UUID id, Instant createdAt, double averageScore, double requiredCoverage, int criticalCount, int highCount,
      int gapCount, boolean hasRoadmap) {}

  /**
   * Progress across all reports (oldest first in {@code history}).
   *
   * @param closedSinceFirst critical/high gaps of the first report that are now confirmed skills
   * @param openFromFirst critical/high gaps of the first report that are still missing
   */
  public record Progress(
      List<ReportPoint> history, UUID latestReportId, List<String> closedSinceFirst, List<String> openFromFirst,
      int confirmedSkillCount) {}
}
