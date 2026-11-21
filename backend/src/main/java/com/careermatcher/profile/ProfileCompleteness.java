package com.careermatcher.profile;

import com.careermatcher.profile.dto.ProfileResponse.Completeness;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Computes completeness and onboarding requirements from real stored data. */
final class ProfileCompleteness {

  private ProfileCompleteness() {}

  /** Fields required to finish onboarding (minimum information for matching). */
  static List<String> missingRequired(CandidateProfile p, CandidatePreferences prefs) {
    List<String> missing = new ArrayList<>();
    if (isBlank(p.getFullName())) missing.add("fullName");
    if (p.getCountryCode() == null) missing.add("countryCode");
    if (p.getCareerStage() == null) missing.add("careerStage");
    if (prefs.getDesiredRoles().isEmpty()) missing.add("desiredRoles");
    if (prefs.getEmploymentTypes().isEmpty()) missing.add("employmentTypes");
    if (prefs.getRemotePreference() == null) missing.add("remotePreference");
    return missing;
  }

  static Completeness compute(CandidateProfile p, CandidatePreferences prefs) {
    Map<String, Boolean> items = new LinkedHashMap<>();
    items.put("fullName", !isBlank(p.getFullName()));
    items.put("headline", !isBlank(p.getHeadline()));
    items.put("bio", !isBlank(p.getBio()));
    items.put("location", p.getCountryCode() != null && !isBlank(p.getCity()));
    items.put("careerStage", p.getCareerStage() != null);
    items.put("graduationYear", p.getGraduationYear() != null);
    items.put(
        "professionalLink",
        !isBlank(p.getLinkedinUrl()) || !isBlank(p.getGithubUrl()) || !isBlank(p.getPortfolioUrl()));
    items.put("desiredRoles", !prefs.getDesiredRoles().isEmpty());
    items.put("employmentTypes", !prefs.getEmploymentTypes().isEmpty());
    items.put("remotePreference", prefs.getRemotePreference() != null);

    List<String> missing =
        items.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
    int percent = (int) Math.round(100.0 * (items.size() - missing.size()) / items.size());
    return new Completeness(percent, missing);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
