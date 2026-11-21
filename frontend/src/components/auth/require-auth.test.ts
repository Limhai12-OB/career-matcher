import { describe, expect, it } from "vitest";

import { homeFor, routeFor } from "./require-auth";

describe("routeFor", () => {
  it("keeps admins in the admin area, job board and settings", () => {
    expect(routeFor("ADMIN", false, "/admin/jobs", "required")).toBeNull();
    expect(routeFor("ADMIN", false, "/jobs/123", "required")).toBeNull();
    expect(routeFor("ADMIN", false, "/settings", "required")).toBeNull();
    expect(routeFor("ADMIN", false, "/dashboard", "required")).toBe("/admin/jobs");
    expect(routeFor("ADMIN", false, "/onboarding", "only")).toBe("/admin/jobs");
  });

  it("keeps candidates out of the admin area and enforces onboarding", () => {
    expect(routeFor("CANDIDATE", true, "/admin/jobs", "required")).toBe("/dashboard");
    expect(routeFor("CANDIDATE", true, "/administrator", "required")).toBeNull();
    expect(routeFor("CANDIDATE", false, "/jobs", "required")).toBe("/onboarding");
    expect(routeFor("CANDIDATE", true, "/jobs", "required")).toBeNull();
    expect(routeFor("CANDIDATE", true, "/onboarding", "only")).toBe("/dashboard");
  });

  it("picks the right home page", () => {
    expect(homeFor({ role: "ADMIN", onboardingCompleted: false })).toBe("/admin/jobs");
    expect(homeFor({ role: "CANDIDATE", onboardingCompleted: false })).toBe("/onboarding");
    expect(homeFor({ role: "CANDIDATE", onboardingCompleted: true })).toBe("/dashboard");
  });
});
