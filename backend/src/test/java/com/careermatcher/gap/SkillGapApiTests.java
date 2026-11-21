package com.careermatcher.gap;

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
import com.careermatcher.aiclient.AiRoadmapClient;
import com.careermatcher.aiclient.dto.AiRoadmap;
import com.careermatcher.aiclient.dto.RoadmapFacts;
import com.careermatcher.support.AdminSupport;
import com.careermatcher.support.CvTestBase;
import com.careermatcher.support.TestApi.Session;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Skill gap reports against the real matching engine. The job catalogue is shared with other
 * tests, so assertions check consistency with the candidate's own match results rather than a
 * fixed job list.
 */
class SkillGapApiTests extends CvTestBase {

  @MockitoBean AiRoadmapClient roadmaps;
  @Autowired AdminSupport admins;
  @Autowired JdbcTemplate jdbc;

  Session candidate;

  private String skill(String slug) {
    return jdbc.queryForObject("select id::text from skills where slug = ?", String.class, slug);
  }

  private void confirm(Session s, String skillId) {
    jdbc.update(
        "insert into candidate_skills (id, user_id, skill_id, source, created_at) values (gen_random_uuid(), ?, ?::uuid, 'MANUAL', now())",
        s.userId(), skillId);
  }

  private String generate(Session s) throws Exception {
    return api.post(s.accessToken(), "/api/v1/me/skill-gaps", null)
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  }

  /** A roadmap built only from the facts (first gap, its curated resources). */
  private static AiRoadmap roadmapFrom(RoadmapFacts f, String summary) {
    RoadmapFacts.Gap g = f.gaps().get(0);
    List<String> res = f.resources().stream().filter(r -> r.skill().equals(g.skill())).map(RoadmapFacts.Resource::id).limit(3).toList();
    return new AiRoadmap("anthropic", "claude-opus-5-5", "roadmap-v1", summary,
        List.of(new AiRoadmap.Step(g.skill(), "Learn " + g.skill(),
            g.skill() + " is required by " + g.requiredIn() + " of your " + f.targetJobCount() + " target jobs.",
            List.of("Build a small project that uses " + g.skill()), 3, res)));
  }

