import { describe, expect, it } from "vitest";

import { emptyJobForm, formToPayload, jobFormSchema, urlProblem, type JobFormValues } from "./job-schema";

const valid = (): JobFormValues => ({
  ...emptyJobForm(),
  title: "Junior Java Developer",
  companyName: "Acme",
  description: "Build and maintain Java services for our customers. ".repeat(2),
  applicationUrl: "https://jobs.acme.io/apply/1",
  sourceName: "Acme careers",
  dataOrigin: "REAL",
  skills: [{ skillId: "s1", requirement: "REQUIRED" }],
});

const fields = (v: JobFormValues) => jobFormSchema.safeParse(v).error?.issues.map((i) => i.path[0]) ?? [];

describe("urlProblem", () => {
  it.each([
    ["https://jobs.acme.io/1", "REAL", null],
    ["https://careers.example.com/demo/1", "DEMO", null],
    ["https://jobs.acme.io/1", "DEMO", "Demo listings must link to example.com/.org/.net"],
    ["https://careers.example.com/x", "REAL", "Real listings must link to the actual employer or job board page"],
    ["https://example.com.evil.io/x", "DEMO", "Demo listings must link to example.com/.org/.net"],
    ["http://jobs.acme.io/1", "REAL", "Must be a valid https:// URL"],
    ["https://user:pw@jobs.acme.io", "REAL", "Must be a valid https:// URL"],
  ] as const)("%s (%s)", (url, origin, expected) => {
    expect(urlProblem(url, origin, true)).toBe(expected);
  });
});

describe("jobFormSchema", () => {
  it("accepts a valid real listing", () => {
    expect(fields(valid())).toEqual([]);
  });

  it("requires an explicit real/demo choice", () => {
    expect(fields({ ...valid(), dataOrigin: "" })).toContain("dataOrigin");
  });

  it("requires a required skill", () => {
    expect(fields({ ...valid(), skills: [{ skillId: "s1", requirement: "PREFERRED" }] })).toContain("skills");
    expect(fields({ ...valid(), skills: [] })).toContain("skills");
  });

  it("checks salary and experience consistency", () => {
    expect(fields({ ...valid(), salaryMin: "100" })).toContain("salaryCurrency");
    expect(fields({ ...valid(), salaryMin: "100", salaryMax: "50", salaryCurrency: "USD", salaryPeriod: "MONTH" })).toContain("salaryMax");
    expect(fields({ ...valid(), minExperienceMonths: "12", maxExperienceMonths: "6" })).toContain("maxExperienceMonths");
  });

  it("converts to the API payload", () => {
    const p = formToPayload({ ...valid(), salaryMin: "800", salaryCurrency: "USD", salaryPeriod: "MONTH", city: " " });
    expect(p).toMatchObject({ salaryMin: 800, salaryMax: null, city: null, minExperienceMonths: 0, dataOrigin: "REAL" });
  });
});
