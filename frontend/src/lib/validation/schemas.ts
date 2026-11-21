/**
 * Client-side validation mirroring the backend rules (the backend remains authoritative).
 * Form values are kept as strings; conversion to API payloads happens in the *ToPayload helpers.
 */
import { z } from "zod";

import {
  CAREER_STAGES,
  EMPLOYMENT_TYPES,
  REMOTE_PREFERENCES,
  type PreferencesUpdate,
  type ProfileUpdate,
} from "@/lib/api/types";

const CONTROL_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/;
const SINGLE_LINE_CONTROL = /[\u0000-\u001F\u007F]/;

const singleLine = (max: number, label: string) =>
  z
    .string()
    .trim()
    .max(max, `${label} must be at most ${max} characters`)
    .refine((v) => !SINGLE_LINE_CONTROL.test(v), "Contains invalid characters");

const httpsUrl = (hosts: string[] = []) =>
  z
    .string()
    .trim()
    .max(300, "URL is too long")
    .refine((v) => {
      if (!v) return true;
      try {
        const url = new URL(v);
        if (url.protocol !== "https:" || url.username || url.password) return false;
        const host = url.hostname.toLowerCase();
        return hosts.length === 0 || hosts.some((h) => host === h || host.endsWith(`.${h}`));
      } catch {
        return false;
      }
    }, hosts.length ? `Must be an https:// URL on ${hosts.join(" or ")}` : "Must be a valid https:// URL");

export const passwordSchema = z
  .string()
  .min(10, "Use at least 10 characters")
  .max(128, "Use at most 128 characters")
  .regex(/\p{L}/u, "Include at least one letter")
  .regex(/\p{Nd}/u, "Include at least one number");

export const loginSchema = z.object({
  email: z.string().trim().min(1, "Email is required").max(254),
  password: z.string().min(1, "Password is required").max(128),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    fullName: singleLine(120, "Full name").pipe(
      z.string().min(2, "Full name must be at least 2 characters"),
    ),
    email: z.string().trim().max(254, "Email is too long").pipe(z.email("Enter a valid email address")),
    password: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  });
export type RegisterValues = z.infer<typeof registerSchema>;

const currentYear = new Date().getFullYear();

export const profileSchema = z.object({
  fullName: singleLine(120, "Full name").pipe(
    z.string().min(2, "Full name must be at least 2 characters"),
  ),
  headline: singleLine(160, "Headline"),
  bio: z
    .string()
    .max(2000, "Bio must be at most 2000 characters")
    .refine((v) => !CONTROL_CHARS.test(v), "Contains invalid characters"),
  city: singleLine(100, "City"),
  countryCode: z.string().regex(/^([A-Z]{2})?$/, "Select a country"),
  careerStage: z.union([z.enum(CAREER_STAGES), z.literal("")]),
  graduationYear: z
    .string()
    .trim()
    .refine(
      (v) => v === "" || (/^\d{4}$/.test(v) && +v >= 1950 && +v <= currentYear + 8),
      `Enter a year between 1950 and ${currentYear + 8}`,
    ),
  linkedinUrl: httpsUrl(["linkedin.com"]),
  githubUrl: httpsUrl(["github.com"]),
  portfolioUrl: httpsUrl(),
});
export type ProfileValues = z.infer<typeof profileSchema>;

export const preferencesSchema = z
  .object({
    desiredRoles: z.array(singleLine(80, "Role").pipe(z.string().min(1))).max(10, "At most 10 roles"),
    preferredLocations: z
      .array(singleLine(100, "Location").pipe(z.string().min(1)))
      .max(10, "At most 10 locations"),
    employmentTypes: z.array(z.enum(EMPLOYMENT_TYPES)).max(4),
    remotePreference: z.union([z.enum(REMOTE_PREFERENCES), z.literal("")]),
    willingToRelocate: z.boolean(),
    expectedMinSalary: z
      .string()
      .trim()
      .refine((v) => v === "" || (/^\d+$/.test(v) && +v <= 100_000_000), "Enter a whole number"),
    salaryCurrency: z.string().regex(/^([A-Z]{3})?$/, "Select a currency"),
    availableFrom: z.string(),
  })
  .refine((v) => (v.expectedMinSalary === "") === (v.salaryCurrency === ""), {
    path: ["salaryCurrency"],
    message: "Provide both a minimum salary and a currency, or neither",
  });
