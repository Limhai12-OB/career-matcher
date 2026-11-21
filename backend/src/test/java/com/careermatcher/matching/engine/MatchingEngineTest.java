package com.careermatcher.matching.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careermatcher.matching.engine.MatchInputs.Candidate;
import com.careermatcher.matching.engine.MatchInputs.CvFacts;
import com.careermatcher.matching.engine.MatchInputs.Education;
import com.careermatcher.matching.engine.MatchInputs.EducationLevel;
import com.careermatcher.matching.engine.MatchInputs.Experience;
import com.careermatcher.matching.engine.MatchInputs.Job;
import com.careermatcher.matching.engine.MatchInputs.JobSkill;
import com.careermatcher.matching.engine.MatchInputs.Preferences;
import com.careermatcher.matching.engine.MatchInputs.RemotePreference;
import com.careermatcher.matching.engine.MatchInputs.RemoteType;
import com.careermatcher.matching.engine.MatchInputs.Requirement;
import com.careermatcher.matching.engine.MatchResult.ComponentScore;
import com.careermatcher.matching.engine.MatchResult.Status;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Known candidate/job inputs with hand-computed expected scores. Weights: skills 45, experience
 * 20, projects 15, education 10, preferences 10.
 */
class MatchingEngineTest {

  static final YearMonth TODAY = YearMonth.of(2026, 11);
  static final UUID JAVA = UUID.randomUUID();
  static final UUID SPRING = UUID.randomUUID();
  static final UUID DOCKER = UUID.randomUUID();
  static final UUID GIT = UUID.randomUUID();
  static final UUID PYTHON = UUID.randomUUID();

  final MatchingEngine engine = new MatchingEngine(MatchingWeights.DEFAULT);

  /** Junior Java backend job: required Java + Spring Boot, preferred Docker + Git. */
  static Job javaJob() {
    Map<UUID, JobSkill> skills = new LinkedHashMap<>();
    skills.put(JAVA, new JobSkill("Java", Requirement.REQUIRED));
    skills.put(SPRING, new JobSkill("Spring Boot", Requirement.REQUIRED));
    skills.put(DOCKER, new JobSkill("Docker", Requirement.PREFERRED));
    skills.put(GIT, new JobSkill("Git", Requirement.PREFERRED));
    return new Job(UUID.randomUUID(), "Junior Java Backend Developer", skills, 6, 24, EducationLevel.BACHELOR,
        List.of("Computer Science"), "FULL_TIME", RemoteType.HYBRID, "KH", "Phnom Penh", 800, 1200, "USD", "MONTH");
  }

  static Preferences prefs(Set<String> types, RemotePreference remote, List<String> locations, Integer salary, List<String> roles) {
    return new Preferences(roles, locations, types, remote, false, salary, salary == null ? null : "USD");
  }

  static Map<Component, ComponentScore> byComponent(MatchResult r) {
    Map<Component, ComponentScore> m = new LinkedHashMap<>();
    r.components().forEach(c -> m.put(c.component(), c));
    return m;
  }

  @Test
  void perfectCandidateScores100() {
    Candidate c = new Candidate(
        Set.of(JAVA, SPRING, DOCKER, GIT),
        new CvFacts(
            List.of(new Experience("2024-01", "2024-12", false)), // 12 months >= 6, within range
            List.of(Set.of(JAVA, SPRING, DOCKER)), // 3 job skills used in projects: full credit
            List.of(new Education(EducationLevel.BACHELOR, "Computer Science", false)),
            true),
        prefs(Set.of("FULL_TIME"), RemotePreference.HYBRID, List.of("Phnom Penh"), 1000, List.of("Backend Developer")),
        "KH", "Phnom Penh");

    MatchResult r = engine.score(c, javaJob(), TODAY);

    assertThat(r.totalScore()).isEqualTo(100.0);
    assertThat(r.algorithmVersion()).isEqualTo("match-v1");
    assertThat(r.missingData()).isEmpty();
    assertThat(r.components()).allSatisfy(s -> assertThat(s.score()).isEqualTo(1.0));
    assertThat(r.weights()).containsEntry("SKILLS", 0.45).containsEntry("PREFERENCES", 0.10);
  }

