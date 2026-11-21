package com.careermatcher.gap.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.careermatcher.gap.engine.GapReport.Priority;
import com.careermatcher.gap.engine.GapReport.SkillGap;
import com.careermatcher.matching.engine.MatchInputs.Candidate;
import com.careermatcher.matching.engine.MatchInputs.Job;
import com.careermatcher.matching.engine.MatchInputs.JobSkill;
import com.careermatcher.matching.engine.MatchInputs.Preferences;
import com.careermatcher.matching.engine.MatchInputs.RemoteType;
import com.careermatcher.matching.engine.MatchInputs.Requirement;
import com.careermatcher.matching.engine.MatchingEngine;
import com.careermatcher.matching.engine.MatchingWeights;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Known inputs, hand-computed expectations. Jobs require no experience or education and the
 * candidate has no CV or preferences, so each job scores 35 + 45 x skills score.
 */
class GapAnalyzerTest {

  static final YearMonth TODAY = YearMonth.of(2026, 11);
  static final UUID JAVA = UUID.randomUUID(), SPRING = UUID.randomUUID(), DOCKER = UUID.randomUUID(),
      SQL = UUID.randomUUID(), GIT = UUID.randomUUID(), PYTHON = UUID.randomUUID();
  static final Map<UUID, String> NAMES =
      Map.of(JAVA, "Java", SPRING, "Spring Boot", DOCKER, "Docker", SQL, "SQL", GIT, "Git", PYTHON, "Python");

  final MatchingEngine engine = new MatchingEngine(MatchingWeights.DEFAULT);
  final GapAnalyzer analyzer = new GapAnalyzer(engine);

  static Job job(String title, List<UUID> required, List<UUID> preferred) {
    Map<UUID, JobSkill> skills = new LinkedHashMap<>();
    required.forEach(id -> skills.put(id, new JobSkill(NAMES.get(id), Requirement.REQUIRED)));
    preferred.forEach(id -> skills.put(id, new JobSkill(NAMES.get(id), Requirement.PREFERRED)));
    return new Job(UUID.randomUUID(), title, skills, 0, null, null, List.of(), "FULL_TIME", RemoteType.REMOTE,
        null, null, null, null, null, null);
  }

  List<GapAnalyzer.Target> targets(Candidate c, Job... jobs) {
    return java.util.Arrays.stream(jobs)
        .map(j -> new GapAnalyzer.Target(j, "Acme", "REAL", engine.score(c, j, TODAY)))
        .toList();
  }

  static Candidate candidate(UUID... skills) {
    return new Candidate(Set.of(skills), null, Preferences.NONE, null, null);
  }