export type PreferencesValues = z.infer<typeof preferencesSchema>;

/** Onboarding requires these to be filled in (mirrors backend ONBOARDING_INCOMPLETE rules). */
export const onboardingProfileSchema = profileSchema.extend({
  countryCode: z.string().regex(/^[A-Z]{2}$/, "Select your country"),
  careerStage: z.enum(CAREER_STAGES, { error: "Select your current stage" }),
});

export const onboardingPreferencesSchema = preferencesSchema.safeExtend({
  desiredRoles: preferencesSchema.shape.desiredRoles.min(1, "Add at least one role"),
  employmentTypes: preferencesSchema.shape.employmentTypes.min(1, "Select at least one type"),
  remotePreference: z.enum(REMOTE_PREFERENCES, { error: "Select a work arrangement" }),
});

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required"),
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  });
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;

// ---- conversions ----

const orNull = (v: string) => (v.trim() === "" ? null : v.trim());

export function profileToValues(p: {
  fullName: string;
  headline: string | null;
  bio: string | null;
  city: string | null;
  countryCode: string | null;
  careerStage: ProfileValues["careerStage"] | null;
  graduationYear: number | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
}): ProfileValues {
  return {
    fullName: p.fullName ?? "",
    headline: p.headline ?? "",
    bio: p.bio ?? "",
    city: p.city ?? "",
    countryCode: p.countryCode ?? "",
    careerStage: p.careerStage ?? "",
    graduationYear: p.graduationYear?.toString() ?? "",
    linkedinUrl: p.linkedinUrl ?? "",
    githubUrl: p.githubUrl ?? "",
    portfolioUrl: p.portfolioUrl ?? "",
  };
}

export function profileToPayload(v: ProfileValues): ProfileUpdate {
  return {
    fullName: v.fullName.trim(),
    headline: orNull(v.headline),
    bio: orNull(v.bio),
    city: orNull(v.city),
    countryCode: orNull(v.countryCode),
    careerStage: v.careerStage === "" ? null : v.careerStage,
    graduationYear: v.graduationYear === "" ? null : Number(v.graduationYear),
    linkedinUrl: orNull(v.linkedinUrl),
    githubUrl: orNull(v.githubUrl),
    portfolioUrl: orNull(v.portfolioUrl),
  };
}

export function preferencesToValues(p: Omit<PreferencesUpdate, never>): PreferencesValues {
  return {
    desiredRoles: p.desiredRoles,
    preferredLocations: p.preferredLocations,
    employmentTypes: p.employmentTypes,
    remotePreference: p.remotePreference ?? "",
    willingToRelocate: p.willingToRelocate,
    expectedMinSalary: p.expectedMinSalary?.toString() ?? "",
    salaryCurrency: p.salaryCurrency ?? "",
    availableFrom: p.availableFrom ?? "",
  };
}

export function preferencesToPayload(v: PreferencesValues): PreferencesUpdate {
  return {
    desiredRoles: v.desiredRoles,
    preferredLocations: v.preferredLocations,
    employmentTypes: v.employmentTypes,
    remotePreference: v.remotePreference === "" ? null : v.remotePreference,
    willingToRelocate: v.willingToRelocate,
    expectedMinSalary: v.expectedMinSalary === "" ? null : Number(v.expectedMinSalary),
    salaryCurrency: orNull(v.salaryCurrency),
    availableFrom: orNull(v.availableFrom),
  };
}
