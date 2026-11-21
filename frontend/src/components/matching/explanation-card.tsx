"use client";

import { Loader2, Sparkles } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ApiError, userMessage } from "@/lib/api/errors";
import { useExplainMatch } from "@/lib/api/hooks";
import type { ExplanationPoint, MatchExplanation } from "@/lib/api/types";

const EXPLANATION_ERRORS: Record<string, string> = {
  RATE_LIMITED: "You've requested many explanations recently. Please try again later.",
};

export function ExplanationCard({ jobId, explanation }: { jobId: string; explanation: MatchExplanation | null }) {
  const explain = useExplainMatch(jobId);
  const data = explanation ?? explain.data ?? null;

  if (!data) {
    const error = explain.error;
    return (
      <div className="rounded-xl border border-dashed p-4">
        <p className="text-sm text-muted-foreground">
          Get a plain-language summary of this score. It&apos;s written only from the breakdown above and
          can&apos;t change your score.
        </p>
        <Button className="mt-3" variant="outline" onClick={() => explain.mutate()} disabled={explain.isPending}>
          {explain.isPending ? <Loader2 className="animate-spin" /> : <Sparkles />}
          Explain this match
        </Button>
        {error && (
          <p role="alert" className="mt-2 text-sm text-destructive">
            {error instanceof ApiError && EXPLANATION_ERRORS[error.code] ? EXPLANATION_ERRORS[error.code] : userMessage(error)}
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-4 rounded-xl border bg-accent/40 p-4" aria-live="polite">
      <p className="flex items-center gap-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">
        <Sparkles className="size-3.5" aria-hidden />
        {data.provider === "local" ? "Summary generated from your score breakdown (no AI)" : "AI explanation of your score"}
      </p>
      <p className="text-sm leading-relaxed">{data.summary}</p>
      <PointList title="Strengths" points={data.strengths} />
      <PointList title="Gaps" points={data.gaps} />
      <PointList title="Next steps" points={data.nextSteps} />
      <p className="text-xs text-muted-foreground">
        Checked against your breakdown: it only mentions skills and numbers that appear in your score.
      </p>
    </div>
  );
}

function PointList({ title, points }: { title: string; points: ExplanationPoint[] }) {
  if (points.length === 0) return null;
  return (
    <div>
      <p className="mb-1 text-sm font-medium">{title}</p>
      <ul className="list-disc space-y-1 pl-5 text-sm">
        {points.map((p, i) => (
          <li key={i}>{p.text}</li>
        ))}
      </ul>
    </div>
  );
}
