import { describe, expect, it } from "vitest";

import { describeFactor, scoreBand } from "./matching";

describe("describeFactor", () => {
  it.each([
    [{ code: "REQUIRED_SKILLS_MISSING", skills: ["Spring Boot"], values: {} }, "Missing required: Spring Boot", "negative"],
    [{ code: "REQUIRED_SKILLS_MATCHED", skills: ["Java", "SQL"], values: {} }, "You have required skills: Java, SQL", "positive"],
    [
      { code: "EXPERIENCE_BELOW_MINIMUM", skills: [], values: { candidateMonths: 3, requiredMonths: 6 } },
      "3 of the 6 months of experience required",
      "negative",
    ],
    [
      { code: "EDUCATION_IN_PROGRESS", skills: [], values: { candidateLevel: "BACHELOR", requiredLevel: "BACHELOR" } },
      "Bachelor's degree in progress (Bachelor's degree required)",
      "neutral",
    ],
    [
      { code: "WORK_ARRANGEMENT_PARTIAL", skills: [], values: { job: "HYBRID", preferred: "REMOTE" } },
      "It's hybrid; you prefer remote",
      "neutral",
    ],
    [{ code: "SOMETHING_NEW", skills: [], values: {} }, "something new", "neutral"],
  ] as const)("%o", (factor, text, tone) => {
    expect(describeFactor({ ...factor, skills: [...factor.skills] })).toEqual({ text, tone });
  });
});

describe("scoreBand", () => {
  it.each([
    [100, "strong"], [75, "strong"], [74.9, "good"], [50, "good"], [49.9, "partial"], [30, "partial"], [29.9, "low"], [0, "low"],
  ] as const)("%d -> %s", (score, band) => {
    expect(scoreBand(score).band).toBe(band);
  });
});