  @Test
  void partialCandidateScoresExactly46point3() {
    Candidate c = new Candidate(
        Set.of(JAVA, DOCKER),
        new CvFacts(
            List.of(new Experience("2024-01", "2024-03", false)), // 3 of 6 months
            List.of(Set.of(JAVA)), // 1 of 3 needed
            List.of(new Education(EducationLevel.ASSOCIATE, "Computer Science", false)), // one level below
            true),
        prefs(Set.of("INTERNSHIP"), RemotePreference.REMOTE, List.of(), null, List.of("Data Analyst")),
        "KH", null);

    MatchResult r = engine.score(c, javaJob(), TODAY);
    Map<Component, ComponentScore> s = byComponent(r);

    // Skills: 0.75*(1/2) + 0.25*(1/2) = 0.5 -> 22.5
    assertThat(s.get(Component.SKILLS).score()).isEqualTo(0.5);
    assertThat(s.get(Component.SKILLS).contribution()).isEqualTo(22.5);
    // Experience: 3/6 = 0.5 -> 10.0
    assertThat(s.get(Component.EXPERIENCE).score()).isEqualTo(0.5);
    // Projects: 1/3 = 0.3333 -> 5.0
    assertThat(s.get(Component.PROJECTS).score()).isEqualTo(0.3333);
    assertThat(s.get(Component.PROJECTS).contribution()).isEqualTo(5.0);
    // Education: associate vs bachelor = 0.5 -> 5.0
    assertThat(s.get(Component.EDUCATION).score()).isEqualTo(0.5);
    // Preferences: type 0, arrangement remote-vs-hybrid 0.5, location same country 1, role 0 -> 0.375 -> 3.75
    assertThat(s.get(Component.PREFERENCES).score()).isEqualTo(0.375);
    // Total 22.5 + 10 + 5 + 5 + 3.75 = 46.25 -> 46.3
    assertThat(r.totalScore()).isEqualTo(46.3);

    assertThat(MatchingEngine.missingRequired(r)).containsExactly("Spring Boot");
    assertThat(s.get(Component.SKILLS).factors())
        .extracting(MatchResult.Factor::code)
        .containsExactly("REQUIRED_SKILLS_MATCHED", "REQUIRED_SKILLS_MISSING", "PREFERRED_SKILLS_MATCHED", "PREFERRED_SKILLS_MISSING");
  }

  @Test
  void missingCandidateDataIsExplicitAndNotInvented() {
    Candidate c = new Candidate(Set.of(), null, Preferences.NONE, null, null);

    MatchResult r = engine.score(c, javaJob(), TODAY);
    Map<Component, ComponentScore> s = byComponent(r);

    assertThat(s.values()).allSatisfy(x -> assertThat(x.status()).isEqualTo(Status.MISSING_CANDIDATE_DATA));
    assertThat(s.get(Component.SKILLS).score()).isZero();
    assertThat(s.get(Component.EXPERIENCE).score()).isZero();
    assertThat(s.get(Component.PROJECTS).score()).isZero();
    assertThat(s.get(Component.EDUCATION).score()).isZero();
    assertThat(s.get(Component.PREFERENCES).score()).isEqualTo(0.5); // neutral, flagged
    assertThat(r.totalScore()).isEqualTo(5.0);
    assertThat(r.missingData())
        .containsExactly("CONFIRMED_SKILLS", "CV_EXPERIENCE", "CV_PROJECTS", "CV_EDUCATION", "PREFERENCES");
  }

  @Test
  void requirementsTheJobDoesNotHaveGiveFullCreditAndBasicExtractionCountsAsMissing() {
    Job job = new Job(UUID.randomUUID(), "Python Intern", Map.of(PYTHON, new JobSkill("Python", Requirement.REQUIRED)),
        0, null, null, List.of(), "INTERNSHIP", RemoteType.REMOTE, null, null, null, null, null, null);
    Candidate c = new Candidate(
        Set.of(PYTHON),
        new CvFacts(List.of(), List.of(), List.of(), false), // basic extraction: experience/projects unknown
        prefs(Set.of("INTERNSHIP"), RemotePreference.ONSITE, List.of(), null, List.of()),
        "KH", null);

    MatchResult r = engine.score(c, job, TODAY);
    Map<Component, ComponentScore> s = byComponent(r);

    assertThat(s.get(Component.SKILLS).score()).isEqualTo(1.0); // no preferred skills: required only
    assertThat(s.get(Component.EXPERIENCE).status()).isEqualTo(Status.NOT_REQUIRED);
    assertThat(s.get(Component.PROJECTS).status()).isEqualTo(Status.MISSING_CANDIDATE_DATA);
    assertThat(s.get(Component.EDUCATION).status()).isEqualTo(Status.NOT_REQUIRED);
    // Preferences: type 1, on-site vs remote 0, location not evaluated for remote jobs -> 0.5
    assertThat(s.get(Component.PREFERENCES).score()).isEqualTo(0.5);
    // 45 + 20 + 0 + 10 + 5
    assertThat(r.totalScore()).isEqualTo(80.0);
    assertThat(r.missingData()).containsExactly("CV_PROJECTS");
  }

