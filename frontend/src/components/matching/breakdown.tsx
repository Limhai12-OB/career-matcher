import { CheckCircle2, CircleDashed, MinusCircle, XCircle } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import type { ComponentScore } from "@/lib/api/types";
import { COMPONENT_LABELS, describeFactor, STATUS_LABELS } from "@/lib/matching";
import { cn } from "@/lib/utils";

const TONE_ICON = {
  positive: <CheckCircle2 className="size-4 shrink-0 text-emerald-600" aria-hidden />,
  negative: <XCircle className="size-4 shrink-0 text-red-500" aria-hidden />,
  neutral: <MinusCircle className="size-4 shrink-0 text-muted-foreground" aria-hidden />,
};

/** Per-component score bars with the factors behind each one. */
export function MatchBreakdown({ components }: { components: ComponentScore[] }) {
  return (
    <ul className="space-y-5">
      {components.map((c) => {
        const pct = Math.round(c.score * 100);
        return (
          <li key={c.component}>
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <p className="text-sm font-medium">
                {COMPONENT_LABELS[c.component]}{" "}
                <span className="text-xs font-normal text-muted-foreground">
                  · weight {Math.round(c.weight * 100)}%
                </span>
              </p>
              <div className="flex items-center gap-2">
                {c.status !== "SCORED" && (
                  <Badge
                    variant="outline"
                    className={cn(c.status === "MISSING_CANDIDATE_DATA" && "border-amber-300 text-amber-800")}
                  >
                    {c.status === "MISSING_CANDIDATE_DATA" && <CircleDashed className="size-3" aria-hidden />}
                    {STATUS_LABELS[c.status]}
                  </Badge>
                )}
                <span className="text-sm tabular-nums">
                  {pct}% <span className="text-muted-foreground">→ {c.contribution.toFixed(1)} pts</span>
                </span>
              </div>
            </div>
            <div
              className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted"
              role="progressbar"
              aria-label={`${COMPONENT_LABELS[c.component]} score`}
              aria-valuenow={pct}
              aria-valuemin={0}
              aria-valuemax={100}
            >
              <div
                className={cn(
                  "h-full rounded-full transition-[width] duration-700",
                  c.status === "MISSING_CANDIDATE_DATA" ? "bg-amber-400" : "bg-primary",
                )}
                style={{ width: `${pct}%` }}
              />
            </div>
            {c.factors.length > 0 && (
              <ul className="mt-2 space-y-1">
                {c.factors.map((f, i) => {
                  const d = describeFactor(f);
                  return (
                    <li key={`${f.code}-${i}`} className="flex items-start gap-2 text-sm text-muted-foreground">
                      {TONE_ICON[d.tone]}
                      <span>{d.text}</span>
                    </li>
                  );
                })}
              </ul>
            )}
          </li>
        );
      })}
    </ul>
  );
}
