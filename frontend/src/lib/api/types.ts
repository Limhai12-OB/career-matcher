/** Mirrors of backend DTOs (see the OpenAPI spec at /v3/api-docs). */

export type Role = "CANDIDATE" | "ADMIN";

export interface UserSummary {
  id: string;
  email: string;
  role: Role;
  onboardingCompleted: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: UserSummary;
}

export const CAREER_STAGES = [
  "STUDENT",
  "RECENT_GRADUATE",
  "JOB_SEEKER",
  "CAREER_CHANGER",
  "EMPLOYED",
] as const;
export type CareerStage = (typeof CAREER_STAGES)[number];

export const REMOTE_PREFERENCES = ["ONSITE", "HYBRID", "REMOTE", "ANY"] as const;
export type RemotePreference = (typeof REMOTE_PREFERENCES)[number];

export const EMPLOYMENT_TYPES = ["FULL_TIME", "PART_TIME", "INTERNSHIP", "CONTRACT"] as const;
export type EmploymentType = (typeof EMPLOYMENT_TYPES)[number];

export interface Profile {
  fullName: string;
  headline: string | null;
  bio: string | null;
  city: string | null;
  countryCode: string | null;
  careerStage: CareerStage | null;
  graduationYear: number | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
  completeness: { percent: number; missing: string[] };
  updatedAt: string;
}

export type ProfileUpdate = Omit<Profile, "completeness" | "updatedAt">;

export interface Preferences {
  desiredRoles: string[];
  preferredLocations: string[];
  employmentTypes: EmploymentType[];
  remotePreference: RemotePreference | null;
  willingToRelocate: boolean;
  expectedMinSalary: number | null;
  salaryCurrency: string | null;
  availableFrom: string | null;
  updatedAt: string;
}

export type PreferencesUpdate = Omit<Preferences, "updatedAt">;

export interface OnboardingStatus {
  completed: boolean;
  completedAt: string | null;
  missingRequired: string[];
}

// ---- CV analysis ----

export type AnalysisStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export type SkillCategory =
  | "PROGRAMMING_LANGUAGE"
  | "FRAMEWORK_LIBRARY"
  | "DATABASE"
  | "CLOUD_DEVOPS"
  | "TOOL"
  | "METHODOLOGY"
  | "SOFT_SKILL"
  | "OTHER";

export type AnalysisWarning =
  | "INSTRUCTION_LIKE_TEXT_REMOVED"
  | "MODEL_FLAGGED_SUSPICIOUS_CONTENT"
  | "UNVERIFIED_ITEMS_REMOVED"
  | "LIMITED_EXTRACTION";

export interface AnalysisSummary {
  id: string;
  documentId: string;
  status: AnalysisStatus;
  errorCode: string | null;
  createdAt: string;
  completedAt: string | null;
  appliedAt: string | null;
}

export interface CvDocument {
  id: string;
  filename: string;
  type: "PDF" | "DOCX";
  sizeBytes: number;
  uploadedAt: string;
  analyses: AnalysisSummary[];
}

export interface ExtractedSkill {
  rawName: string;
  skillId: string | null;
  canonicalName: string | null;
  category: SkillCategory;
  evidence: string;
  inProfile: boolean;
}

export interface AnalysisContent {
  document: { type: string; pages: number | null; characters: number };
  education: {
    institution: string;
    degree: string | null;
    fieldOfStudy: string | null;
    level: string;
    startDate: string | null;
    endDate: string | null;
    ongoing: boolean;
    evidence: string;
  }[];
  experience: {
    title: string;
    organization: string;
    employmentType: string;
    startDate: string | null;
    endDate: string | null;
    current: boolean;
    skills: string[];
    evidence: string;
  }[];
  projects: { name: string; skills: string[]; evidence: string }[];
  certifications: { name: string; issuer: string | null; year: string | null; evidence: string }[];
  droppedItems: {
    skills: number;
    education: number;
    experience: number;
    projects: number;
    certifications: number;
  };
}

export interface AnalysisDetail {
  id: string;
  documentId: string;
  documentFilename: string;
  status: AnalysisStatus;
  errorCode: string | null;
  provider: string | null;
  model: string | null;
  createdAt: string;
  completedAt: string | null;
  appliedAt: string | null;
  warnings: AnalysisWarning[];
  skills: ExtractedSkill[];
  content: AnalysisContent | null;
}

export interface CandidateSkill {
  skillId: string;
  name: string;
  category: SkillCategory;
  source: "CV" | "MANUAL";
  analysisId: string | null;
  evidence: string | null;
  addedAt: string;
}

// ---- Jobs ----

export const JOB_REMOTE_TYPES = ["ONSITE", "HYBRID", "REMOTE"] as const;
export type JobRemoteType = (typeof JOB_REMOTE_TYPES)[number];
export const JOB_SENIORITIES = ["INTERNSHIP", "ENTRY", "JUNIOR"] as const;
export type Seniority = (typeof JOB_SENIORITIES)[number];
export const EDUCATION_LEVELS = ["HIGH_SCHOOL", "VOCATIONAL", "ASSOCIATE", "BACHELOR", "MASTER"] as const;
export type EducationLevel = (typeof EDUCATION_LEVELS)[number];
export type SalaryPeriod = "MONTH" | "YEAR";
export type DataOrigin = "REAL" | "DEMO";
export type JobStatus = "DRAFT" | "PUBLISHED" | "CLOSED";
export type SkillRequirement = "REQUIRED" | "PREFERRED";

