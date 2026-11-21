import { describe, expect, it } from "vitest";

import {
  EMPTY_FILTERS,
  formatExperience,
  formatLocation,
  formatSalary,
  isSafeExternalUrl,
  parseFilters,
  postedAgo,
  toSearchParams,
} from "./jobs";

describe("job filters", () => {
  it("round-trips valid filters through the URL", () => {
    const f = { ...EMPTY_FILTERS, q: "java", country: "KH", remote: "REMOTE" as const, skill: "spring-boot", origin: "REAL" as const, page: 2 };
    expect(parseFilters(toSearchParams(f))).toEqual(f);
  });

  it("drops invalid or hostile values", () => {
    const parsed = parseFilters(
      new URLSearchParams("country=khm&remote=SPACE&type=x&seniority=BOSS&skill=DROP TABLE&origin=FAKE&page=-3&q=" + "a".repeat(200)),
    );
    expect(parsed).toEqual({ ...EMPTY_FILTERS, q: "a".repeat(100) });
  });

  it("omits empty values from the query string", () => {
    expect(toSearchParams(EMPTY_FILTERS).toString()).toBe("");
    expect(toSearchParams({ ...EMPTY_FILTERS, q: "  react  " }).toString()).toBe("q=react");
  });
});

describe("formatting", () => {
  it("formats salary ranges and open-ended salaries", () => {
    const base = { salaryCurrency: "USD", salaryPeriod: "MONTH" as const };
    expect(formatSalary({ ...base, salaryMin: 800, salaryMax: 1200 })).toBe("$800 – $1,200 / month");
    expect(formatSalary({ ...base, salaryMin: 800, salaryMax: null })).toBe("From $800 / month");
    expect(formatSalary({ ...base, salaryMin: null, salaryMax: 900, salaryPeriod: "YEAR" })).toBe("Up to $900 / year");
    expect(formatSalary({ salaryMin: null, salaryMax: null, salaryCurrency: null, salaryPeriod: null })).toBeNull();
  });

  it("formats location including remote jobs", () => {
    expect(formatLocation({ city: "Phnom Penh", countryCode: "KH", remoteType: "HYBRID" })).toBe("Phnom Penh, Cambodia");
    expect(formatLocation({ city: null, countryCode: "SG", remoteType: "REMOTE" })).toBe("Remote (Singapore)");
    expect(formatLocation({ city: null, countryCode: null, remoteType: "REMOTE" })).toBe("Remote");
  });

  it("formats experience requirements", () => {
    expect(formatExperience(0, null)).toBe("No experience required");
    expect(formatExperience(0, 12)).toBe("Up to 1 year");
    expect(formatExperience(6, 24)).toBe("6 months – 2 years");
    expect(formatExperience(12, null)).toBe("1 year+");
  });

  it("describes posting age", () => {
    const now = new Date("2026-11-21T12:00:00Z");
    expect(postedAgo("2026-11-21", now)).toBe("Posted today");
    expect(postedAgo("2026-11-20", now)).toBe("Posted yesterday");
    expect(postedAgo("2026-11-11", now)).toBe("Posted 10 days ago");
  });

  it("only treats https links as safe to open", () => {
    expect(isSafeExternalUrl("https://jobs.example.org/1")).toBe(true);
    expect(isSafeExternalUrl("http://jobs.acme.io/1")).toBe(false);
    expect(isSafeExternalUrl("javascript:alert(1)")).toBe(false);
    expect(isSafeExternalUrl(null)).toBe(false);
  });
});
