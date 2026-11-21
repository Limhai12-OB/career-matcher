import { apiRequest } from "@/lib/auth/session";

import type {
  GapProgress,
  GapReport,
  MatchDetail,
  MatchExplanation,
  Recommendations,
  CatalogSkill,
  JobDetail,
  JobPayload,
  JobStatus,
  JobSummary,
  Page,
  AnalysisDetail,
  AnalysisSummary,
  AuthResponse,
  CandidateSkill,
  CvDocument,
  OnboardingStatus,
  Preferences,
  PreferencesUpdate,
  Profile,
  ProfileUpdate,
} from "./types";

export const profileApi = {
  get: () => apiRequest<Profile>("/api/v1/me/profile"),
  update: (body: ProfileUpdate) =>
    apiRequest<Profile>("/api/v1/me/profile", { method: "PUT", body }),
};

export const preferencesApi = {
  get: () => apiRequest<Preferences>("/api/v1/me/preferences"),
  update: (body: PreferencesUpdate) =>
    apiRequest<Preferences>("/api/v1/me/preferences", { method: "PUT", body }),
};

export const onboardingApi = {
  status: () => apiRequest<OnboardingStatus>("/api/v1/me/onboarding"),
  complete: () =>
    apiRequest<OnboardingStatus>("/api/v1/me/onboarding/complete", { method: "POST" }),
};

/**
 * These endpoints rotate or clear the HttpOnly refresh cookie, so they must be sent with
 * credentials; otherwise the browser discards the Set-Cookie response header.
 */
export const accountApi = {
  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<AuthResponse>("/api/v1/me/password", {
      method: "POST",
      body: { currentPassword, newPassword },
      withCredentials: true,
    }),
  deleteAccount: (password: string) =>
    apiRequest<void>("/api/v1/me", { method: "DELETE", body: { password }, withCredentials: true }),
};

export const cvApi = {
  upload: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiRequest<AnalysisSummary>("/api/v1/me/cvs", { method: "POST", body: form });
  },
  list: () => apiRequest<CvDocument[]>("/api/v1/me/cvs"),
  analysis: (id: string) => apiRequest<AnalysisDetail>(`/api/v1/me/cv-analyses/${encodeURIComponent(id)}`),
  reanalyze: (documentId: string) =>
    apiRequest<AnalysisSummary>(`/api/v1/me/cvs/${encodeURIComponent(documentId)}/analyses`, {
      method: "POST",
    }),
  remove: (documentId: string) =>
    apiRequest<void>(`/api/v1/me/cvs/${encodeURIComponent(documentId)}`, { method: "DELETE" }),
  download: (documentId: string) =>
    apiRequest<Blob>(`/api/v1/me/cvs/${encodeURIComponent(documentId)}/file`, { responseType: "blob" }),
  applySkills: (analysisId: string, skillIds: string[]) =>
    apiRequest<CandidateSkill[]>(`/api/v1/me/cv-analyses/${encodeURIComponent(analysisId)}/apply`, {
      method: "POST",
      body: { skillIds },
    }),
};

export const skillsApi = {
  list: () => apiRequest<CandidateSkill[]>("/api/v1/me/skills"),
  remove: (skillId: string) =>
    apiRequest<void>(`/api/v1/me/skills/${encodeURIComponent(skillId)}`, { method: "DELETE" }),
};

export const jobsApi = {
  search: (params: URLSearchParams, size = 12) => {
    const p = new URLSearchParams(params);
    p.set("size", String(size));
    return apiRequest<Page<JobSummary>>(`/api/v1/jobs?${p.toString()}`);
  },
  get: (id: string) => apiRequest<JobDetail>(`/api/v1/jobs/${encodeURIComponent(id)}`),
};

export const adminJobsApi = {
  list: (params: URLSearchParams) => apiRequest<Page<JobSummary>>(`/api/v1/admin/jobs?${params.toString()}`),
  get: (id: string) => apiRequest<JobDetail>(`/api/v1/admin/jobs/${encodeURIComponent(id)}`),
  create: (body: JobPayload) => apiRequest<JobDetail>("/api/v1/admin/jobs", { method: "POST", body }),
  update: (id: string, body: JobPayload) =>
    apiRequest<JobDetail>(`/api/v1/admin/jobs/${encodeURIComponent(id)}`, { method: "PUT", body }),
  setStatus: (id: string, status: JobStatus) =>
    apiRequest<JobDetail>(`/api/v1/admin/jobs/${encodeURIComponent(id)}/status`, {
      method: "PATCH",
      body: { status },
    }),
  remove: (id: string) => apiRequest<void>(`/api/v1/admin/jobs/${encodeURIComponent(id)}`, { method: "DELETE" }),
};

export const catalogApi = {
  all: () => apiRequest<CatalogSkill[]>("/api/v1/skills"),
};

export const matchesApi = {
  recommendations: (params: URLSearchParams) =>
    apiRequest<Recommendations>(`/api/v1/me/matches?${params.toString()}`),
  detail: (jobId: string) => apiRequest<MatchDetail>(`/api/v1/me/matches/${encodeURIComponent(jobId)}`),
  explain: (jobId: string) =>
    apiRequest<MatchExplanation>(`/api/v1/me/matches/${encodeURIComponent(jobId)}/explanation`, {
      method: "POST",
    }),
};

export const skillGapsApi = {
  progress: () => apiRequest<GapProgress>("/api/v1/me/skill-gaps"),
  report: (id: string) => apiRequest<GapReport>(`/api/v1/me/skill-gaps/${encodeURIComponent(id)}`),
  generate: () => apiRequest<GapReport>("/api/v1/me/skill-gaps", { method: "POST" }),
  roadmap: (id: string) =>
    apiRequest<GapReport>(`/api/v1/me/skill-gaps/${encodeURIComponent(id)}/roadmap`, { method: "POST" }),
};
