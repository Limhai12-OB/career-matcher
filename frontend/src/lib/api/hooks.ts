"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  adminJobsApi,
  catalogApi,
  cvApi,
  jobsApi,
  matchesApi,
  onboardingApi,
  preferencesApi,
  profileApi,
  skillsApi,
} from "./endpoints";
import type { PreferencesUpdate, ProfileUpdate } from "./types";

export const queryKeys = {
  profile: ["me", "profile"] as const,
  preferences: ["me", "preferences"] as const,
  onboarding: ["me", "onboarding"] as const,
};

export function useProfile(options: { enabled?: boolean } = {}) {
  return useQuery({ queryKey: queryKeys.profile, queryFn: profileApi.get, enabled: options.enabled ?? true });
}

export function usePreferences() {
  return useQuery({ queryKey: queryKeys.preferences, queryFn: preferencesApi.get });
}

export function useOnboardingStatus() {
  return useQuery({ queryKey: queryKeys.onboarding, queryFn: onboardingApi.status });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProfileUpdate) => profileApi.update(body),
    onSuccess: (profile) => {
      qc.setQueryData(queryKeys.profile, profile);
      void qc.invalidateQueries({ queryKey: queryKeys.onboarding });
    },
  });
}

export function useUpdatePreferences() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: PreferencesUpdate) => preferencesApi.update(body),
    onSuccess: (prefs) => {
      qc.setQueryData(queryKeys.preferences, prefs);
      void qc.invalidateQueries({ queryKey: ["me", "matches"] });
      // Completeness on the profile depends on preferences.
      void qc.invalidateQueries({ queryKey: queryKeys.profile });
      void qc.invalidateQueries({ queryKey: queryKeys.onboarding });
    },
  });
}

// ---- CV & skills ----

export const cvKeys = {
  documents: ["me", "cvs"] as const,
  analysis: (id: string) => ["me", "cv-analyses", id] as const,
  skills: ["me", "skills"] as const,
};

const ACTIVE: ReadonlySet<string> = new Set(["PENDING", "PROCESSING"]);

export function useCvDocuments() {
  return useQuery({
    queryKey: cvKeys.documents,
    queryFn: cvApi.list,
    // Keep polling while any analysis is still running.
    refetchInterval: (q) =>
      q.state.data?.some((d) => d.analyses.some((a) => ACTIVE.has(a.status))) ? 2000 : false,
  });
}

export function useAnalysis(id: string) {
  return useQuery({
    queryKey: cvKeys.analysis(id),
    queryFn: () => cvApi.analysis(id),
    refetchInterval: (q) => (q.state.data && !ACTIVE.has(q.state.data.status) ? false : 1500),
  });
}

export function useCandidateSkills() {
  return useQuery({ queryKey: cvKeys.skills, queryFn: skillsApi.list });
}

export function useUploadCv() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => cvApi.upload(file),
    onSuccess: () => void qc.invalidateQueries({ queryKey: cvKeys.documents }),
  });
}

export function useReanalyze() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (documentId: string) => cvApi.reanalyze(documentId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: cvKeys.documents }),
  });
}

export function useDeleteCv() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (documentId: string) => cvApi.remove(documentId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: cvKeys.documents });
      void qc.invalidateQueries({ queryKey: cvKeys.skills });
    },
  });
}

export function useApplySkills(analysisId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (skillIds: string[]) => cvApi.applySkills(analysisId, skillIds),
    onSuccess: (skills) => {
      qc.setQueryData(cvKeys.skills, skills);
      void qc.invalidateQueries({ queryKey: ["me", "matches"] });
      void qc.invalidateQueries({ queryKey: cvKeys.analysis(analysisId) });
      void qc.invalidateQueries({ queryKey: cvKeys.documents });
    },
  });
}

export function useRemoveSkill() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (skillId: string) => skillsApi.remove(skillId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: cvKeys.skills });
      void qc.invalidateQueries({ queryKey: ["me", "matches"] });
    },
  });
}

// ---- Jobs ----

export const jobKeys = {
  search: (params: string) => ["jobs", "search", params] as const,
  detail: (id: string) => ["jobs", "detail", id] as const,
  admin: (params: string) => ["admin", "jobs", params] as const,
  adminDetail: (id: string) => ["admin", "jobs", "detail", id] as const,
  catalog: ["skills", "catalog"] as const,
};

export function useJobSearch(params: URLSearchParams) {
  const key = params.toString();
  return useQuery({
    queryKey: jobKeys.search(key),
    queryFn: () => jobsApi.search(params),
    placeholderData: keepPreviousData,
  });
}

export function useJob(id: string) {
  return useQuery({ queryKey: jobKeys.detail(id), queryFn: () => jobsApi.get(id) });
}

export function useSkillCatalog() {
  return useQuery({ queryKey: jobKeys.catalog, queryFn: catalogApi.all, staleTime: 60 * 60_000 });
}

export function useAdminJobs(params: URLSearchParams) {
  const key = params.toString();
  return useQuery({
    queryKey: jobKeys.admin(key),
    queryFn: () => adminJobsApi.list(params),
    placeholderData: keepPreviousData,
  });
}

export function useAdminJob(id: string | null) {
  return useQuery({
    queryKey: jobKeys.adminDetail(id ?? "new"),
    queryFn: () => adminJobsApi.get(id!),
    enabled: !!id,
  });
}

/** Invalidate everything job-related after admin changes. */
export function useInvalidateJobs() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: ["admin", "jobs"] });
    void qc.invalidateQueries({ queryKey: ["jobs"] });
  };
}

// ---- Matching ----

export const matchKeys = {
  all: ["me", "matches"] as const,
  list: (params: string) => ["me", "matches", "list", params] as const,
  detail: (jobId: string) => ["me", "matches", "detail", jobId] as const,
};

export function useRecommendations(params: URLSearchParams, options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: matchKeys.list(params.toString()),
    queryFn: () => matchesApi.recommendations(params),
    placeholderData: keepPreviousData,
    enabled: options.enabled ?? true,
  });
}

export function useMatch(jobId: string, options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: matchKeys.detail(jobId),
    queryFn: () => matchesApi.detail(jobId),
    enabled: options.enabled ?? true,
  });
}

export function useExplainMatch(jobId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => matchesApi.explain(jobId),
    onSuccess: (explanation) =>
      qc.setQueryData(matchKeys.detail(jobId), (old: import("./types").MatchDetail | undefined) =>
        old ? { ...old, explanation } : old,
      ),
  });
}