  @Test
  void experienceMergesOverlapsIgnoresUndatedAndPenalizesFarAboveRange() {
    var months = MatchingEngine.experienceMonths(
        List.of(
            new Experience("2024-01", "2024-06", false),
            new Experience("2024-04", "2024-09", false), // overlaps: Jan-Sep = 9 months
            new Experience(null, "2023", false), // undated: ignored
            new Experience("2026-06", null, true)), // current: Jun-Nov 2026 = 6 months
        TODAY);
    assertThat(months.total()).isEqualTo(15);
    assertThat(months.undated()).isEqualTo(1);

    Job job = javaJob(); // min 6, max 24: more than 48 months is "above range"
    Candidate senior = new Candidate(Set.of(JAVA),
        new CvFacts(List.of(new Experience("2018", "2022", false)), List.of(), List.of(), true), Preferences.NONE, null, null);
    ComponentScore exp = byComponent(engine.score(senior, job, TODAY)).get(Component.EXPERIENCE);
    assertThat(exp.score()).isEqualTo(0.9);
    assertThat(exp.factors().get(0).values()).containsEntry("candidateMonths", 60);
  }

  @Test
  void educationHandlesOngoingDegreesAndFieldMismatch() {
    Job job = javaJob();
    Candidate studying = new Candidate(Set.of(JAVA), new CvFacts(List.of(), List.of(),
        List.of(new Education(EducationLevel.BACHELOR, "Computer Science", true)), true), Preferences.NONE, null, null);
    assertThat(byComponent(engine.score(studying, job, TODAY)).get(Component.EDUCATION).score()).isEqualTo(0.8);

    Candidate business = new Candidate(Set.of(JAVA), new CvFacts(List.of(), List.of(),
        List.of(new Education(EducationLevel.MASTER, "Business Administration", false)), true), Preferences.NONE, null, null);
    ComponentScore e = byComponent(engine.score(business, job, TODAY)).get(Component.EDUCATION);
    assertThat(e.score()).isEqualTo(0.8); // meets level, different field
    assertThat(e.factors()).extracting(MatchResult.Factor::code).contains("FIELD_OF_STUDY_DIFFERENT");

    Candidate highSchool = new Candidate(Set.of(JAVA), new CvFacts(List.of(), List.of(),
        List.of(new Education(EducationLevel.HIGH_SCHOOL, null, false)), true), Preferences.NONE, null, null);
    assertThat(byComponent(engine.score(highSchool, job, TODAY)).get(Component.EDUCATION).score()).isZero();
  }

  @Test
  void salaryIsComparedMonthlyInTheSameCurrencyOnly() {
    Job yearly = new Job(UUID.randomUUID(), "Junior Java Developer", javaJob().skills(), 0, null, null, List.of(),
        "FULL_TIME", RemoteType.REMOTE, null, null, null, 24000, "USD", "YEAR"); // 2000/month
    Candidate wants2500 = new Candidate(Set.of(JAVA), null,
        new Preferences(List.of(), List.of(), Set.of(), null, false, 2500, "USD"), null, null);
    assertThat(byComponent(engine.score(wants2500, yearly, TODAY)).get(Component.PREFERENCES).score()).isEqualTo(0.8);

    Candidate wantsEuro = new Candidate(Set.of(JAVA), null,
        new Preferences(List.of(), List.of(), Set.of(), null, false, 2500, "EUR"), null, null);
    ComponentScore p = byComponent(engine.score(wantsEuro, yearly, TODAY)).get(Component.PREFERENCES);
    assertThat(p.status()).isEqualTo(Status.MISSING_CANDIDATE_DATA); // nothing comparable: neutral
    assertThat(p.score()).isEqualTo(0.5);
  }

