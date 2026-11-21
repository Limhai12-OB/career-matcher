package com.careermatcher.matching.dto;

import com.careermatcher.job.dto.JobSummary;
import com.careermatcher.matching.engine.MatchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API DTOs of the matching module. */
public final class MatchDtos {

  private MatchDtos() {}

  /** What the candidate has given us; drives the "improve your accuracy" hints. */
  public record CandidateDataStatus(
      boolean hasConfirmedSkills, boolean hasCvAnalysis, boolean cvExtractionLimited, boolean hasPreferences) {}

  public record ComponentSummary(String component, String status, double score, double weight, double contribution) {}

  public record MatchSummary(
      JobSummary job,
      double totalScore,
      String algorithmVersion,
      List<ComponentSummary> components,
      List<String> missingRequiredSkills,
      int requiredSkillCount,
      int matchedRequiredSkillCount) {}

  public record PageInfo(int number, int size, long totalElements, int totalPages) {}

  public record Recommendations(List<MatchSummary> content, PageInfo page, CandidateDataStatus candidateData) {}

  public record Explanation(
      String provider,
      String model,
      String summary,
      List<Point> strengths,
      List<Point> gaps,
      List<Point> nextSteps,
      Instant createdAt) {

    public record Point(String text, List<String> skills) {}
  }

  /**
   * Full compatibility for one job.
   *
   * @param resultId the stored result version (explanations attach to it)
   * @param explanation cached explanation for this exact result, if one was generated
   */
  public record MatchDetail(
      UUID resultId,
      JobSummary job,
      MatchResult result,
      Instant computedAt,
      CandidateDataStatus candidateData,
      Explanation explanation) {}
}
