package com.careermatcher.aiclient.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything an explanation may be based on: the calculated breakdown and public job fields. No
 * CV text, contact details or other personal data are ever included.
 */
public record MatchFacts(
    String algorithmVersion,
    JobFacts job,
    double totalScore,
    List<ComponentFacts> components,
    List<String> missingData) {

  public record JobFacts(
      String title, String company, String employmentType, String remoteType, String seniority, String dataOrigin) {}

  public record ComponentFacts(
      String component, int weightPercent, int scorePercent, String status, List<FactorFacts> factors) {}

  public record FactorFacts(String code, List<String> skills, Map<String, Object> values) {}
}
