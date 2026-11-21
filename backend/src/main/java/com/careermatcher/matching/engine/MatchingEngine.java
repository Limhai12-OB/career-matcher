package com.careermatcher.matching.engine;

import com.careermatcher.matching.engine.MatchInputs.Candidate;
import com.careermatcher.matching.engine.MatchInputs.Education;
import com.careermatcher.matching.engine.MatchInputs.Experience;
import com.careermatcher.matching.engine.MatchInputs.Job;
import com.careermatcher.matching.engine.MatchInputs.JobSkill;
import com.careermatcher.matching.engine.MatchInputs.Preferences;
import com.careermatcher.matching.engine.MatchInputs.RemotePreference;
import com.careermatcher.matching.engine.MatchInputs.RemoteType;
import com.careermatcher.matching.engine.MatchInputs.Requirement;
import com.careermatcher.matching.engine.MatchResult.ComponentScore;
import com.careermatcher.matching.engine.MatchResult.Factor;
import com.careermatcher.matching.engine.MatchResult.Status;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, explainable job matching (algorithm {@value #VERSION}). Pure function of its
 * inputs: no I/O, no randomness, no AI. Any change to the rules must bump {@link #VERSION}.
 *
 * <p>Rules (each component scores 0-1):
 *
 * <ul>
 *   <li><b>Skills:</b> 0.75 x required coverage + 0.25 x preferred coverage (required coverage
 *       alone when the job lists no preferred skills). No confirmed skills: missing data.
 *   <li><b>Experience:</b> no minimum: full credit. Otherwise merged months of dated CV experience
 *       divided by the minimum, capped at 1; 0.9 if more than 24 months above the job maximum.
 *   <li><b>Projects:</b> job skills used in CV projects / min(3, number of job skills), capped at 1.
 *   <li><b>Education:</b> no requirement: full credit. Highest completed level meets it: 1, one rank
 *       below: 0.5, else 0; an ongoing degree at the required level: 0.8. Field of study outside
 *       the job's listed fields: x 0.8.
 *   <li><b>Preferences:</b> mean of the stated preferences only (employment type, work arrangement,
 *       location, salary in the same currency, role keywords). None stated: neutral 0.5.
 * </ul>
 */
public final class MatchingEngine {

  public static final String VERSION = "match-v1";

  static final double REQUIRED_SHARE = 0.75;
  static final double PREFERRED_SHARE = 0.25;
  static final int PROJECT_FULL_CREDIT_MATCHES = 3;
  static final double OVERQUALIFIED_SCORE = 0.9;
  static final int OVERQUALIFIED_MARGIN_MONTHS = 24;
  static final double ONGOING_DEGREE_SCORE = 0.8;
  static final double FIELD_MISMATCH_FACTOR = 0.8;
  static final double NEUTRAL_PREFERENCES = 0.5;
  static final double RELOCATION_SCORE = 0.7;

  private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{Nd}+#.]+");
  private static final Set<String> STOP_WORDS =
      Set.of("and", "or", "of", "the", "a", "an", "in", "for", "with", "to", "developer", "engineer",
          "junior", "senior", "intern", "internship", "graduate", "entry", "level", "trainee", "associate");

  private final MatchingWeights weights;

  public MatchingEngine(MatchingWeights weights) {
    if (!weights.isNormalized()) {
      throw new IllegalArgumentException("Weights must sum to 1.0");
    }
    this.weights = weights;
  }

  public MatchResult score(Candidate c, Job job, YearMonth today) {
    List<String> missing = new ArrayList<>();
    List<ComponentScore> components = new ArrayList<>();
    components.add(component(Component.SKILLS, skills(c, job, missing)));
    components.add(component(Component.EXPERIENCE, experience(c, job, today, missing)));
    components.add(component(Component.PROJECTS, projects(c, job, missing)));
    components.add(component(Component.EDUCATION, education(c, job, missing)));
    components.add(component(Component.PREFERENCES, preferences(c, job, missing)));
    double total = components.stream().mapToDouble(ComponentScore::contribution).sum();
    return new MatchResult(VERSION, round(total, 1), weights.asMap(), List.copyOf(components), List.copyOf(missing));
  }

  // ------------------------------------------------------------------ components

  private record Partial(Status status, double score, List<Factor> factors) {}

  private ComponentScore component(Component comp, Partial p) {
    double w = weights.of(comp);
    double score = round(p.score(), 4);
    return new ComponentScore(comp, p.status(), score, w, round(w * score * 100, 2), List.copyOf(p.factors()));
  }

  private Partial skills(Candidate c, Job job, List<String> missing) {
    List<Factor> f = new ArrayList<>();
    List<String> reqMatched = new ArrayList<>();
    List<String> reqMissing = new ArrayList<>();
    List<String> prefMatched = new ArrayList<>();
    List<String> prefMissing = new ArrayList<>();
    job.skills().entrySet().stream()
        .sorted(Map.Entry.comparingByValue(Comparator.comparing(JobSkill::name, String.CASE_INSENSITIVE_ORDER)))
        .forEach(e -> {
          boolean has = c.skillIds().contains(e.getKey());
          boolean required = e.getValue().requirement() == Requirement.REQUIRED;
          (required ? (has ? reqMatched : reqMissing) : (has ? prefMatched : prefMissing)).add(e.getValue().name());
        });
    if (!reqMatched.isEmpty()) f.add(Factor.skills("REQUIRED_SKILLS_MATCHED", reqMatched));
    if (!reqMissing.isEmpty()) f.add(Factor.skills("REQUIRED_SKILLS_MISSING", reqMissing));
    if (!prefMatched.isEmpty()) f.add(Factor.skills("PREFERRED_SKILLS_MATCHED", prefMatched));
    if (!prefMissing.isEmpty()) f.add(Factor.skills("PREFERRED_SKILLS_MISSING", prefMissing));

    if (c.skillIds().isEmpty()) {
      missing.add("CONFIRMED_SKILLS");
      f.add(0, Factor.of("NO_CONFIRMED_SKILLS"));
      return new Partial(Status.MISSING_CANDIDATE_DATA, 0, f);
    }
    int reqTotal = reqMatched.size() + reqMissing.size();
    int prefTotal = prefMatched.size() + prefMissing.size();
    double reqCov = reqTotal == 0 ? 1 : (double) reqMatched.size() / reqTotal;
    if (prefTotal == 0) {
      return new Partial(Status.SCORED, reqCov, f);
    }
    double prefCov = (double) prefMatched.size() / prefTotal;
    return new Partial(Status.SCORED, REQUIRED_SHARE * reqCov + PREFERRED_SHARE * prefCov, f);
  }

  private Partial experience(Candidate c, Job job, YearMonth today, List<String> missing) {
    int min = job.minExperienceMonths();
    if (min <= 0) {
      return new Partial(Status.NOT_REQUIRED, 1, List.of(Factor.of("NO_EXPERIENCE_REQUIRED")));
    }
    if (c.cv() == null || !c.cv().experienceAndProjectsExtracted()) {
      missing.add("CV_EXPERIENCE");
      return new Partial(
          Status.MISSING_CANDIDATE_DATA, 0,
          List.of(Factor.values("EXPERIENCE_UNKNOWN", Map.of("requiredMonths", min))));
    }
    ExperienceMonths months = experienceMonths(c.cv().experience(), today);
    List<Factor> f = new ArrayList<>();
    if (months.undated() > 0) {
      f.add(Factor.values("UNDATED_EXPERIENCE_IGNORED", Map.of("entries", months.undated())));
    }
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("candidateMonths", months.total());
    values.put("requiredMonths", min);
    if (job.maxExperienceMonths() != null) values.put("maximumMonths", job.maxExperienceMonths());
    if (months.total() >= min) {
      if (job.maxExperienceMonths() != null
          && months.total() > job.maxExperienceMonths() + OVERQUALIFIED_MARGIN_MONTHS) {
        f.add(0, Factor.values("EXPERIENCE_ABOVE_RANGE", values));
        return new Partial(Status.SCORED, OVERQUALIFIED_SCORE, f);
      }
      f.add(0, Factor.values("EXPERIENCE_MEETS_MINIMUM", values));
      return new Partial(Status.SCORED, 1, f);
    }
    f.add(0, Factor.values("EXPERIENCE_BELOW_MINIMUM", values));
    return new Partial(Status.SCORED, (double) months.total() / min, f);
  }

  private Partial projects(Candidate c, Job job, List<String> missing) {
    if (c.cv() == null || !c.cv().experienceAndProjectsExtracted()) {
      missing.add("CV_PROJECTS");
      return new Partial(Status.MISSING_CANDIDATE_DATA, 0, List.of(Factor.of("PROJECTS_UNKNOWN")));
    }
    if (c.cv().projectSkills().isEmpty()) {
      return new Partial(Status.SCORED, 0, List.of(Factor.of("NO_PROJECTS_IN_CV")));
    }
    List<String> used =
        job.skills().entrySet().stream()
            .filter(e -> c.cv().projectSkills().stream().anyMatch(p -> p.contains(e.getKey())))
            .map(e -> e.getValue().name())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    int needed = Math.min(PROJECT_FULL_CREDIT_MATCHES, job.skills().size());
    double score = needed == 0 ? 1 : Math.min(1.0, (double) used.size() / needed);
    return new Partial(
        Status.SCORED,
        score,
        List.of(
            used.isEmpty()
                ? Factor.of("NO_RELEVANT_PROJECTS")
                : new Factor("PROJECT_SKILLS_MATCHED", used, Map.of("matched", used.size(), "neededForFullCredit", needed))));
  }

  private Partial education(Candidate c, Job job, List<String> missing) {
    if (job.educationLevel() == null) {
      return new Partial(Status.NOT_REQUIRED, 1, List.of(Factor.of("NO_EDUCATION_REQUIRED")));
    }
    if (c.cv() == null || c.cv().education().isEmpty()) {
      missing.add("CV_EDUCATION");
      return new Partial(
          Status.MISSING_CANDIDATE_DATA, 0,
          List.of(Factor.values("EDUCATION_UNKNOWN", Map.of("requiredLevel", job.educationLevel().name()))));
    }
    int required = job.educationLevel().rank;
    Education best =
        c.cv().education().stream()
            .max(Comparator.comparingInt(MatchingEngine::effectiveRank))
            .orElseThrow();
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("requiredLevel", job.educationLevel().name());
    values.put("candidateLevel", best.level().name());
    List<Factor> f = new ArrayList<>();
    double score;
    if (!best.ongoing() && best.level().rank >= required) {
      score = 1;
      f.add(Factor.values("EDUCATION_MEETS_REQUIREMENT", values));
    } else if (best.ongoing() && best.level().rank >= required) {
      score = ONGOING_DEGREE_SCORE;
      f.add(Factor.values("EDUCATION_IN_PROGRESS", values));
    } else if (best.level().rank == required - 1) {
      score = 0.5;
      f.add(Factor.values("EDUCATION_ONE_LEVEL_BELOW", values));
    } else {
      score = 0;
      f.add(Factor.values("EDUCATION_BELOW_REQUIREMENT", values));
    }
    if (score > 0 && !job.educationFields().isEmpty()) {
      if (best.fieldOfStudy() == null || best.fieldOfStudy().isBlank()) {
        f.add(Factor.of("FIELD_OF_STUDY_UNKNOWN"));
      } else if (fieldMatches(best.fieldOfStudy(), job.educationFields())) {
        f.add(Factor.values("FIELD_OF_STUDY_MATCHES", Map.of("field", best.fieldOfStudy())));
      } else {
        score *= FIELD_MISMATCH_FACTOR;
        f.add(Factor.values("FIELD_OF_STUDY_DIFFERENT", Map.of("field", best.fieldOfStudy())));
      }
    }
    return new Partial(Status.SCORED, score, f);
  }

  /** Completed degrees rank by level; ongoing ones just below a completed one of the same level. */
  private static int effectiveRank(Education e) {
    return e.level().rank * 2 - (e.ongoing() ? 1 : 0);
  }

  private Partial preferences(Candidate c, Job job, List<String> missing) {
    Preferences p = c.preferences() == null ? Preferences.NONE : c.preferences();
    List<Double> scores = new ArrayList<>();
    List<Factor> f = new ArrayList<>();

    if (!p.employmentTypes().isEmpty()) {
      boolean ok = p.employmentTypes().contains(job.employmentType());
      scores.add(ok ? 1.0 : 0.0);
      f.add(Factor.values(ok ? "EMPLOYMENT_TYPE_MATCHES" : "EMPLOYMENT_TYPE_DIFFERENT", Map.of("jobType", job.employmentType())));
    }
    if (p.remotePreference() != null) {
      double s = remoteScore(p.remotePreference(), job.remoteType());
      scores.add(s);
      f.add(Factor.values(
          s == 1 ? "WORK_ARRANGEMENT_MATCHES" : s > 0 ? "WORK_ARRANGEMENT_PARTIAL" : "WORK_ARRANGEMENT_DIFFERENT",
          Map.of("preferred", p.remotePreference().name(), "job", job.remoteType().name())));
    }
    Double location = locationScore(c, p, job);
    if (location != null) {
      scores.add(location);
      f.add(Factor.of(location == 1 ? "LOCATION_MATCHES" : location > 0 ? "LOCATION_REQUIRES_RELOCATION" : "LOCATION_DIFFERENT"));
    }
    if (p.expectedMinSalary() != null && p.salaryCurrency() != null && job.salaryCurrency() != null) {
      if (!p.salaryCurrency().equals(job.salaryCurrency())) {
        f.add(Factor.values("SALARY_NOT_COMPARABLE", Map.of("expectedCurrency", p.salaryCurrency(), "jobCurrency", job.salaryCurrency())));
      } else {
        Integer top = job.salaryMax() != null ? job.salaryMax() : job.salaryMin();
        int monthly = "YEAR".equals(job.salaryPeriod()) ? Math.round(top / 12f) : top;
        double s = monthly >= p.expectedMinSalary() ? 1 : (double) monthly / p.expectedMinSalary();
        scores.add(s);
        f.add(Factor.values(
            s >= 1 ? "SALARY_MEETS_EXPECTATION" : "SALARY_BELOW_EXPECTATION",
            Map.of("expectedMonthly", p.expectedMinSalary(), "jobMonthlyMax", monthly, "currency", p.salaryCurrency())));
      }
    }
    if (!p.desiredRoles().isEmpty()) {
      String matched = roleMatch(p.desiredRoles(), job.title());
      scores.add(matched != null ? 1.0 : 0.0);
      f.add(matched != null
          ? Factor.values("ROLE_MATCHES_INTEREST", Map.of("role", matched))
          : Factor.of("ROLE_NOT_IN_INTERESTS"));
    }
    if (scores.isEmpty()) {
      missing.add("PREFERENCES");
      return new Partial(Status.MISSING_CANDIDATE_DATA, NEUTRAL_PREFERENCES, List.of(Factor.of("NO_PREFERENCES_STATED")));
    }
    return new Partial(Status.SCORED, scores.stream().mapToDouble(Double::doubleValue).average().orElse(0), f);
  }

  // ------------------------------------------------------------------ helpers

  static double remoteScore(RemotePreference pref, RemoteType job) {
    if (pref == RemotePreference.ANY || pref.name().equals(job.name())) return 1;
    if (pref == RemotePreference.HYBRID || job == RemoteType.HYBRID) return 0.5;
    return 0; // remote vs on-site
  }

  /**
   * 1 if the job is in a preferred location or in the candidate's own country, 0.7 if not but the
   * candidate is willing to relocate, else 0. Null (not evaluated) for remote jobs, jobs without a
   * country, or when nothing is known about where the candidate wants to work.
   */
  static Double locationScore(Candidate c, Preferences p, Job job) {
    if (job.remoteType() == RemoteType.REMOTE || job.countryCode() == null) {
      return null;
    }
    if (p.preferredLocations().isEmpty() && c.countryCode() == null) {
      return null;
    }
    String city = job.city() == null ? "" : job.city().toLowerCase(Locale.ROOT).strip();
    String country = Locale.of("", job.countryCode()).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT);
    String code = job.countryCode().toLowerCase(Locale.ROOT);
    boolean preferred =
        p.preferredLocations().stream()
            .map(l -> l.toLowerCase(Locale.ROOT).strip())
            .anyMatch(l -> l.equals(code) || l.contains(country) || (!city.isEmpty() && l.contains(city)));
    if (preferred || job.countryCode().equals(c.countryCode())) {
      return 1.0;
    }
    return p.willingToRelocate() ? RELOCATION_SCORE : 0.0;
  }

  /** First desired role sharing a significant word with the job title, or null. */
  static String roleMatch(List<String> desiredRoles, String title) {
    Set<String> titleWords = significantWords(title);
    for (String role : desiredRoles) {
      if (significantWords(role).stream().anyMatch(titleWords::contains)) {
        return role;
      }
    }
    return null;
  }

  private static Set<String> significantWords(String s) {
    Set<String> out = new java.util.HashSet<>();
    for (String w : WORDS.split(s.toLowerCase(Locale.ROOT))) {
      String word = w.replaceAll("^[.]+|[.]+$", "");
      if (word.length() > 1 && !STOP_WORDS.contains(word)) {
        out.add(word);
      }
    }
    return out;
  }

  private static boolean fieldMatches(String field, List<String> jobFields) {
    String f = field.toLowerCase(Locale.ROOT);
    return jobFields.stream()
        .map(j -> j.toLowerCase(Locale.ROOT))
        .anyMatch(j -> f.contains(j) || j.contains(f));
  }

  record ExperienceMonths(int total, int undated) {}

  /** Sums months across dated entries, merging overlaps so concurrent roles aren't double-counted. */
  static ExperienceMonths experienceMonths(List<Experience> entries, YearMonth today) {
    List<YearMonth[]> intervals = new ArrayList<>();
    int undated = 0;
    for (Experience e : entries) {
      YearMonth start = parse(e.startDate(), true);
      YearMonth end = e.current() ? today : parse(e.endDate(), false);
      if (start == null || end == null || end.isBefore(start)) {
        undated++;
        continue;
      }
      if (end.isAfter(today)) end = today;
      if (start.isAfter(today)) continue;
      intervals.add(new YearMonth[] {start, end});
    }
    intervals.sort(Comparator.comparing(i -> i[0]));
    int total = 0;
    YearMonth curStart = null;
    YearMonth curEnd = null;
    for (YearMonth[] i : intervals) {
      if (curEnd == null || i[0].isAfter(curEnd.plusMonths(1))) {
        if (curEnd != null) total += months(curStart, curEnd);
        curStart = i[0];
        curEnd = i[1];
      } else if (i[1].isAfter(curEnd)) {
        curEnd = i[1];
      }
    }
    if (curEnd != null) total += months(curStart, curEnd);
    return new ExperienceMonths(total, undated);
  }

  private static int months(YearMonth start, YearMonth end) {
    return (end.getYear() - start.getYear()) * 12 + end.getMonthValue() - start.getMonthValue() + 1;
  }

  /** "YYYY-MM" exactly; "YYYY" as January (start) or December (end). */
  static YearMonth parse(String value, boolean start) {
    if (value == null || value.isBlank()) return null;
    try {
      return value.length() == 4
          ? YearMonth.of(Integer.parseInt(value), start ? 1 : 12)
          : YearMonth.parse(value);
    } catch (RuntimeException e) {
      return null;
    }
  }

  static double round(double v, int decimals) {
    double f = Math.pow(10, decimals);
    return Math.round(v * f) / f;
  }

  /** Used by callers that need to know which skills the candidate is missing. */
  public static List<String> missingRequired(MatchResult r) {
    return r.components().get(0).factors().stream()
        .filter(f -> f.code().equals("REQUIRED_SKILLS_MISSING"))
        .findFirst()
        .map(Factor::skills)
        .orElse(List.of());
  }
}