export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface JobSummary {
  id: string;
  title: string;
  companyName: string;
  city: string | null;
  countryCode: string | null;
  remoteType: JobRemoteType;
  employmentType: EmploymentType;
  seniority: Seniority;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: SalaryPeriod | null;
  postedOn: string;
  expiresOn: string | null;
  dataOrigin: DataOrigin;
  status: JobStatus;
  requiredSkills: string[];
  preferredSkillCount: number;
}

export interface JobSkill {
  skillId: string;
  slug: string;
  name: string;
  category: SkillCategory;
  requirement: SkillRequirement;
}

export interface JobDetail extends Omit<JobSummary, "requiredSkills" | "preferredSkillCount"> {
  companyWebsite: string | null;
  description: string;
  minExperienceMonths: number;
  maxExperienceMonths: number | null;
  educationLevel: EducationLevel | null;
  educationFields: string[];
  applicationUrl: string;
  sourceName: string;
  sourceUrl: string | null;
  publishedAt: string | null;
  updatedAt: string;
  skills: JobSkill[];
}

export interface JobPayload {
  title: string;
  companyName: string;
  companyWebsite: string | null;
  description: string;
  city: string | null;
  countryCode: string | null;
  remoteType: JobRemoteType;
  employmentType: EmploymentType;
  seniority: Seniority;
  minExperienceMonths: number;
  maxExperienceMonths: number | null;
  educationLevel: EducationLevel | null;
  educationFields: string[];
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: SalaryPeriod | null;
  applicationUrl: string;
  sourceName: string;
  sourceUrl: string | null;
  dataOrigin: DataOrigin;
  postedOn: string;
  expiresOn: string | null;
  skills: { skillId: string; requirement: SkillRequirement }[];
}

export interface CatalogSkill {
  id: string;
  slug: string;
  name: string;
  category: SkillCategory;
}

// ---- Matching ----

export type MatchComponent = "SKILLS" | "EXPERIENCE" | "PROJECTS" | "EDUCATION" | "PREFERENCES";
export type ComponentStatus = "SCORED" | "MISSING_CANDIDATE_DATA" | "NOT_REQUIRED";

export interface MatchFactor {
  code: string;
  skills: string[];
  values: Record<string, string | number>;
}

export interface ComponentScore {
  component: MatchComponent;
  status: ComponentStatus;
  score: number;
  weight: number;
  contribution: number;
  factors: MatchFactor[];
}

export interface MatchResult {
  algorithmVersion: string;
  totalScore: number;
  weights: Record<MatchComponent, number>;
  components: ComponentScore[];
  missingData: string[];
}

export interface CandidateDataStatus {
  hasConfirmedSkills: boolean;
  hasCvAnalysis: boolean;
  cvExtractionLimited: boolean;
  hasPreferences: boolean;
}

export interface MatchSummary {
  job: JobSummary;
  totalScore: number;
  algorithmVersion: string;
  components: Omit<ComponentScore, "factors">[];
  missingRequiredSkills: string[];
  requiredSkillCount: number;
  matchedRequiredSkillCount: number;
}

export interface Recommendations {
  content: MatchSummary[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
  candidateData: CandidateDataStatus;
}

export interface ExplanationPoint {
  text: string;
  skills: string[];
}

export interface MatchExplanation {
  provider: string;
  model: string;
  summary: string;
  strengths: ExplanationPoint[];
  gaps: ExplanationPoint[];
  nextSteps: ExplanationPoint[];
  createdAt: string;
}

export interface MatchDetail {
  resultId: string;
  job: JobSummary;
  result: MatchResult;
  computedAt: string;
  candidateData: CandidateDataStatus;
  explanation: MatchExplanation | null;
}

// ---- Skill gaps ----

export type GapPriority = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

export interface LearningResource {
  id: string;
  title: string;
  provider: string;
  type: "DOCUMENTATION" | "TUTORIAL" | "COURSE";
  url: string;
  verifiedOn: string;
}

export interface GapJobRef {
  jobId: string;
  title: string;
  company: string;
  dataOrigin: "REAL" | "DEMO";
  score: number;
}

export interface SkillGap {
  skillId: string;
  name: string;
  category: string;
  priority: GapPriority;
  requiredIn: number;
  preferredIn: number;
  averageGain: number;
  jobs: GapJobRef[];
  closed: boolean;
  resources: LearningResource[];
}

export interface RoadmapStep {
  skill: string;
  title: string;
  why: string;
  activities: string[];
  estimatedWeeks: number;
  resources: LearningResource[];
  done: boolean;
}

export interface Roadmap {
  provider: string;
  model: string;
  summary: string;
  steps: RoadmapStep[];
  createdAt: string;
}

export interface GapReport {
  id: string;
  createdAt: string;
  algorithmVersion: string;
  matchAlgorithmVersion: string;
  targetJobCount: number;
  averageScore: number;
  requiredCoverage: number;
  targets: GapJobRef[];
  gaps: SkillGap[];
  strengths: { name: string; requiredIn: number }[];
  roadmap: Roadmap | null;
}

export interface GapReportPoint {
  id: string;
  createdAt: string;
  averageScore: number;
  requiredCoverage: number;
  criticalCount: number;
  highCount: number;
  gapCount: number;
  hasRoadmap: boolean;
}

export interface GapProgress {
  history: GapReportPoint[];
  latestReportId: string | null;
  closedSinceFirst: string[];
  openFromFirst: string[];
  confirmedSkillCount: number;
}