  @BeforeEach
  void setUp() throws Exception {
    reset(roadmaps);
    // Make sure the catalogue has jobs requiring skills this candidate lacks.
    Session admin = admins.admin();
    for (String slug : List.of("spring-boot", "docker")) {
      String body = com.careermatcher.job.JobTestDataAccess.job(jdbc, new java.util.LinkedHashMap<>(Map.of(
          "title", "\"Gap Test " + slug + " " + System.nanoTime() + "\"",
          "educationLevel", "null", "educationFields", "[]", "minExperienceMonths", "0", "maxExperienceMonths", "null",
          "salaryMin", "null", "salaryMax", "null", "salaryCurrency", "null", "salaryPeriod", "null",
          "skills", "[{\"skillId\":\"" + skill("java") + "\",\"requirement\":\"REQUIRED\"},"
              + "{\"skillId\":\"" + skill(slug) + "\",\"requirement\":\"REQUIRED\"}]")));
      String id = JsonPath.read(api.post(admin.accessToken(), "/api/v1/admin/jobs", body)
          .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
      api.patch(admin.accessToken(), "/api/v1/admin/jobs/" + id + "/status", "{\"status\":\"PUBLISHED\"}")
          .andExpect(status().isOk());
    }
    candidate = api.register();
    api.put(candidate.accessToken(), "/api/v1/me/profile", json("{'fullName':'Dara','countryCode':'KH'}"))
        .andExpect(status().isOk());
    confirm(candidate, skill("java"));
  }

  @Test
  void reportIsConsistentWithTheCandidatesTopMatches() throws Exception {
    String report = generate(candidate);
    List<String> targetIds = JsonPath.read(report, "$.targets[*].jobId");
    List<Double> targetScores = JsonPath.read(report, "$.targets[*].score");
    assertThat(targetIds).hasSizeBetween(2, 10);
    assertThat(targetScores).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    assertThat((String) JsonPath.read(report, "$.algorithmVersion")).isEqualTo("gap-v1");
    assertThat((String) JsonPath.read(report, "$.matchAlgorithmVersion")).isEqualTo("match-v1");

    // Recount every gap from the stored match breakdowns of the target jobs.
    Map<String, int[]> expected = new HashMap<>();
    int requiredTotal = 0, requiredHave = 0;
    double scoreSum = 0;
    for (int i = 0; i < targetIds.size(); i++) {
      String detail = api.get(candidate.accessToken(), "/api/v1/me/matches/" + targetIds.get(i))
          .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      assertThat(((Number) JsonPath.read(detail, "$.result.totalScore")).doubleValue()).isEqualTo(targetScores.get(i));
      scoreSum += targetScores.get(i);
      String job = api.get(candidate.accessToken(), "/api/v1/jobs/" + targetIds.get(i))
          .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      List<Map<String, Object>> skills = JsonPath.read(job, "$.skills[*]");
      for (Map<String, Object> s : skills) {
        boolean required = "REQUIRED".equals(s.get("requirement"));
        boolean has = s.get("name").equals("Java");
        if (required) { requiredTotal++; if (has) requiredHave++; }
        if (!has) expected.computeIfAbsent((String) s.get("name"), k -> new int[2])[required ? 0 : 1]++;
      }
    }
    List<Map<String, Object>> gaps = JsonPath.read(report, "$.gaps[*]");
    assertThat(gaps).extracting(g -> (String) g.get("name")).containsExactlyInAnyOrderElementsOf(expected.keySet());
    int criticalThreshold = Math.max(2, (int) Math.ceil(0.3 * targetIds.size()));
    for (Map<String, Object> g : gaps) {
      int[] c = expected.get((String) g.get("name"));
      assertThat(g.get("requiredIn")).isEqualTo(c[0]);
      assertThat(g.get("preferredIn")).isEqualTo(c[1]);
      String priority = c[0] >= criticalThreshold ? "CRITICAL" : c[0] >= 1 ? "HIGH" : c[1] >= 2 ? "MEDIUM" : "LOW";
      assertThat(g.get("priority")).isEqualTo(priority);
      assertThat(((Number) g.get("averageGain")).doubleValue()).isGreaterThanOrEqualTo(0);
      assertThat(g.get("closed")).isEqualTo(false);
    }
    assertThat(((Number) JsonPath.read(report, "$.requiredCoverage")).doubleValue())
        .isEqualTo(Math.round(1000.0 * requiredHave / requiredTotal) / 10.0);
    assertThat(((Number) JsonPath.read(report, "$.averageScore")).doubleValue())
        .isEqualTo(Math.round(scoreSum / targetIds.size() * 10) / 10.0);
    assertThat((List<String>) JsonPath.read(report, "$.strengths[*].name")).containsExactly("Java");

    // Curated, verified resources are attached to gaps that have them.
    List<String> urls = JsonPath.read(report, "$.gaps[?(@.name == 'Spring Boot')].resources[*].url");
    assertThat(urls).isNotEmpty().allMatch(u -> u.startsWith("https://"));
  }

  @Test
  void progressTracksReportsAndClosedGaps() throws Exception {
    String first = generate(candidate);
    String critical = JsonPath.read(first, "$.gaps[0].name");
    String criticalId = JsonPath.read(first, "$.gaps[0].skillId");
    assertThat((String) JsonPath.read(first, "$.gaps[0].priority")).isIn("CRITICAL", "HIGH");
    double coverageBefore = ((Number) JsonPath.read(first, "$.requiredCoverage")).doubleValue();

    confirm(candidate, criticalId);
    // The stored report is a snapshot, but shows the gap as closed now.
    api.get(candidate.accessToken(), "/api/v1/me/skill-gaps/" + JsonPath.read(first, "$.id"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gaps[0].name").value(critical))
        .andExpect(jsonPath("$.gaps[0].closed").value(true));

    String second = generate(candidate);
    assertThat((List<String>) JsonPath.read(second, "$.gaps[*].name")).doesNotContain(critical);
    assertThat(((Number) JsonPath.read(second, "$.requiredCoverage")).doubleValue()).isGreaterThanOrEqualTo(coverageBefore);

    api.get(candidate.accessToken(), "/api/v1/me/skill-gaps")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.history.length()").value(2))
        .andExpect(jsonPath("$.history[0].id").value((String) JsonPath.read(first, "$.id")))
        .andExpect(jsonPath("$.latestReportId").value((String) JsonPath.read(second, "$.id")))
        .andExpect(jsonPath("$.closedSinceFirst[0]").value(critical))
        .andExpect(jsonPath("$.confirmedSkillCount").value(2));
    api.get(candidate.accessToken(), "/api/v1/me/skill-gaps/latest")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value((String) JsonPath.read(second, "$.id")));
  }

  @Test
  void roadmapIsGroundedGeneratedOnceAndTracksCompletion() throws Exception {
    AtomicReference<RoadmapFacts> sent = new AtomicReference<>();
    when(roadmaps.roadmap(any())).thenAnswer(inv -> {
      RoadmapFacts f = inv.getArgument(0);
      sent.set(f);
      return roadmapFrom(f, "A focused plan for the skills your best-matching jobs ask for most.");
    });
    String report = generate(candidate);
    String id = JsonPath.read(report, "$.id");
    String topGap = JsonPath.read(report, "$.gaps[0].name");

    String withRoadmap = api.post(candidate.accessToken(), "/api/v1/me/skill-gaps/" + id + "/roadmap", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadmap.provider").value("anthropic"))
        .andExpect(jsonPath("$.roadmap.steps[0].skill").value(topGap))
        .andExpect(jsonPath("$.roadmap.steps[0].done").value(false))
        .andReturn().getResponse().getContentAsString();
    List<String> urls = JsonPath.read(withRoadmap, "$.roadmap.steps[0].resources[*].url");
    assertThat(urls).allMatch(u -> u.startsWith("https://"));

    // Facts: gaps, skill names and resource ids only; no personal data or URLs.
    RoadmapFacts f = sent.get();
    assertThat(f.gaps()).hasSizeLessThanOrEqualTo(6);
    assertThat(f.existingSkills()).containsExactly("Java");
    String factsText = f.toString();
    assertThat(factsText).doesNotContain("Dara", candidate.email(), "https://");

    // Cached: a second request does not call the AI service again.
    api.post(candidate.accessToken(), "/api/v1/me/skill-gaps/" + id + "/roadmap", null).andExpect(status().isOk());
    verify(roadmaps, times(1)).roadmap(any());

    // Confirming the skill marks the step done.
    confirm(candidate, JsonPath.read(report, "$.gaps[0].skillId"));
    api.get(candidate.accessToken(), "/api/v1/me/skill-gaps/" + id)
        .andExpect(jsonPath("$.roadmap.steps[0].done").value(true));
  }

  @Test
  void roadmapsWithUnsupportedClaimsOrInventedContentAreRejected() throws Exception {
    String id = JsonPath.read(generate(candidate), "$.id");
    for (String summary : List.of(
        "Follow this plan and you will get a job within a few months, guaranteed.",
        "Finishing this roadmap makes you fully qualified for every target role.",
        "This plan covers what 95 percent of employers look for in junior developers.")) {
      reset(roadmaps);
      when(roadmaps.roadmap(any())).thenAnswer(inv -> roadmapFrom(inv.getArgument(0), summary));
      api.post(candidate.accessToken(), "/api/v1/me/skill-gaps/" + id + "/roadmap", null)
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("ROADMAP_UNAVAILABLE"))
          .andExpect(jsonPath("$.reason").value("AI_INVALID_OUTPUT"));
    }
    // A step for a skill that is not a gap, or an invented resource id.
    reset(roadmaps);
    when(roadmaps.roadmap(any())).thenAnswer(inv -> {
      AiRoadmap r = roadmapFrom(inv.getArgument(0), "A focused plan for the skills your best-matching jobs ask for.");
      AiRoadmap.Step s = r.steps().get(0);
      return new AiRoadmap(r.provider(), r.model(), r.promptVersion(), r.summary(),
          List.of(new AiRoadmap.Step("Java", s.title(), s.why(), s.activities(), 2, List.of("not-a-resource"))));
    });
    api.post(candidate.accessToken(), "/api/v1/me/skill-gaps/" + id + "/roadmap", null)
        .andExpect(status().isServiceUnavailable());
    assertThat(jdbc.queryForObject("select roadmap is null from skill_gap_reports where id = ?::uuid", Boolean.class, id)).isTrue();

    // AI outage: the report stays available.
    reset(roadmaps);
    when(roadmaps.roadmap(any())).thenThrow(new AiAnalysisException("AI_TIMEOUT", "slow"));
    api.post(candidate.accessToken(), "/api/v1/me/skill-gaps/" + id + "/roadmap", null)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.reason").value("AI_TIMEOUT"));
    api.get(candidate.accessToken(), "/api/v1/me/skill-gaps/" + id).andExpect(status().isOk());
  }

  @Test
  void reportsArePrivateToTheirOwner() throws Exception {
    String id = JsonPath.read(generate(candidate), "$.id");
    Session other = api.register();
    api.get(other.accessToken(), "/api/v1/me/skill-gaps/" + id)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("GAP_REPORT_NOT_FOUND"));
    api.post(other.accessToken(), "/api/v1/me/skill-gaps/" + id + "/roadmap", null).andExpect(status().isNotFound());
    api.get(other.accessToken(), "/api/v1/me/skill-gaps")
        .andExpect(jsonPath("$.history.length()").value(0))
        .andExpect(jsonPath("$.latestReportId").doesNotExist());
    api.get(other.accessToken(), "/api/v1/me/skill-gaps/latest")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NO_GAP_REPORT"));

    Session admin = admins.admin();
    api.get(admin.accessToken(), "/api/v1/me/skill-gaps").andExpect(status().isForbidden());
    api.post(admin.accessToken(), "/api/v1/me/skill-gaps", null).andExpect(status().isForbidden());
    api.get(null, "/api/v1/me/skill-gaps").andExpect(status().isUnauthorized());
    verify(roadmaps, never()).roadmap(any());
  }
}
