package com.careermatcher.aiclient.dto;

import java.util.List;

/**
 * Everything a learning roadmap may be based on: the measured skill gaps, the candidate's existing
 * skill names and curated resources (by id; URLs are never sent). No CV text or personal data.
 */
public record RoadmapFacts(
    int targetJobCount,
    double averageScore,
    double requiredCoverage,
    List<Gap> gaps,
    List<String> existingSkills,
    List<Resource> resources) {

  public record Gap(
      String skill, String category, String priority, int requiredIn, int preferredIn, double averageGain,
      List<String> exampleJobs) {}

  public record Resource(String id, String skill, String title, String provider, String type) {}
}
