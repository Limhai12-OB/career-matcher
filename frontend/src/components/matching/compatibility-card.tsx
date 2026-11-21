"use client";

import { ErrorState } from "@/components/states/states";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useMatch } from "@/lib/api/hooks";
import { scoreBand } from "@/lib/matching";

import { MatchBreakdown } from "./breakdown";
import { DataHints } from "./data-hints";
import { ExplanationCard } from "./explanation-card";
import { ScoreRing } from "./score-ring";

/** The candidate's compatibility with one job: score, breakdown, explanation. */
export function CompatibilityCard({ jobId }: { jobId: string }) {
  const match = useMatch(jobId);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Your compatibility</CardTitle>
        <CardDescription>
          Calculated by a transparent formula from your confirmed skills, CV and preferences.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {match.isPending ? (
          <Skeleton className="h-48 w-full" />
        ) : match.isError ? (
          <ErrorState error={match.error} onRetry={() => void match.refetch()} title="We couldn't calculate your score" />
        ) : (
          <>
            <div className="flex items-center gap-4">
              <ScoreRing score={match.data.result.totalScore} size={80} />
              <div>
                <p className="text-lg font-semibold">{scoreBand(match.data.result.totalScore).label}</p>
                <p className="text-xs text-muted-foreground">
                  {match.data.result.totalScore.toFixed(1)} / 100 · algorithm {match.data.result.algorithmVersion}
                </p>
              </div>
            </div>
            <DataHints data={match.data.candidateData} />
            <MatchBreakdown components={match.data.result.components} />
            <ExplanationCard key={match.data.resultId} jobId={jobId} explanation={match.data.explanation} />
          </>
        )}
      </CardContent>
    </Card>
  );
}
