import type { CareerStage, EmploymentType, RemotePreference } from "@/lib/api/types";

export const CAREER_STAGE_LABELS: Record<CareerStage, string> = {
  STUDENT: "University student",
  RECENT_GRADUATE: "Recent graduate",
  JOB_SEEKER: "Looking for my first IT job",
  CAREER_CHANGER: "Switching into IT",
  EMPLOYED: "Employed, exploring options",
};

export const REMOTE_LABELS: Record<RemotePreference, string> = {
  ONSITE: "On-site",
  HYBRID: "Hybrid",
  REMOTE: "Remote",
  ANY: "No preference",
};

export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  INTERNSHIP: "Internship",
  CONTRACT: "Contract",
};

/** Suggestions only: candidates can type any role. */
export const ROLE_SUGGESTIONS = [
  "Junior Software Engineer",
  "Frontend Developer",
  "Backend Developer",
  "Full-Stack Developer",
  "Mobile Developer",
  "QA Engineer",
  "Data Analyst",
  "DevOps Engineer",
  "IT Support Specialist",
  "UI/UX Designer",
];

/** Human-readable names for completeness/onboarding keys returned by the API. */
export const MISSING_FIELD_LABELS: Record<string, string> = {
  fullName: "Full name",
  headline: "Headline",
  bio: "About you",
  location: "City and country",
  countryCode: "Country",
  careerStage: "Current stage",
  graduationYear: "Graduation year",
  professionalLink: "LinkedIn, GitHub or portfolio link",
  desiredRoles: "Desired roles",
  employmentTypes: "Employment types",
  remotePreference: "Work arrangement",
};

/** Call-to-action text for each missing completeness item on the dashboard. */
export const COMPLETENESS_ACTIONS: Record<string, string> = {
  fullName: "Add your full name",
  headline: "Add a headline",
  bio: "Write a short bio",
  location: "Add your city and country",
  careerStage: "Choose your current stage",
  graduationYear: "Add your graduation year",
  professionalLink: "Link LinkedIn, GitHub or a portfolio",
  desiredRoles: "Add roles you want",
  employmentTypes: "Choose employment types",
  remotePreference: "Choose a work arrangement",
};
