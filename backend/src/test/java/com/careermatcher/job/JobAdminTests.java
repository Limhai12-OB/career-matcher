package com.careermatcher.job;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.AdminSupport;
import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import com.careermatcher.support.TestApi.Session;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JobAdminTests {

  @Autowired TestApi api;
  @Autowired AdminSupport admins;
  @Autowired JdbcTemplate jdbc;

  private String create(Session admin, Map<String, String> overrides) throws Exception {
    String body =
        api.post(admin.accessToken(), "/api/v1/admin/jobs", JobTestData.job(jdbc, overrides))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  @Test
  void candidatesCannotManageJobs() throws Exception {
    Session candidate = api.register();
    api.get(candidate.accessToken(), "/api/v1/admin/jobs").andExpect(status().isForbidden());
    api.post(candidate.accessToken(), "/api/v1/admin/jobs", JobTestData.job(jdbc, Map.of()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void lifecycleDraftPublishCloseDelete() throws Exception {
    Session admin = admins.admin();
    Session candidate = api.register();
    String id = create(admin, Map.of());

    api.get(admin.accessToken(), "/api/v1/admin/jobs/" + id)
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.dataOrigin").value("REAL"))
        .andExpect(jsonPath("$.skills[0].requirement").value("REQUIRED"))
        .andExpect(jsonPath("$.skills[0].name").value("Java"));
    api.get(candidate.accessToken(), "/api/v1/jobs/" + id).andExpect(status().isNotFound());

    api.patch(admin.accessToken(), "/api/v1/admin/jobs/" + id + "/status", "{\"status\":\"PUBLISHED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedAt").isNotEmpty());
    api.get(candidate.accessToken(), "/api/v1/jobs/" + id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationUrl").value("https://jobs.acme-real.io/apply/123"))
        .andExpect(jsonPath("$.sourceName").value("Acme careers page"));

    api.patch(admin.accessToken(), "/api/v1/admin/jobs/" + id + "/status", "{\"status\":\"DRAFT\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

    api.patch(admin.accessToken(), "/api/v1/admin/jobs/" + id + "/status", "{\"status\":\"CLOSED\"}")
        .andExpect(status().isOk());
    api.get(candidate.accessToken(), "/api/v1/jobs/" + id).andExpect(status().isNotFound());

    api.delete(admin.accessToken(), "/api/v1/admin/jobs/" + id, "{}").andExpect(status().isNoContent());
    api.get(admin.accessToken(), "/api/v1/admin/jobs/" + id).andExpect(status().isNotFound());
  }

  @Test
  void updateReplacesContentAndSkills() throws Exception {
    Session admin = admins.admin();
    String id = create(admin, Map.of());
    String python = JobTestData.skillId(jdbc, "python");
    api.put(
            admin.accessToken(),
            "/api/v1/admin/jobs/" + id,
            JobTestData.job(
                jdbc,
                Map.of(
                    "title", "\"Junior Python Developer\"",
                    "skills", "[{\"skillId\":\"" + python + "\",\"requirement\":\"REQUIRED\"}]")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Junior Python Developer"))
        .andExpect(jsonPath("$.skills.length()").value(1))
        .andExpect(jsonPath("$.skills[0].name").value("Python"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "http url|applicationUrl|\"http://jobs.acme-real.io/apply\"",
        "javascript url|applicationUrl|\"javascript:alert(1)\"",
        "real listing on example domain|applicationUrl|\"https://careers.example.com/x\"",
        "unknown country|countryCode|\"XX\"",
        "salary max below min|salaryMax|500",
        "experience max below min|maxExperienceMonths|3",
        "future posting date|postedOn|\"2999-01-01\"",
        "description too short|description|\"Too short\"",
        "missing origin|dataOrigin|null",
        "unknown currency|salaryCurrency|\"ZZZ\"",
      })
  void invalidJobsAreRejectedWithFieldErrors(String name, String field, String value) throws Exception {
    Session admin = admins.admin();
    api.post(admin.accessToken(), "/api/v1/admin/jobs", JobTestData.job(jdbc, Map.of(field, value)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
  }

  @Test
  void demoListingsMustUseReservedExampleDomains() throws Exception {
    Session admin = admins.admin();
    api.post(
            admin.accessToken(),
            "/api/v1/admin/jobs",
            JobTestData.job(jdbc, Map.of("dataOrigin", "\"DEMO\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", hasItem("applicationUrl")));
    api.post(
            admin.accessToken(),
            "/api/v1/admin/jobs",
            JobTestData.job(
                jdbc,
                Map.of(
                    "dataOrigin", "\"DEMO\"",
                    "applicationUrl", "\"https://careers.example.com/demo/1\"",
                    "sourceUrl", "\"https://example.org/demo\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.dataOrigin").value("DEMO"));
  }

  @Test
  void databaseAlsoRejectsDemoRowsLinkingToRealSites() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into jobs (id, title, company_name, description, remote_type, employment_type, seniority,"
                        + " application_url, source_name, data_origin, status, posted_on, created_at, updated_at)"
                        + " values (gen_random_uuid(), 't', 'c', ?, 'REMOTE', 'FULL_TIME', 'JUNIOR',"
                        + " 'https://real-company.io/apply', 's', 'DEMO', 'DRAFT', current_date, now(), now())",
                    "x".repeat(60)))
        .hasMessageContaining("ck_jobs_demo_urls");
  }

  @Test
  void skillRulesAreEnforced() throws Exception {
    Session admin = admins.admin();
    String java = JobTestData.skillId(jdbc, "java");
    for (String skills :
        new String[] {
          "[]",
          "[{\"skillId\":\"" + java + "\",\"requirement\":\"PREFERRED\"}]",
          "[{\"skillId\":\"" + java + "\",\"requirement\":\"REQUIRED\"},{\"skillId\":\"" + java + "\",\"requirement\":\"PREFERRED\"}]",
          "[{\"skillId\":\"00000000-0000-0000-0000-000000000000\",\"requirement\":\"REQUIRED\"}]",
        }) {
      api.post(admin.accessToken(), "/api/v1/admin/jobs", JobTestData.job(jdbc, Map.of("skills", skills)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[*].field", hasItem("skills")));
    }
  }

  @Test
  void expiryMustNotPrecedePosting() throws Exception {
    Session admin = admins.admin();
    api.post(
            admin.accessToken(),
            "/api/v1/admin/jobs",
            JobTestData.job(jdbc, Map.of("expiresOn", "\"" + LocalDate.now().minusDays(1) + "\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", hasItem("expiresOn")));
  }

  @Test
  void adminListFiltersByStatusAndOrigin() throws Exception {
    Session admin = admins.admin();
    String token = "zq" + System.nanoTime();
    String draft = create(admin, Map.of("title", "\"Draft " + token + "\""));
    String published = create(admin, Map.of("title", "\"Live " + token + "\""));
    api.patch(admin.accessToken(), "/api/v1/admin/jobs/" + published + "/status", "{\"status\":\"PUBLISHED\"}");
    api.get(admin.accessToken(), "/api/v1/admin/jobs?q=" + token)
        .andExpect(jsonPath("$.page.totalElements").value(2));
    api.get(admin.accessToken(), "/api/v1/admin/jobs?q=" + token + "&status=DRAFT")
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(draft));
    api.get(admin.accessToken(), "/api/v1/admin/jobs?q=" + token + "&origin=DEMO")
        .andExpect(jsonPath("$.content.length()").value(0));
  }
}
