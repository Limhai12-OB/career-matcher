package com.careermatcher.profile.dto;

import com.careermatcher.profile.CareerStage;
import java.time.Instant;
import java.util.List;

public record ProfileResponse(
    String fullName,
    String headline,
    String bio,
    String city,
    String countryCode,
    CareerStage careerStage,
    Integer graduationYear,
    String linkedinUrl,
    String githubUrl,
    String portfolioUrl,
    Completeness completeness,
    Instant updatedAt) {

  /**
   * Profile completeness across profile and preferences.
   *
   * @param percent 0-100
   * @param missing stable keys of missing items, e.g. {@code "headline"}, {@code "desiredRoles"}
   */
  public record Completeness(int percent, List<String> missing) {}
}
