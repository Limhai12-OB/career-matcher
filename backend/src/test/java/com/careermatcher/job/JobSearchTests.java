package com.careermatcher.job;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.AdminSupport;
import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import com.careermatcher.support.TestApi.Session;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JobSearchTests {

  @Autowired TestApi api;
  @Autowired AdminSupport admins;
  @Autowired JdbcTemplate jdbc;

  String token;
  Session candidate;
  String jobA;
  String jobB;

  private String create(Session admin, Map<String, String> overrides, boolean publish) throws Exception {
    String id =
        JsonPath.read(
            api.post(admin.accessToken(), "/api/v1/admin/jobs", JobTestData.job(jdbc, overrides))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(),
            "$.id");
    if (publish) {
      api.patch(admin.accessToken(), "/api/v1/admin/jobs/" + id + "/status", "{\"status\":\"PUBLISHED\"}")
          .andExpect(status().isOk());
    }
    return id;
  }

  /** Each test gets its own unique search token so tests stay independent. */
  @BeforeEach
  void data() throws Exception {
    token = "tok" + System.nanoTime();
    Session admin = admins.admin();
    candidate = api.register();
    String python = JobTestData.skillId(jdbc, "python");
    jobA =
        create(admin, Map.of(
            "title", "\"Java Intern " + token + "\"",
            "remoteType", "\"REMOTE\"", "employmentType", "\"INTERNSHIP\"", "seniority", "\"INTERNSHIP\""), true);
    jobB =
        create(admin, Map.of(
            "title", "\"Data Analyst\"",
            "companyName", "\"" + token + " Analytics\"",
            "countryCode", "\"SG\"", "city", "\"Singapore\"", "remoteType", "\"ONSITE\"",
            "postedOn", "\"" + LocalDate.now().minusDays(1) + "\"",
            "skills", "[{\"skillId\":\"" + python + "\",\"requirement\":\"REQUIRED\"}]"), true);
    create(admin, Map.of("title", "\"Hidden draft " + token + "\""), false);
    create(admin, Map.of(
        "title", "\"Expired " + token + "\"",
        "postedOn", "\"" + LocalDate.now().minusDays(30) + "\"",
        "expiresOn", "\"" + LocalDate.now().minusDays(1) + "\""), true);
  }

  private org.springframework.test.web.servlet.ResultActions search(String query) throws Exception {
    return api.get(candidate.accessToken(), "/api/v1/jobs?q=" + token + query);
  }

  @Test
  void onlyPublishedUnexpiredJobsAreVisibleNewestFirst() throws Exception {
    search("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].id", contains(jobA, jobB)))
        .andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  void textSearchCoversTitleCompanyAndDescriptionCaseInsensitively() throws Exception {
    api.get(candidate.accessToken(), "/api/v1/jobs?q=" + token.toUpperCase())
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(jobA, jobB)));
  }

  @Test
  void likeWildcardsInQueryAreLiteral() throws Exception {
    api.get(candidate.accessToken(), "/api/v1/jobs?q=%25%25" + token).andExpect(jsonPath("$.content.length()").value(0));
    api.get(candidate.accessToken(), "/api/v1/jobs?q=_" + token.substring(1)).andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void filtersNarrowResults() throws Exception {
    search("&country=SG").andExpect(jsonPath("$.content[*].id", contains(jobB)));
    search("&remote=REMOTE").andExpect(jsonPath("$.content[*].id", contains(jobA)));
    search("&type=INTERNSHIP").andExpect(jsonPath("$.content[*].id", contains(jobA)));
    search("&seniority=JUNIOR").andExpect(jsonPath("$.content[*].id", contains(jobB)));
    search("&skill=python").andExpect(jsonPath("$.content[*].id", contains(jobB)));
    search("&skill=java&skill=python").andExpect(jsonPath("$.page.totalElements").value(2));
    search("&skill=no-such-skill").andExpect(jsonPath("$.content.length()").value(0));
    search("&origin=DEMO").andExpect(jsonPath("$.content.length()").value(0));
    search("&origin=REAL").andExpect(jsonPath("$.page.totalElements").value(2));
  }

  @Test
  void paginationReportsTotals() throws Exception {
    search("&size=1&page=0")
        .andExpect(jsonPath("$.content[*].id", contains(jobA)))
        .andExpect(jsonPath("$.page.totalPages").value(2));
    search("&size=1&page=1").andExpect(jsonPath("$.content[*].id", contains(jobB)));
  }

  @Test
  void invalidParametersAreRejected() throws Exception {
    search("&size=51").andExpect(status().isBadRequest());
    search("&remote=SPACE").andExpect(status().isBadRequest());
    search("&country=khm").andExpect(status().isBadRequest());
    search("&skill=DROP%20TABLE").andExpect(status().isBadRequest());
  }

  @Test
  void summariesAndDetailsExposeSkillsSourceAndOrigin() throws Exception {
    search("&remote=REMOTE")
        .andExpect(jsonPath("$.content[0].requiredSkills", contains("Java")))
        .andExpect(jsonPath("$.content[0].preferredSkillCount").value(1))
        .andExpect(jsonPath("$.content[0].dataOrigin").value("REAL"));
    api.get(candidate.accessToken(), "/api/v1/jobs/" + jobA)
        .andExpect(jsonPath("$.skills[*].name", contains("Java", "Docker")))
        .andExpect(jsonPath("$.skills[*].requirement", contains("REQUIRED", "PREFERRED")))
        .andExpect(jsonPath("$.sourceUrl").value("https://jobs.acme-real.io/123"));
  }

  @Test
  void jobsRequireAuthentication() throws Exception {
    api.get(null, "/api/v1/jobs").andExpect(status().isUnauthorized());
  }
}
