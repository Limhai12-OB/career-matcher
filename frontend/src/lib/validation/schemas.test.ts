import { describe, expect, it } from "vitest";

import {
  onboardingPreferencesSchema,
  onboardingProfileSchema,
  preferencesSchema,
  preferencesToPayload,
  profileSchema,
  profileToPayload,
  registerSchema,
  type PreferencesValues,
  type ProfileValues,
} from "./schemas";

const profile: ProfileValues = {
  fullName: "Grace Hopper",
  headline: "",
  bio: "",
  city: "",
  countryCode: "",
  careerStage: "",
  graduationYear: "",
  linkedinUrl: "",
  githubUrl: "",
  portfolioUrl: "",
};

const prefs: PreferencesValues = {
  desiredRoles: [],
  preferredLocations: [],
  employmentTypes: [],
  remotePreference: "",
  willingToRelocate: false,
  expectedMinSalary: "",
  salaryCurrency: "",
  availableFrom: "",
};

describe("registerSchema", () => {
  const base = { fullName: "Ada", email: "ada@example.com", password: "abcdefgh12", confirmPassword: "abcdefgh12" };

  it("accepts valid input", () => {
    expect(registerSchema.safeParse(base).success).toBe(true);
  });

  it.each([
    ["short password", { password: "abc1", confirmPassword: "abc1" }, "password"],
    ["no digit", { password: "abcdefghijk", confirmPassword: "abcdefghijk" }, "password"],
    ["mismatch", { confirmPassword: "different12" }, "confirmPassword"],
    ["bad email", { email: "nope" }, "email"],
    ["short name", { fullName: "A" }, "fullName"],
  ])("rejects %s", (_, patch, field) => {
    const r = registerSchema.safeParse({ ...base, ...patch });
    expect(r.success).toBe(false);
    expect(r.error?.issues.map((i) => i.path[0])).toContain(field);
  });
});

describe("profileSchema", () => {
  it("allows an otherwise empty profile in edit mode but not onboarding", () => {
    expect(profileSchema.safeParse(profile).success).toBe(true);
    const r = onboardingProfileSchema.safeParse(profile);
    expect(r.error?.issues.map((i) => i.path[0])).toEqual(
      expect.arrayContaining(["countryCode", "careerStage"]),
    );
  });

  it.each([
    ["linkedinUrl", "https://evil.example/in/x"],
    ["linkedinUrl", "https://linkedin.com.evil.example/in/x"],
    ["githubUrl", "http://github.com/x"],
    ["portfolioUrl", "javascript:alert(1)"],
    ["graduationYear", "1800"],
    ["fullName", "Bad\u0000Name"],
  ])("rejects invalid %s %s", (field, value) => {
    const r = profileSchema.safeParse({ ...profile, [field]: value });
    expect(r.error?.issues.map((i) => i.path[0])).toContain(field);
  });

  it("converts blanks to null in the payload", () => {
    expect(profileToPayload({ ...profile, graduationYear: "2026", city: "  " })).toMatchObject({
      graduationYear: 2026,
      city: null,
      careerStage: null,
    });
  });
});

describe("preferencesSchema", () => {
  it("requires salary and currency together", () => {
    const r = preferencesSchema.safeParse({ ...prefs, expectedMinSalary: "500" });
    expect(r.error?.issues[0].path).toEqual(["salaryCurrency"]);
    expect(preferencesSchema.safeParse({ ...prefs, expectedMinSalary: "500", salaryCurrency: "USD" }).success).toBe(true);
  });

  it("enforces onboarding requirements", () => {
    const r = onboardingPreferencesSchema.safeParse(prefs);
    expect(r.error?.issues.map((i) => i.path[0])).toEqual(
      expect.arrayContaining(["desiredRoles", "employmentTypes", "remotePreference"]),
    );
  });

  it("converts to payload", () => {
    expect(
      preferencesToPayload({ ...prefs, expectedMinSalary: "800", salaryCurrency: "USD", remotePreference: "REMOTE" }),
    ).toMatchObject({ expectedMinSalary: 800, salaryCurrency: "USD", remotePreference: "REMOTE", availableFrom: null });
  });
});
