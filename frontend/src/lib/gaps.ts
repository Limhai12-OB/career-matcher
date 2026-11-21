/** Plain-language rendering of skill gap results (deterministic; no AI). */
import type { GapPriority, GapReportPoint, SkillGap } from "@/lib/api/types";

export const PRIORITY_ORDER: GapPriority[] = [
  "CRITICAL",
  "HIGH",
  "MEDIUM",
  "LOW",
];

export const PRIORITY_META: Record<
  GapPriority,
  { label: string; description: string; className: string }
> = {
  CRITICAL: {
    label: "Critical",
    description: "Required by many of your best-matching jobs",
    className:
      "border-red-200 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200",
  },
  HIGH: {
    label: "High",
    description: "Required by at least one of your best-matching jobs",
    className:
      "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200",
  },
  MEDIUM: {
    label: "Medium",
    description: "Preferred by several of your best-matching jobs",
    className:
      "border-sky-200 bg-sky-50 text-sky-800 dark:border-sky-900 dark:bg-sky-950 dark:text-sky-200",
  },
  LOW: {
    label: "Low",
    description: "Preferred by one of your best-matching jobs",
    className: "border-border bg-muted text-muted-foreground",
  },
};

function jobs(n: number): string {
  return n === 1 ? "1 job" : `${n} jobs`;
}

/** Why a gap matters, from its measured counts and score gain only. */
export function gapReason(gap: SkillGap, targetCount: number): string {
  const parts: string[] = [];
  if (gap.requiredIn > 0)
    parts.push(
      `Required by ${gap.requiredIn} of your top ${targetCount} matches`,
    );
  if (gap.preferredIn > 0) {
    parts.push(
      gap.requiredIn > 0
        ? `preferred by ${jobs(gap.preferredIn)}`
        : `Preferred by ${gap.preferredIn} of your top ${targetCount} matches`,
    );
  }
  return parts.join(", ") + ".";
}

/** The measured effect of confirming the skill, or null when it has no measurable effect. */
export function gainText(gain: number): string | null {
  if (gain <= 0) return null;
  return `+${gain.toFixed(1)} points average compatibility if confirmed`;
}

/** Change between two numbers as a signed string with one decimal ("+4.2", "−1.0", "±0.0"). */
export function delta(
  current: number,
  previous: number | undefined,
): string | null {
  if (previous === undefined) return null;
  const d = Math.round((current - previous) * 10) / 10;
  if (d === 0) return "±0.0";
  return d > 0 ? `+${d.toFixed(1)}` : `−${Math.abs(d).toFixed(1)}`;
}

export function groupByPriority(gaps: SkillGap[]): [GapPriority, SkillGap[]][] {
  return PRIORITY_ORDER.map(
    (p) =>
      [p, gaps.filter((g) => g.priority === p)] as [GapPriority, SkillGap[]],
  ).filter(([, list]) => list.length > 0);
}

export function totalWeeks(
  steps: { estimatedWeeks: number; done: boolean }[],
): number {
  return steps
    .filter((s) => !s.done)
    .reduce((sum, s) => sum + s.estimatedWeeks, 0);
}

export function previousPoint(
  history: GapReportPoint[],
  id: string,
): GapReportPoint | undefined {
  const i = history.findIndex((h) => h.id === id);
  return i > 0 ? history[i - 1] : undefined;
}
