package com.careermatcher.gap.engine;

import com.careermatcher.gap.engine.GapReport.Priority;
import com.careermatcher.gap.engine.GapReport.SkillGap;
import com.careermatcher.gap.engine.GapReport.Strength;
import com.careermatcher.gap.engine.GapReport.TargetJob;
import com.careermatcher.matching.engine.MatchInputs.Candidate;
import com.careermatcher.matching.engine.MatchInputs.Job;
import com.careermatcher.matching.engine.MatchInputs.JobSkill;
import com.careermatcher.matching.engine.MatchInputs.Requirement;
import com.careermatcher.matching.engine.MatchResult;
import com.careermatcher.matching.engine.MatchingEngine;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic skill gap analysis (algorithm {@value #VERSION}), built on the matching engine.
 *
 * <ul>
 *   <li>Gaps: skills listed by the target jobs that the candidate has not confirmed.
 *   <li>Priority: CRITICAL if required by at least max(2, ceil(30% of targets)) jobs; HIGH if
 *       required by any; MEDIUM if preferred by two or more; LOW otherwise.
 *   <li>Average gain: the candidate is re-scored by the matching engine as if they had the skill;
 *       the mean score change across all target jobs (one decimal). This makes "why it matters" a
 *       measured number, not an assertion.
 * </ul>
 */
public final class GapAnalyzer {

  public static final String VERSION = "gap-v1";
  static final double CRITICAL_SHARE = 0.3;

  private final MatchingEngine engine;

  public GapAnalyzer(MatchingEngine engine) {
    this.engine = engine;
  }

  /** A target job with its current result. */
  public record Target(Job job, String company, String dataOrigin, MatchResult result) {}

  public GapReport analyze(Candidate candidate, List<Target> targets, Map<UUID, String> categories, YearMonth today) {
    int n = targets.size();
    Map<UUID, String> names = new LinkedHashMap<>();
    Map<UUID, int[]> counts = new LinkedHashMap<>(); // [required, preferred]
    Map<UUID, List<UUID>> jobsBySkill = new LinkedHashMap<>();
    Map<UUID, Integer> strengths = new LinkedHashMap<>();
    int requiredTotal = 0;
    int requiredHave = 0;

    for (Target t : targets) {
      for (Map.Entry<UUID, JobSkill> e : t.job().skills().entrySet()) {
        UUID id = e.getKey();
        boolean required = e.getValue().requirement() == Requirement.REQUIRED;
        boolean has = candidate.skillIds().contains(id);
        if (required) {
          requiredTotal++;
          if (has) requiredHave++;
        }
        if (has) {
          if (required) {
            strengths.merge(id, 1, Integer::sum);
            names.putIfAbsent(id, e.getValue().name());
          }
          continue;
        }
        names.putIfAbsent(id, e.getValue().name());
        int[] c = counts.computeIfAbsent(id, k -> new int[2]);
        c[required ? 0 : 1]++;
        jobsBySkill.computeIfAbsent(id, k -> new ArrayList<>()).add(t.job().id());
      }
    }

    int criticalThreshold = Math.max(2, (int) Math.ceil(CRITICAL_SHARE * n));
    List<SkillGap> gaps = new ArrayList<>();
    for (Map.Entry<UUID, int[]> e : counts.entrySet()) {
      UUID id = e.getKey();
      int req = e.getValue()[0];
      int pref = e.getValue()[1];
      Priority priority =
          req >= criticalThreshold ? Priority.CRITICAL
              : req >= 1 ? Priority.HIGH
              : pref >= 2 ? Priority.MEDIUM
              : Priority.LOW;
      gaps.add(new SkillGap(
          id, names.get(id), categories.getOrDefault(id, "OTHER"), priority, req, pref,
          averageGain(candidate, targets, id, today), List.copyOf(jobsBySkill.get(id))));
    }
    gaps.sort(
        Comparator.comparing(SkillGap::priority)
            .thenComparing(Comparator.comparingDouble(SkillGap::averageGain).reversed())
            .thenComparing(SkillGap::requiredIn, Comparator.reverseOrder())
            .thenComparing(SkillGap::name, String.CASE_INSENSITIVE_ORDER));

    List<Strength> strong =
        strengths.entrySet().stream()
            .map(e -> new Strength(e.getKey(), names.get(e.getKey()), e.getValue()))
            .sorted(Comparator.comparingInt(Strength::requiredIn).reversed().thenComparing(Strength::name, String.CASE_INSENSITIVE_ORDER))
            .toList();

    double avg = n == 0 ? 0 : round(targets.stream().mapToDouble(t -> t.result().totalScore()).average().orElse(0));
    double coverage = requiredTotal == 0 ? 0 : round(100.0 * requiredHave / requiredTotal);
    return new GapReport(
        VERSION, MatchingEngine.VERSION,
        targets.stream().map(t -> new TargetJob(t.job().id(), t.job().title(), t.company(), t.dataOrigin(), t.result().totalScore())).toList(),
        avg, coverage, List.copyOf(gaps), strong);
  }

  /** Mean score change across all targets if the candidate had {@code skillId}. */
  private double averageGain(Candidate c, List<Target> targets, UUID skillId, YearMonth today) {
    Set<UUID> withSkill = new HashSet<>(c.skillIds());
    withSkill.add(skillId);
    Candidate improved = new Candidate(withSkill, c.cv(), c.preferences(), c.countryCode(), c.city());
    double total = 0;
    for (Target t : targets) {
      total += engine.score(improved, t.job(), today).totalScore() - t.result().totalScore();
    }
    return targets.isEmpty() ? 0 : round(total / targets.size());
  }

  static double round(double v) {
    return Math.round(v * 10) / 10.0;
  }
}