  @Test
  void locationUsesPreferencesCountryAndRelocation() {
    Job job = javaJob(); // Phnom Penh, KH, hybrid
    Preferences singapore = new Preferences(List.of(), List.of("Singapore"), Set.of(), null, false, null, null);
    Preferences relocate = new Preferences(List.of(), List.of("Singapore"), Set.of(), null, true, null, null);
    assertThat(MatchingEngine.locationScore(new Candidate(Set.of(), null, singapore, "SG", null), singapore, job)).isEqualTo(0.0);
    assertThat(MatchingEngine.locationScore(new Candidate(Set.of(), null, relocate, "SG", null), relocate, job)).isEqualTo(0.7);
    assertThat(MatchingEngine.locationScore(new Candidate(Set.of(), null, singapore, "KH", null), singapore, job)).isEqualTo(1.0);
    Preferences cambodia = new Preferences(List.of(), List.of("cambodia"), Set.of(), null, false, null, null);
    assertThat(MatchingEngine.locationScore(new Candidate(Set.of(), null, cambodia, null, null), cambodia, job)).isEqualTo(1.0);
    assertThat(MatchingEngine.locationScore(new Candidate(Set.of(), null, Preferences.NONE, null, null), Preferences.NONE, job)).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "ANY,ONSITE,1.0", "REMOTE,REMOTE,1.0", "HYBRID,HYBRID,1.0", "REMOTE,HYBRID,0.5",
    "HYBRID,ONSITE,0.5", "ONSITE,HYBRID,0.5", "HYBRID,REMOTE,0.5", "ONSITE,REMOTE,0.0", "REMOTE,ONSITE,0.0",
  })
  void workArrangementCompatibility(RemotePreference pref, RemoteType job, double expected) {
    assertThat(MatchingEngine.remoteScore(pref, job)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Java Developer|Junior Java Backend Developer|Java Developer",
        "Backend Developer|Junior Java Backend Developer|Backend Developer",
        "Frontend Developer|Junior Java Backend Developer|",
        "QA Engineer|Junior QA Engineer|QA Engineer",
        "Data Analyst|Junior Java Backend Developer|",
      })
  void roleKeywordsIgnoreGenericWords(String role, String title, String expected) {
    assertThat(MatchingEngine.roleMatch(List.of(role), title)).isEqualTo(expected);
  }

  @Test
  void weightsAreConfigurableAndValidated() {
    MatchingEngine skillsOnly = new MatchingEngine(new MatchingWeights(1.0, 0, 0, 0, 0));
    Candidate c = new Candidate(Set.of(JAVA, DOCKER), null, Preferences.NONE, null, null);
    assertThat(skillsOnly.score(c, javaJob(), TODAY).totalScore()).isEqualTo(50.0);

    assertThatThrownBy(() -> new MatchingEngine(new MatchingWeights(0.5, 0.2, 0.15, 0.1, 0.1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new MatchingWeights(0.5, 0.2, 0.15, 0.1, 0.1).isNormalized()).isFalse();
    assertThat(MatchingWeights.DEFAULT.isNormalized()).isTrue();
  }

  @Test
  void resultsAreDeterministic() {
    Candidate c = new Candidate(Set.of(JAVA, GIT), null,
        prefs(Set.of("FULL_TIME"), RemotePreference.ANY, List.of(), null, List.of()), "KH", null);
    Job job = javaJob();
    assertThat(engine.score(c, job, TODAY)).isEqualTo(engine.score(c, job, TODAY));
  }

  @Test
  void dateParsing() {
    assertThat(MatchingEngine.parse("2024", true)).isEqualTo(YearMonth.of(2024, 1));
    assertThat(MatchingEngine.parse("2024", false)).isEqualTo(YearMonth.of(2024, 12));
    assertThat(MatchingEngine.parse("2024-06", true)).isEqualTo(YearMonth.of(2024, 6));
    assertThat(MatchingEngine.parse("June 2024", true)).isNull();
  }
}
