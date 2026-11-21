import { describe, expect, it } from "vitest";

import type { SkillGap } from "@/lib/api/types";
import {
  delta,
  gainText,
  gapReason,
  groupByPriority,
  previousPoint,
  totalWeeks,
} from "@/lib/gaps";

const gap = (over: Partial<SkillGap>): SkillGap => ({
  skillId: "s",
  name: "Docker",
  category: "DEVOPS",
  priority: "HIGH",
  requiredIn: 0,
  preferredIn: 0,
  averageGain: 0,
  jobs: [],
  closed: false,
  resources: [],
  ...over,
});

describe("gap rendering", () => {
  it("explains why a gap matters using only its counts", () => {
    expect(gapReason(gap({ requiredIn: 3, preferredIn: 1 }), 10)).toBe(
      "Required by 3 of your top 10 matches, preferred by 1 job.",
    );
    expect(gapReason(gap({ preferredIn: 2 }), 5)).toBe(
      "Preferred by 2 of your top 5 matches.",
    );
  });

  it("shows the measured gain only when positive", () => {
    expect(gainText(5.55)).toBe(
      "+5.5 points average compatibility if confirmed",
    );
    expect(gainText(0)).toBeNull();
  });

  it("formats signed deltas", () => {
    expect(delta(52.3, 48.1)).toBe("+4.2");
    expect(delta(40, 41)).toBe("−1.0");
    expect(delta(40, 40)).toBe("±0.0");
    expect(delta(40, undefined)).toBeNull();
  });

  it("groups by priority in severity order and drops empty groups", () => {
    const groups = groupByPriority([
      gap({ priority: "LOW", name: "a" }),
      gap({ priority: "CRITICAL", name: "b" }),
    ]);
    expect(groups.map(([p, l]) => [p, l.map((g) => g.name)])).toEqual([
      ["CRITICAL", ["b"]],
      ["LOW", ["a"]],
    ]);
  });

  it("counts remaining weeks and finds the previous report", () => {
    expect(
      totalWeeks([
        { estimatedWeeks: 3, done: false },
        { estimatedWeeks: 4, done: true },
      ]),
    ).toBe(3);
    const h = [{ id: "a" }, { id: "b" }] as never;
    expect(previousPoint(h, "b")).toEqual({ id: "a" });
    expect(previousPoint(h, "a")).toBeUndefined();
  });
});
