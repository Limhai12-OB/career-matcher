package com.careermatcher.gap;

import static org.assertj.core.api.Assertions.assertThat;

import com.careermatcher.aiclient.dto.AiRoadmap;
import com.careermatcher.aiclient.dto.RoadmapFacts;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RoadmapGuardTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final RoadmapFacts FACTS = new RoadmapFacts(10, 48.3, 42.5,
      List.of(new RoadmapFacts.Gap("Spring Boot", "FRAMEWORK", "CRITICAL", 4, 1, 6.8, List.of("Backend Intern")),
          new RoadmapFacts.Gap("Docker", "DEVOPS", "MEDIUM", 0, 3, 1.7, List.of())),
      List.of("Java"),
      List.of(new RoadmapFacts.Resource("r1", "Spring Boot", "Guide", "Spring", "TUTORIAL"),
          new RoadmapFacts.Resource("r2", "Docker", "Get started", "Docker", "TUTORIAL")));

  static AiRoadmap.Step step(String skill, List<String> res, String why) {
    return new AiRoadmap.Step(skill, "Learn " + skill, why, List.of("Build a small project"), 4, res);
  }

  static AiRoadmap roadmap(String summary, AiRoadmap.Step... steps) {
    return new AiRoadmap("p", "m", "v", summary, List.of(steps));
  }

  static final AiRoadmap.Step OK = step("Spring Boot", List.of("r1"), "Required by 4 of your 10 jobs; about 6.8 points.");

  @Test
  void groundedRoadmapPasses() {
    assertThat(RoadmapGuard.violations(FACTS, roadmap("Your average compatibility is about 48% today.", OK), JSON)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "This guarantees you a role.", "You will land a job soon.", "You'll get hired faster.",
      "Expect a job offer.", "This makes you job-ready.", "You will be fully qualified.", "We promise progress."})
  void outcomeClaimsAreRejected(String claim) {
    assertThat(RoadmapGuard.violations(FACTS, roadmap("A plan for you. " + claim, OK), JSON))
        .anyMatch(p -> p.startsWith("unsupported claim"));
  }

  @Test
  void inventedSkillsResourcesNumbersAndLinksAreRejected() {
    assertThat(RoadmapGuard.violations(FACTS, roadmap("A plan for you.", step("Kubernetes", List.of(), "Needed a lot.")), JSON))
        .contains("step for a skill that is not a gap: Kubernetes");
    assertThat(RoadmapGuard.violations(FACTS, roadmap("A plan for you.", step("Spring Boot", List.of("r2"), "Needed a lot.")), JSON))
        .contains("resource r2 is not for Spring Boot");
    assertThat(RoadmapGuard.violations(FACTS, roadmap("Used by 75% of companies.", OK), JSON))
        .containsExactly("unsupported number 75");
    assertThat(RoadmapGuard.violations(FACTS, roadmap("See www.example.com for more.", OK), JSON))
        .contains("links or markup");
    assertThat(RoadmapGuard.violations(FACTS, roadmap("A plan for you.", OK, OK), JSON))
        .contains("duplicate step for Spring Boot");
  }
}
