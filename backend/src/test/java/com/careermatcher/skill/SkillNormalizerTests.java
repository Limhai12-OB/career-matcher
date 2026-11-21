package com.careermatcher.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.careermatcher.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SkillNormalizerTests {

  @Autowired SkillNormalizer normalizer;

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Java|Java",
        "  JAVA |Java",
        "Java 17|Java",
        "Java SE|Java",
        "javascript|JavaScript",
        "JS|JavaScript",
        "ES6|JavaScript",
        "ReactJS|React",
        "React.js|React",
        "Node.js|Node.js",
        "nodejs|Node.js",
        "Postgres|PostgreSQL",
        "PostgreSQL 15|PostgreSQL",
        "C++|C++",
        "C#|C#",
        "Golang|Go",
        "Spring Boot 3|Spring Boot",
        "AWS|Amazon Web Services",
        "k8s|Kubernetes",
        "RESTful APIs|REST APIs",
        "Python 3.12|Python",
        "Vue.js 3.x|Vue.js",
        "Docker (containers)|Docker",
        "Kotlin programming language|Kotlin",
        "• Git,|Git",
      })
  void resolvesAliasesToCanonicalSkills(String raw, String expected) {
    assertThat(normalizer.resolve(raw)).get().extracting(SkillRef::name).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "Quantum Basket Weaving", "Javaa", "Jav", "Script", "Microsoft"})
  void unknownNamesAreNotGuessed(String raw) {
    assertThat(normalizer.resolve(raw)).isEmpty();
  }

  @org.junit.jupiter.api.Test
  void javaAndJavascriptStayDistinct() {
    assertThat(normalizer.resolve("Java").orElseThrow().id())
        .isNotEqualTo(normalizer.resolve("JavaScript").orElseThrow().id());
  }
}
