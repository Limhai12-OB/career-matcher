/** Deterministic, plain-language rendering of match factors (no AI involved). */
import type { ComponentStatus, MatchComponent, MatchFactor } from "@/lib/api/types";

export const COMPONENT_LABELS: Record<MatchComponent, string> = {
  SKILLS: "Skills",
  EXPERIENCE: "Experience",
  PROJECTS: "Projects",
  EDUCATION: "Education",
  PREFERENCES: "Your preferences",
};

export const STATUS_LABELS: Record<ComponentStatus, string> = {
  SCORED: "Scored",
  MISSING_CANDIDATE_DATA: "Missing information",
  NOT_REQUIRED: "Not required",
};

const LEVELS: Record<string, string> = {
  HIGH_SCHOOL: "High school",
  VOCATIONAL: "Vocational diploma",
  BOOTCAMP: "Bootcamp",
  ASSOCIATE: "Associate degree",
  BACHELOR: "Bachelor's degree",
  MASTER: "Master's degree",
  DOCTORATE: "Doctorate",
  OTHER: "Other education",
};

const ARRANGEMENT: Record<string, string> = {
  ONSITE: "on-site",
  HYBRID: "hybrid",
  REMOTE: "remote",
  ANY: "any arrangement",
};

const TYPES: Record<string, string> = {
  FULL_TIME: "full-time",
  PART_TIME: "part-time",
  INTERNSHIP: "internship",
  CONTRACT: "contract",
};

const list = (skills: string[]) => skills.join(", ");
const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

export type FactorTone = "positive" | "negative" | "neutral";

