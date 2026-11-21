package com.careermatcher.matching.engine;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Component weights of the matching score (app.matching.weights.*). Validated at startup: each in
 * [0,1] and summing to 1.0, otherwise the application refuses to start.
 */
@Validated
@ConfigurationProperties("app.matching.weights")
public record MatchingWeights(
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.45") double skills,
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.20") double experience,
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.15") double projects,
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.10") double education,
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.10") double preferences) {

  public static final MatchingWeights DEFAULT = new MatchingWeights(0.45, 0.20, 0.15, 0.10, 0.10);

  @AssertTrue(message = "app.matching.weights must sum to 1.0")
  public boolean isNormalized() {
    return Math.abs(skills + experience + projects + education + preferences - 1.0) < 1e-9;
  }

  public double of(Component c) {
    return switch (c) {
      case SKILLS -> skills;
      case EXPERIENCE -> experience;
      case PROJECTS -> projects;
      case EDUCATION -> education;
      case PREFERENCES -> preferences;
    };
  }

  public Map<String, Double> asMap() {
    Map<String, Double> m = new LinkedHashMap<>();
    for (Component c : Component.values()) {
      m.put(c.name(), of(c));
    }
    return m;
  }
}
