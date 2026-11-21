"use client";

import { Building2, ChevronLeft, ChevronRight, MapPin } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo } from "react";

import { NativeSelect } from "@/components/forms/native-select";
import { OriginBadge } from "@/components/jobs/origin-badge";
import { DataHints } from "@/components/matching/data-hints";
import { ScoreRing } from "@/components/matching/score-ring";
import { PageHeader } from "@/components/shell/app-shell";
import { EmptyState, ErrorState } from "@/components/states/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useRecommendations } from "@/lib/api/hooks";
import type { MatchSummary } from "@/lib/api/types";
import { formatLocation } from "@/lib/jobs";
import { COMPONENT_LABELS, scoreBand } from "@/lib/matching";

export function RecommendationsView() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const origin = ["REAL", "DEMO"].includes(sp.get("origin") ?? "") ? sp.get("origin")! : "";
  const page = Math.max(0, Math.min(999, Number(sp.get("page")) || 0));
  const params = useMemo(() => {
    const p = new URLSearchParams({ size: "10" });
    if (origin) p.set("origin", origin);
    if (page) p.set("page", String(page));
    return p;
  }, [origin, page]);
  const recs = useRecommendations(params);

  function go(patch: { origin?: string; page?: number }) {
    const p = new URLSearchParams();
    const o = patch.origin ?? origin;
    const pg = patch.page ?? 0;
    if (o) p.set("origin", o);
    if (pg) p.set("page", String(pg));
    router.replace(p.size ? `${pathname}?${p}` : pathname, { scroll: false });
  }

  return (
    <div className="animate-fade-in space-y-6">
      <PageHeader
        title="Recommended for you"
        description="Jobs ranked by a transparent compatibility score. Open a job to see exactly how it was calculated."
        actions={
          <NativeSelect aria-label="Listing type" value={origin} onChange={(e) => go({ origin: e.target.value })} className="w-56">
            <option value="">Real and demo listings</option>
            <option value="REAL">Real opportunities only</option>
            <option value="DEMO">Demo listings only</option>
          </NativeSelect>
        }
      />
      {recs.data && <DataHints data={recs.data.candidateData} />}

      {recs.isPending ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-28 rounded-2xl" />)}
        </div>
      ) : recs.isError ? (
        <ErrorState error={recs.error} onRetry={() => void recs.refetch()} />
      ) : recs.data.content.length === 0 ? (
        <EmptyState title="No jobs to recommend yet" description="There are no published jobs matching this filter." />
      ) : (
        <>
          <ol className={`space-y-3 ${recs.isPlaceholderData ? "opacity-60" : ""}`} aria-label="Recommended jobs">
            {recs.data.content.map((m, i) => (
              <li key={m.job.id}>
                <RecommendationCard match={m} rank={page * 10 + i + 1} />
              </li>
            ))}
          </ol>
          {recs.data.page.totalPages > 1 && (
            <nav aria-label="Pagination" className="flex items-center justify-center gap-3">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => go({ page: page - 1 })}>
                <ChevronLeft /> Previous
              </Button>
              <span className="text-sm text-muted-foreground">
                Page {page + 1} of {recs.data.page.totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={page + 1 >= recs.data.page.totalPages} onClick={() => go({ page: page + 1 })}>
                Next <ChevronRight />
              </Button>
            </nav>
          )}
        </>
      )}
    </div>
  );
}

function RecommendationCard({ match, rank }: { match: MatchSummary; rank: number }) {
  const { job } = match;
  return (
    <Link
      href={`/jobs/${job.id}`}
      className="group flex gap-4 rounded-2xl border bg-card p-4 shadow-xs transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none sm:p-5"
    >
      <ScoreRing score={match.totalScore} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-xs text-muted-foreground">
              #{rank} · {scoreBand(match.totalScore).label}
            </p>
            <h3 className="font-semibold group-hover:text-primary">{job.title}</h3>
          </div>
          <OriginBadge origin={job.dataOrigin} />
        </div>
        <p className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
          <span className="flex items-center gap-1.5"><Building2 className="size-3.5" aria-hidden /> {job.companyName}</span>
          <span className="flex items-center gap-1.5"><MapPin className="size-3.5" aria-hidden /> {formatLocation(job)}</span>
        </p>
        <p className="mt-2 text-sm">
          <span className="font-medium">
            {match.matchedRequiredSkillCount} of {match.requiredSkillCount}
          </span>{" "}
          required skills
          {match.missingRequiredSkills.length > 0 && (
            <>
              {" "}· missing{" "}
              {match.missingRequiredSkills.slice(0, 3).map((s) => (
                <Badge key={s} variant="outline" className="mr-1 border-red-200 text-red-700">
                  {s}
                </Badge>
              ))}
              {match.missingRequiredSkills.length > 3 && `+${match.missingRequiredSkills.length - 3}`}
            </>
          )}
        </p>
        <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Score by component">
          {match.components.map((c) => (
            <span key={c.component} className="rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground">
              {COMPONENT_LABELS[c.component]} {c.status === "MISSING_CANDIDATE_DATA" ? "?" : `${Math.round(c.score * 100)}%`}
            </span>
          ))}
        </div>
      </div>
    </Link>
  );
}