/** A sentence and tone for one factor. Unknown codes fall back to a neutral generic message. */
export function describeFactor(f: MatchFactor): { text: string; tone: FactorTone } {
  const v = f.values;
  const level = (k: string) => LEVELS[String(v[k])] ?? String(v[k]);
  switch (f.code) {
    case "REQUIRED_SKILLS_MATCHED":
      return { text: `You have required ${f.skills.length === 1 ? "skill" : "skills"}: ${list(f.skills)}`, tone: "positive" };
    case "REQUIRED_SKILLS_MISSING":
      return { text: `Missing required: ${list(f.skills)}`, tone: "negative" };
    case "PREFERRED_SKILLS_MATCHED":
      return { text: `You have preferred: ${list(f.skills)}`, tone: "positive" };
    case "PREFERRED_SKILLS_MISSING":
      return { text: `Preferred, not yet in your profile: ${list(f.skills)}`, tone: "neutral" };
    case "NO_CONFIRMED_SKILLS":
      return { text: "No confirmed skills yet. Analyze your CV and confirm your skills.", tone: "negative" };
    case "NO_EXPERIENCE_REQUIRED":
      return { text: "No prior experience required", tone: "positive" };
    case "EXPERIENCE_UNKNOWN":
      return { text: `Your experience isn't known yet (this role asks for ${v.requiredMonths} months)`, tone: "negative" };
    case "EXPERIENCE_MEETS_MINIMUM":
      return { text: `${v.candidateMonths} months of experience meets the ${v.requiredMonths}-month minimum`, tone: "positive" };
    case "EXPERIENCE_BELOW_MINIMUM":
      return { text: `${v.candidateMonths} of the ${v.requiredMonths} months of experience required`, tone: "negative" };
    case "EXPERIENCE_ABOVE_RANGE":
      return { text: `${v.candidateMonths} months is well above this role's range (up to ${v.maximumMonths})`, tone: "neutral" };
    case "UNDATED_EXPERIENCE_IGNORED":
      return { text: `${plural(Number(v.entries), "role")} without dates in your CV couldn't be counted`, tone: "neutral" };
    case "PROJECTS_UNKNOWN":
      return { text: "Projects aren't available from your CV analysis", tone: "negative" };
    case "NO_PROJECTS_IN_CV":
      return { text: "No projects found in your CV", tone: "negative" };
    case "NO_RELEVANT_PROJECTS":
      return { text: "Your projects don't use this role's skills", tone: "negative" };
    case "PROJECT_SKILLS_MATCHED":
      return { text: `Your projects use: ${list(f.skills)}`, tone: "positive" };
    case "NO_EDUCATION_REQUIRED":
      return { text: "No education requirement", tone: "positive" };
    case "EDUCATION_UNKNOWN":
      return { text: `No education found in your CV (${level("requiredLevel")} required)`, tone: "negative" };
    case "EDUCATION_MEETS_REQUIREMENT":
      return { text: `${level("candidateLevel")} meets the requirement`, tone: "positive" };
    case "EDUCATION_IN_PROGRESS":
      return { text: `${level("candidateLevel")} in progress (${level("requiredLevel")} required)`, tone: "neutral" };
    case "EDUCATION_ONE_LEVEL_BELOW":
    case "EDUCATION_BELOW_REQUIREMENT":
      return { text: `${level("candidateLevel")}; this role asks for a ${level("requiredLevel")}`, tone: "negative" };
    case "FIELD_OF_STUDY_MATCHES":
      return { text: `Your field (${v.field}) matches`, tone: "positive" };
    case "FIELD_OF_STUDY_DIFFERENT":
      return { text: `Your field (${v.field}) differs from the fields this role lists`, tone: "neutral" };
    case "FIELD_OF_STUDY_UNKNOWN":
      return { text: "Your field of study isn't known", tone: "neutral" };
    case "EMPLOYMENT_TYPE_MATCHES":
      return { text: `It's a ${TYPES[String(v.jobType)] ?? v.jobType} role, as you prefer`, tone: "positive" };
    case "EMPLOYMENT_TYPE_DIFFERENT":
      return { text: `It's a ${TYPES[String(v.jobType)] ?? v.jobType} role, which isn't one of your preferred types`, tone: "negative" };
    case "WORK_ARRANGEMENT_MATCHES":
      return { text: `The ${ARRANGEMENT[String(v.job)]} arrangement suits you`, tone: "positive" };
    case "WORK_ARRANGEMENT_PARTIAL":
      return { text: `It's ${ARRANGEMENT[String(v.job)]}; you prefer ${ARRANGEMENT[String(v.preferred)]}`, tone: "neutral" };
    case "WORK_ARRANGEMENT_DIFFERENT":
      return { text: `It's ${ARRANGEMENT[String(v.job)]}; you prefer ${ARRANGEMENT[String(v.preferred)]}`, tone: "negative" };
    case "LOCATION_MATCHES":
      return { text: "The location suits you", tone: "positive" };
    case "LOCATION_REQUIRES_RELOCATION":
      return { text: "You'd need to relocate (you said you're open to it)", tone: "neutral" };
    case "LOCATION_DIFFERENT":
      return { text: "The location isn't one you prefer", tone: "negative" };
    case "SALARY_MEETS_EXPECTATION":
      return { text: "The salary meets your expectation", tone: "positive" };
    case "SALARY_BELOW_EXPECTATION":
      return { text: `The salary (up to ${v.jobMonthlyMax} ${v.currency}/month) is below your ${v.expectedMonthly}`, tone: "negative" };
    case "SALARY_NOT_COMPARABLE":
      return { text: "The salary is in a different currency, so it wasn't compared", tone: "neutral" };
    case "ROLE_MATCHES_INTEREST":
      return { text: `Matches your interest in "${v.role}"`, tone: "positive" };
    case "ROLE_NOT_IN_INTERESTS":
      return { text: "Not one of the roles you listed", tone: "neutral" };
    case "NO_PREFERENCES_STATED":
      return { text: "You haven't set career preferences, so a neutral value was used", tone: "neutral" };
    default:
      return { text: f.code.toLowerCase().replaceAll("_", " "), tone: "neutral" };
  }
}

export type ScoreBand = "strong" | "good" | "partial" | "low";

export function scoreBand(score: number): { band: ScoreBand; label: string } {
  if (score >= 75) return { band: "strong", label: "Strong match" };
  if (score >= 50) return { band: "good", label: "Good match" };
  if (score >= 30) return { band: "partial", label: "Partial match" };
  return { band: "low", label: "Low match" };
}

export const MISSING_DATA_HINTS: Record<string, { text: string; href: string }> = {
  CONFIRMED_SKILLS: { text: "Confirm your skills from a CV analysis", href: "/cv" },
  CV_EXPERIENCE: { text: "Analyze your CV so your experience can be counted", href: "/cv" },
  CV_PROJECTS: { text: "Analyze your CV so your projects can be counted", href: "/cv" },
  CV_EDUCATION: { text: "Analyze a CV that lists your education", href: "/cv" },
  PREFERENCES: { text: "Set your career preferences", href: "/preferences" },
};