  @Test
  void prioritizesGapsAndMeasuresTheirScoreImpact() {
    Candidate c = candidate(JAVA, GIT);
    Job j1 = job("Backend", List.of(JAVA, SPRING), List.of(DOCKER)); // skills .375 -> 51.9
    Job j2 = job("Data", List.of(JAVA, SQL), List.of(DOCKER, GIT)); // skills .5   -> 57.5
    Job j3 = job("Python", List.of(PYTHON), List.of(SQL)); //          skills 0    -> 35.0

    GapReport r = analyzer.analyze(c, targets(c, j1, j2, j3), Map.of(PYTHON, "PROGRAMMING_LANGUAGE"), TODAY);

    assertThat(r.targets()).extracting(GapReport.TargetJob::score).containsExactly(51.9, 57.5, 35.0);
    assertThat(r.averageScore()).isEqualTo(48.1); // (51.9 + 57.5 + 35) / 3
    assertThat(r.requiredCoverage()).isEqualTo(40.0); // Java in j1 and j2: 2 of 5 required
    assertThat(r.algorithmVersion()).isEqualTo("gap-v1");
    assertThat(r.matchAlgorithmVersion()).isEqualTo("match-v1");

    // 3 targets: critical needs >= 2 jobs requiring the skill; none does.
    assertThat(r.gaps()).extracting(SkillGap::name).containsExactly("Python", "SQL", "Spring Boot", "Docker");
    assertThat(r.gaps()).extracting(SkillGap::priority)
        .containsExactly(Priority.HIGH, Priority.HIGH, Priority.HIGH, Priority.MEDIUM);
    // Python: j3 35.0 -> 68.8, mean over 3 jobs = 11.3
    // SQL:    j2 57.5 -> 74.4 (+16.9), j3 35.0 -> 46.3 (+11.3) -> 9.4
    // Spring: j1 51.9 -> 68.8 (+16.9) -> 5.6
    // Docker: j1 51.9 -> 63.1 (+11.2), j2 57.5 -> 63.1 (+5.6) -> 5.6
    assertThat(r.gaps()).extracting(SkillGap::averageGain).containsExactly(11.3, 9.4, 5.6, 5.6);
    SkillGap sql = r.gaps().get(1);
    assertThat(sql.requiredIn()).isEqualTo(1);
    assertThat(sql.preferredIn()).isEqualTo(1);
    assertThat(sql.jobIds()).containsExactly(j2.id(), j3.id());
    assertThat(r.gaps().get(0).category()).isEqualTo("PROGRAMMING_LANGUAGE");
    assertThat(r.gaps().get(1).category()).isEqualTo("OTHER");

    assertThat(r.strengths()).hasSize(1);
    assertThat(r.strengths().get(0).name()).isEqualTo("Java");
    assertThat(r.strengths().get(0).requiredIn()).isEqualTo(2);
  }

  @Test
  void skillsRequiredByManyTargetsAreCritical() {
    Candidate c = candidate(GIT);
    GapReport r = analyzer.analyze(c, targets(c,
        job("A", List.of(DOCKER), List.of()),
        job("B", List.of(DOCKER, SQL), List.of()),
        job("C", List.of(SQL), List.of(DOCKER)),
        job("D", List.of(PYTHON), List.of())), Map.of(), TODAY);
    // 4 targets: threshold max(2, ceil(1.2)) = 2
    assertThat(r.gaps()).extracting(SkillGap::name, SkillGap::priority).containsExactly(
        org.assertj.core.groups.Tuple.tuple("Docker", Priority.CRITICAL),
        org.assertj.core.groups.Tuple.tuple("SQL", Priority.CRITICAL),
        org.assertj.core.groups.Tuple.tuple("Python", Priority.HIGH));
    assertThat(r.requiredCoverage()).isZero();
  }

  @Test
  void candidateWithEverythingHasNoGapsAndFullCoverage() {
    Candidate c = candidate(JAVA, SPRING, DOCKER);
    GapReport r = analyzer.analyze(c, targets(c, job("Backend", List.of(JAVA, SPRING), List.of(DOCKER))), Map.of(), TODAY);
    assertThat(r.gaps()).isEmpty();
    assertThat(r.requiredCoverage()).isEqualTo(100.0);
    assertThat(r.averageScore()).isEqualTo(80.0); // 45 + 20 + 0 + 10 + 5
  }

  @Test
  void noTargetsYieldsAnEmptyReport() {
    GapReport r = analyzer.analyze(candidate(), List.of(), Map.of(), TODAY);
    assertThat(r.gaps()).isEmpty();
    assertThat(r.averageScore()).isZero();
    assertThat(r.requiredCoverage()).isZero();
  }

  @Test
  void gainsMatchActuallyRescoringWithTheSkill() {
    Candidate c = candidate(JAVA);
    Job j = job("Backend", List.of(JAVA, SPRING), List.of());
    GapReport r = analyzer.analyze(c, targets(c, j), Map.of(), TODAY);
    double after = engine.score(candidate(JAVA, SPRING), j, TODAY).totalScore();
    double before = engine.score(c, j, TODAY).totalScore();
    assertThat(r.gaps().get(0).averageGain()).isEqualTo(GapAnalyzer.round(after - before));
  }
}
