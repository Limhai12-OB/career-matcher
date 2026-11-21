package com.careermatcher.cv.dto;

import com.careermatcher.cv.AnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisDetail(
    UUID id,
    UUID documentId,
    String documentFilename,
    AnalysisStatus status,
    String errorCode,
    String provider,
    String model,
    Instant createdAt,
    Instant completedAt,
    Instant appliedAt,
    List<String> warnings,
    List<ExtractedSkill> skills,
    /** Null until the analysis has completed. */
    AnalysisContent content) {

  /**
   * A skill found in the CV.
   *
   * @param skillId catalog skill, or null if the name was not recognized
   * @param inProfile whether the catalog skill is already in the candidate's confirmed skills
   */
  public record ExtractedSkill(
      String rawName,
      UUID skillId,
      String canonicalName,
      String category,
      String evidence,
      boolean inProfile) {}
}
