package com.careermatcher.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.careermatcher.aiclient.dto.AiMatchExplanation;
import com.careermatcher.aiclient.dto.AiMatchExplanation.Point;
import com.careermatcher.aiclient.dto.MatchFacts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExplanationGuardTest {

  static final JsonMapper JSON = JsonMapper.builder().build();

  static MatchFacts facts(double total) {
    return new MatchFacts(
        "match-v1",
        new MatchFacts.JobFacts("Junior Java Developer", "Acme", "FULL_TIME", "HYBRID", "JUNIOR", "REAL"),
        total,
        List.of(new MatchFacts.ComponentFacts("SKILLS", 45, 50, "SCORED", List.of(
            new MatchFacts.FactorFacts("REQUIRED_SKILLS_MISSING", List.of("Spring Boot"), Map.of()),
            new MatchFacts.FactorFacts("EXPERIENCE_BELOW_MINIMUM", List.of(), Map.of("candidateMonths", 3, "requiredMonths", 6))))),
        List.of());
  }

  static AiMatchExplanation explanation(String summary, String skill) {
    return new AiMatchExplanation("p", "m", "v", summary,
        List.of(new Point("Learn " + skill + " next.", List.of(skill))), List.of(), List.of());
  }

  @Test
  void acceptsFactsAndEitherRoundingOfTheTotal() {
    assertThat(ExplanationGuard.violations(facts(58.5), explanation("Your compatibility is 59%; you have 3 of 6 months.", "Spring Boot"), JSON)).isEmpty();
    assertThat(ExplanationGuard.violations(facts(58.5), explanation("Your compatibility is 58% for this role.", "Spring Boot"), JSON)).isEmpty();
  }

  @Test
  void rejectsInventedSkillsNumbersAndLinks() {
    assertThat(ExplanationGuard.violations(facts(58.5), explanation("Your compatibility is 60% for this role.", "Spring Boot"), JSON))
        .containsExactly("unsupported number 60");
    assertThat(ExplanationGuard.violations(facts(58.5), explanation("Your compatibility is 59% for this role.", "Kubernetes"), JSON))
        .containsExactly("unknown skill Kubernetes");
    assertThat(ExplanationGuard.violations(facts(58.5), explanation("See https://example.org for 59% details.", "Spring Boot"), JSON))
        .contains("links or markup");
  }
}
