package com.careermatcher.job;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/** Builds job request JSON for tests. */
final class JobTestData {

  private JobTestData() {}

  static String skillId(JdbcTemplate jdbc, String slug) {
    return jdbc.queryForObject("select id::text from skills where slug = ?", String.class, slug);
  }

  /** A valid REAL job; override any top-level field with raw JSON values. */
  static String job(JdbcTemplate jdbc, Map<String, String> overrides) {
    Map<String, String> f = new java.util.LinkedHashMap<>();
    f.put("title", "\"Junior Java Developer\"");
    f.put("companyName", "\"Acme Real Co\"");
    f.put("companyWebsite", "\"https://acme.test-company.io\"");
    f.put("description", "\"" + "Build and maintain Java services for our customers. ".repeat(3) + "\"");
    f.put("city", "\"Phnom Penh\"");
    f.put("countryCode", "\"KH\"");
    f.put("remoteType", "\"HYBRID\"");
    f.put("employmentType", "\"FULL_TIME\"");
    f.put("seniority", "\"JUNIOR\"");
    f.put("minExperienceMonths", "6");
    f.put("maxExperienceMonths", "24");
    f.put("educationLevel", "\"BACHELOR\"");
    f.put("educationFields", "[\"Computer Science\"]");
    f.put("salaryMin", "800");
    f.put("salaryMax", "1200");
    f.put("salaryCurrency", "\"USD\"");
    f.put("salaryPeriod", "\"MONTH\"");
    f.put("applicationUrl", "\"https://jobs.acme-real.io/apply/123\"");
    f.put("sourceName", "\"Acme careers page\"");
    f.put("sourceUrl", "\"https://jobs.acme-real.io/123\"");
    f.put("dataOrigin", "\"REAL\"");
    f.put("postedOn", "\"" + LocalDate.now() + "\"");
    f.put("expiresOn", "null");
    f.put(
        "skills",
        "[{\"skillId\":\"" + skillId(jdbc, "java") + "\",\"requirement\":\"REQUIRED\"},"
            + "{\"skillId\":\"" + skillId(jdbc, "docker") + "\",\"requirement\":\"PREFERRED\"}]");
    f.putAll(overrides);
    return f.entrySet().stream().map(e -> "\"" + e.getKey() + "\":" + e.getValue()).collect(Collectors.joining(",", "{", "}"));
  }
}
