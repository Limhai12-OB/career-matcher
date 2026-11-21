package com.careermatcher.cv.dto;

import java.util.List;

/**
 * Validated, grounded structured CV data stored with an analysis (JSONB) and returned to the
 * owner. Every item carries a verbatim evidence quote from the CV.
 */
public record AnalysisContent(
    DocumentStats document,
    List<Education> education,
    List<Experience> experience,
    List<Project> projects,
    List<Certification> certifications,
    DroppedItems droppedItems) {

  public record DocumentStats(String type, Integer pages, int characters) {}

  public record Education(
      String institution,
      String degree,
      String fieldOfStudy,
      String level,
      String startDate,
      String endDate,
      boolean ongoing,
      String evidence) {}

  public record Experience(
      String title,
      String organization,
      String employmentType,
      String startDate,
      String endDate,
      boolean current,
      List<String> skills,
      String evidence) {}

  public record Project(String name, List<String> skills, String evidence) {}

  public record Certification(String name, String issuer, String year, String evidence) {}

  public record DroppedItems(int skills, int education, int experience, int projects, int certifications) {}
}
