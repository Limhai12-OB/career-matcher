package com.careermatcher.matching.engine;

import java.util.List;
import java.util.Map;

/**
 * Deterministic match result.
 *
 * @param totalScore 0-100, one decimal
 * @param components per-component breakdown, in {@link Component} order
 * @param missingData stable keys of candidate data whose absence lowered accuracy
 */
public record MatchResult(
    String algorithmVersion,
    double totalScore,
    Map<String, Double> weights,
    List<ComponentScore> components,
    List<String> missingData) {

  public enum Status {
    /** Calculated from candidate data. */
    SCORED,
    /** The candidate data needed is absent; see the component's factors. */
    MISSING_CANDIDATE_DATA,
    /** The job has no requirement here; full credit. */
    NOT_REQUIRED
  }

  /**
   * @param score 0-1, four decimals
   * @param contribution weight x score x 100, two decimals
   */
  public record ComponentScore(
      Component component, Status status, double score, double weight, double contribution, List<Factor> factors) {}

  /**
   * One reason behind a component score. {@code skills} holds catalog skill names; {@code values}
   * holds the numbers used (months, levels, amounts), so explanations can cite them exactly.
   */
  public record Factor(String code, List<String> skills, Map<String, Object> values) {

    /** Sorted, immutable values so serialization (and the result hash) is stable. */
    public Factor {
      skills = List.copyOf(skills);
      values = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(values));
    }

    public static Factor of(String code) {
      return new Factor(code, List.of(), Map.of());
    }

    public static Factor skills(String code, List<String> skills) {
      return new Factor(code, List.copyOf(skills), Map.of());
    }

    public static Factor values(String code, Map<String, Object> values) {
      return new Factor(code, List.of(), Map.copyOf(values));
    }
  }
}
