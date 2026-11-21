package com.careermatcher.matching;

import static com.careermatcher.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.aiclient.AiAnalysisException;
import com.careermatcher.aiclient.AiMatchExplanationClient;
import com.careermatcher.aiclient.dto.AiMatchExplanation;
import com.careermatcher.support.AdminSupport;
import com.careermatcher.support.CvFixtures;
import com.careermatcher.support.CvTestBase;
import com.careermatcher.support.TestApi.Session;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class MatchApiTests extends CvTestBase {

  @MockitoBean AiMatchExplanationClient explainer;
  @Autowired AdminSupport admins;
  @Autowired JdbcTemplate jdbc;

  String token;
  String javaJob;
  String pythonJob;
  Session candidate;

  private String skill(String slug) {
    return jdbc.queryForObject("select id::text from skills where slug = ?", String.class, slug);
  }

  private String publishJob(Session admin, String title, String required, String preferred, Map<String, String> extra)
      throws Exception {
    Map<String, String> f = new java.util.LinkedHashMap<>(Map.of(
        "title", "\"" + title + "\"",
        "educationLevel", "null", "educationFields", "[]",
        "minExperienceMonths", "0", "maxExperienceMonths", "null",
        "salaryMin", "null", "salaryMax", "null", "salaryCurrency", "null", "salaryPeriod", "null"));
    f.put("skills", "[{\"skillId\":\"" + skill(required) + "\",\"requirement\":\"REQUIRED\"},"
        + "{\"skillId\":\"" + skill(preferred) + "\",\"requirement\":\"PREFERRED\"}]");
    f.putAll(extra);
    String id = JsonPath.read(
        api.post(admin.accessToken(), "/api/v1/admin/jobs", com.careermatcher.job.JobTestDataAccess.job(jdbc, f))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    api.patch(admin.accessToken(), "/api/v1/admin/jobs/" + id + "/status", "{\"status\":\"PUBLISHED\"}")
        .andExpect(status().isOk());
    return id;
  }

  private void confirmSkills(Session s, String... slugs) {
    for (String slug : slugs) {
      jdbc.update(
          "insert into candidate_skills (id, user_id, skill_id, source, created_at) values (gen_random_uuid(), ?, ?::uuid, 'MANUAL', now())",
          s.userId(), skill(slug));
    }
  }

  /** Finds one job in the (globally shared) recommendation list. */
  private Map<String, Object> recommendation(Session s, String jobId) throws Exception {
    for (int page = 0; page < 20; page++) {
      String body = api.get(s.accessToken(), "/api/v1/me/matches?size=50&page=" + page).andReturn().getResponse().getContentAsString();
      List<Map<String, Object>> items = JsonPath.read(body, "$.content[?(@.job.id == '" + jobId + "')]");
      if (!items.isEmpty()) return items.get(0);
      if (((List<?>) JsonPath.read(body, "$.content")).isEmpty()) break;
    }
    throw new AssertionError("job not recommended: " + jobId);
  }

  @BeforeEach
  void setUp() throws Exception {
    reset(explainer);
    token = "m" + System.nanoTime();
    Session admin = admins.admin();
    javaJob = publishJob(admin, "Junior Java Developer " + token, "java", "docker", Map.of());
    pythonJob = publishJob(admin, "Data Engineer " + token, "python", "docker", Map.of());
    candidate = api.register();
    api.put(candidate.accessToken(), "/api/v1/me/profile", json("{'fullName':'Kim','countryCode':'KH'}")).andExpect(status().isOk());
    api.put(candidate.accessToken(), "/api/v1/me/preferences",
        json("{'desiredRoles':['Java Developer'],'employmentTypes':['FULL_TIME'],'remotePreference':'HYBRID'}"))
        .andExpect(status().isOk());
    confirmSkills(candidate, "java", "docker");
  }

  @Test
  void recommendationsAreRankedWithExactDeterministicScores() throws Exception {
    Map<String, Object> javaMatch = recommendation(candidate, javaJob);
    Map<String, Object> python = recommendation(candidate, pythonJob);

    // 45 (skills) + 20 (no experience required) + 0 (projects unknown) + 10 (no education required) + 10 (preferences)
    assertThat(((Number) javaMatch.get("totalScore")).doubleValue()).isEqualTo(85.0);
    // 0.25*45 + 20 + 0 + 10 + 0.75*10 = 48.75
    assertThat(((Number) python.get("totalScore")).doubleValue()).isEqualTo(48.8);
    assertThat(python.get("missingRequiredSkills")).isEqualTo(List.of("Python"));
    assertThat(javaMatch.get("algorithmVersion")).isEqualTo("match-v1");

    String body = api.get(candidate.accessToken(), "/api/v1/me/matches?size=50").andReturn().getResponse().getContentAsString();
    List<String> order = JsonPath.read(body, "$.content[*].job.id");
    if (order.contains(javaJob) && order.contains(pythonJob)) {
      assertThat(order.indexOf(javaJob)).isLessThan(order.indexOf(pythonJob));
    }
    List<Double> scores = JsonPath.read(body, "$.content[*].totalScore");
    assertThat(scores).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    assertThat((Boolean) JsonPath.read(body, "$.candidateData.hasConfirmedSkills")).isTrue();
    assertThat((Boolean) JsonPath.read(body, "$.candidateData.hasCvAnalysis")).isFalse();
  }

  @Test
  void detailShowsBreakdownAndResultsAreVersionedOnlyWhenTheyChange() throws Exception {
    String first = api.get(candidate.accessToken(), "/api/v1/me/matches/" + pythonJob)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalScore").value(48.8))
        .andExpect(jsonPath("$.result.components[0].component").value("SKILLS"))
        .andExpect(jsonPath("$.result.components[0].factors[0].code").value("REQUIRED_SKILLS_MISSING"))
        .andExpect(jsonPath("$.result.components[2].status").value("MISSING_CANDIDATE_DATA"))
        .andExpect(jsonPath("$.result.missingData[0]").value("CV_PROJECTS"))
        .andExpect(jsonPath("$.result.weights.SKILLS").value(0.45))
        .andReturn().getResponse().getContentAsString();
    String id1 = JsonPath.read(first, "$.resultId");
    String again = JsonPath.read(api.get(candidate.accessToken(), "/api/v1/me/matches/" + pythonJob)
        .andReturn().getResponse().getContentAsString(), "$.resultId");
    assertThat(again).isEqualTo(id1); // unchanged inputs: same stored version

    confirmSkills(candidate, "python");
    // Now 45 + 20 + 0 + 10 + 7.5 (role keywords still don't match "Data Engineer") = 82.5
    String changed = api.get(candidate.accessToken(), "/api/v1/me/matches/" + pythonJob)
        .andExpect(jsonPath("$.result.totalScore").value(82.5))
        .andReturn().getResponse().getContentAsString();
    assertThat((String) JsonPath.read(changed, "$.resultId")).isNotEqualTo(id1);
    assertThat(jdbc.queryForObject("select count(*) from job_matches where user_id = ? and job_id = ?::uuid",
        Integer.class, candidate.userId(), pythonJob)).isEqualTo(2); // history kept
  }

  @Test
  void cvAnalysisFeedsExperienceProjectsAndEducation() throws Exception {
    when(aiClient.analyze(any(), any())).thenReturn(CvFixtures.analysis());
    UUID analysis = uploadAccepted(candidate.accessToken(), "cv.pdf", CvFixtures.pdf());
    awaitTerminal(candidate.accessToken(), analysis);
    Session admin = admins.admin();
    String job = publishJob(admin, "Java Intern " + token, "java", "docker",
        Map.of("minExperienceMonths", "6", "educationLevel", "\"BACHELOR\"", "educationFields", "[\"Computer Science\"]"));

    api.get(candidate.accessToken(), "/api/v1/me/matches/" + job)
        .andExpect(jsonPath("$.candidateData.hasCvAnalysis").value(true))
        // 2024-06..2024-12 = 7 months >= 6
        .andExpect(jsonPath("$.result.components[1].factors[0].code").value("EXPERIENCE_MEETS_MINIMUM"))
        .andExpect(jsonPath("$.result.components[1].factors[0].values.candidateMonths").value(7))
        // the CV's project uses React, which this job doesn't list
        .andExpect(jsonPath("$.result.components[2].factors[0].code").value("NO_RELEVANT_PROJECTS"))
        .andExpect(jsonPath("$.result.components[3].factors[0].code").value("EDUCATION_MEETS_REQUIREMENT"))
        .andExpect(jsonPath("$.result.components[3].score").value(1.0));
    assertThat(jdbc.queryForObject(
        "select cv_analysis_id from job_matches where user_id = ? and job_id = ?::uuid order by computed_at desc limit 1",
        UUID.class, candidate.userId(), job)).isEqualTo(analysis);
  }

  private static AiMatchExplanation explanation(String summary, String skill) {
    return new AiMatchExplanation("fake", "fake-model", "match-explain-v1", summary,
        List.of(new AiMatchExplanation.Point("You already have " + skill + ".", List.of(skill))), List.of(), List.of());
  }

  @Test
  void explanationsAreGroundedAndCachedPerResultVersion() throws Exception {
    when(explainer.explain(any())).thenReturn(explanation("Your compatibility with this job is 85%. Nice work.", "Java"));
    api.post(candidate.accessToken(), "/api/v1/me/matches/" + javaJob + "/explanation", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary").value("Your compatibility with this job is 85%. Nice work."))
        .andExpect(jsonPath("$.strengths[0].skills[0]").value("Java"))
        .andExpect(jsonPath("$.provider").value("fake"));
    api.post(candidate.accessToken(), "/api/v1/me/matches/" + javaJob + "/explanation", null).andExpect(status().isOk());
    verify(explainer, times(1)).explain(any()); // cached for the same result
    api.get(candidate.accessToken(), "/api/v1/me/matches/" + javaJob)
        .andExpect(jsonPath("$.explanation.summary").value("Your compatibility with this job is 85%. Nice work."));
  }

  @Test
  void explanationFactsContainNoPersonalData() throws Exception {
    when(explainer.explain(any())).thenReturn(explanation("Your compatibility with this job is 85%. Nice work.", "Java"));
    api.post(candidate.accessToken(), "/api/v1/me/matches/" + javaJob + "/explanation", null).andExpect(status().isOk());
    var captor = org.mockito.ArgumentCaptor.forClass(com.careermatcher.aiclient.dto.MatchFacts.class);
    verify(explainer).explain(captor.capture());
    String facts = captor.getValue().toString();
    assertThat(facts).doesNotContain(candidate.email()).doesNotContain("Kim").doesNotContain(candidate.userId().toString());
    assertThat(captor.getValue().totalScore()).isEqualTo(85.0);
  }

  @Test
  void ungroundedExplanationsAreRejectedAndNotStored() throws Exception {
    when(explainer.explain(any()))
        .thenReturn(explanation("Your compatibility with this job is 99%, you will be hired.", "Java"))
        .thenReturn(explanation("Your compatibility with this job is 85% overall.", "Kubernetes"));
    for (int i = 0; i < 2; i++) {
      api.post(candidate.accessToken(), "/api/v1/me/matches/" + javaJob + "/explanation", null)
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("EXPLANATION_UNAVAILABLE"))
          .andExpect(jsonPath("$.reason").value("AI_INVALID_OUTPUT"));
    }
    assertThat(jdbc.queryForObject(
        "select count(*) from match_explanations e join job_matches m on m.id = e.match_id where m.user_id = ?",
        Integer.class, candidate.userId())).isZero();
  }

  @Test
  void aiFailureKeepsTheScoreAvailable() throws Exception {
    when(explainer.explain(any())).thenThrow(new AiAnalysisException("AI_UNAVAILABLE", "down"));
    api.post(candidate.accessToken(), "/api/v1/me/matches/" + javaJob + "/explanation", null)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.reason").value("AI_UNAVAILABLE"));
    api.get(candidate.accessToken(), "/api/v1/me/matches/" + javaJob)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalScore").value(85.0))
        .andExpect(jsonPath("$.explanation").doesNotExist());
  }

  @Test
  void matchesAreCandidateSpecificAndOnlyForVisibleJobs() throws Exception {
    Session other = api.register();
    api.get(other.accessToken(), "/api/v1/me/matches/" + javaJob)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidateData.hasConfirmedSkills").value(false))
        .andExpect(jsonPath("$.result.components[0].status").value("MISSING_CANDIDATE_DATA"));
    assertThat(jdbc.queryForObject("select count(distinct user_id) from job_matches where job_id = ?::uuid",
        Integer.class, javaJob)).isEqualTo(1); // only "other" so far; nothing shared between users

    Session admin = admins.admin();
    api.get(admin.accessToken(), "/api/v1/me/matches").andExpect(status().isForbidden());
    api.get(null, "/api/v1/me/matches").andExpect(status().isUnauthorized());

    api.patch(admin.accessToken(), "/api/v1/admin/jobs/" + javaJob + "/status", "{\"status\":\"CLOSED\"}");
    api.get(candidate.accessToken(), "/api/v1/me/matches/" + javaJob)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    api.post(candidate.accessToken(), "/api/v1/me/matches/" + javaJob + "/explanation", null).andExpect(status().isNotFound());
    verify(explainer, never()).explain(any());
  }
}
