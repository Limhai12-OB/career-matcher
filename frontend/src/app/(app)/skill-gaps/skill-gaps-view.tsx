"use client";

import {
  BookOpen,
  CheckCircle2,
  Circle,
  ExternalLink,
  Loader2,
  Map,
  RefreshCw,
  Target,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

import { PageHeader } from "@/components/shell/app-shell";
import { EmptyState, ErrorState } from "@/components/states/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError, userMessage } from "@/lib/api/errors";
import {
  useGapProgress,
  useGapReport,
  useGenerateGapReport,
  useGenerateRoadmap,
} from "@/lib/api/hooks";
import type {
  GapProgress,
  GapReport,
  LearningResource,
  SkillGap,
} from "@/lib/api/types";
import {
  delta,
  gainText,
  gapReason,
  groupByPriority,
  PRIORITY_META,
  previousPoint,
  totalWeeks,
} from "@/lib/gaps";
import { cn } from "@/lib/utils";

const ERRORS: Record<string, string> = {
  RATE_LIMITED: "You've run many analyses recently. Please try again later.",
};

function errorText(e: unknown) {
  return e instanceof ApiError && ERRORS[e.code]
    ? ERRORS[e.code]
    : userMessage(e);
}

export function SkillGapsView() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const progress = useGapProgress();
  const selected = sp.get("report") ?? progress.data?.latestReportId ?? null;
  const report = useGapReport(selected);
  const generate = useGenerateGapReport();

  function analyze() {
    generate.mutate(undefined, {
      onSuccess: () => router.replace(pathname, { scroll: false }),
    });
  }

  const header = (
    <PageHeader
      title="Skill gaps"
      description="The skills your best-matching jobs ask for that you haven't confirmed yet, ranked by how much they matter."
      actions={
        <Button onClick={analyze} disabled={generate.isPending}>
          {generate.isPending ? (
            <Loader2 className="animate-spin" />
          ) : (
            <RefreshCw />
          )}
          {progress.data?.latestReportId
            ? "Run a new analysis"
            : "Analyze my skill gaps"}
        </Button>
      }
    />
  );

  return (
    <div className="animate-fade-in space-y-6">
      {header}
      {generate.isError && (
        <p role="alert" className="text-sm text-destructive">
          {errorText(generate.error)}
        </p>
      )}
      {progress.isPending ? (
        <Skeleton className="h-40 rounded-2xl" />
      ) : progress.isError ? (
        <ErrorState
          error={progress.error}
          onRetry={() => void progress.refetch()}
        />
      ) : !progress.data.latestReportId ? (
        <EmptyState
          title="No analysis yet"
          description="Run an analysis to compare your confirmed skills with your 10 best-matching jobs. Confirm your skills first for the most accurate result."
        />
      ) : report.isPending ? (
        <Skeleton className="h-96 rounded-2xl" />
      ) : report.isError ? (
        <ErrorState
          error={report.error}
          onRetry={() => void report.refetch()}
        />
      ) : (
        <>
          <ProgressPanel progress={progress.data} report={report.data} />
          <div className="grid gap-6 lg:grid-cols-5">
            <div className="space-y-6 lg:col-span-3">
              <GapList report={report.data} />
            </div>
            <div className="space-y-6 lg:col-span-2">
              <RoadmapCard report={report.data} />
              <Strengths report={report.data} />
            </div>
          </div>
          <History progress={progress.data} selected={report.data.id} />
        </>
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  change,
  hint,
}: {
  label: string;
  value: string;
  change?: string | null;
  hint: string;
}) {
  return (
    <div className="rounded-xl border bg-card p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">
        {value}
        {change && (
          <span className="ml-2 text-sm font-medium text-muted-foreground">
            {change} vs last
          </span>
        )}
      </p>
      <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
    </div>
  );
}

function ProgressPanel({
  progress,
  report,
}: {
  progress: GapProgress;
  report: GapReport;
}) {
  const prev = previousPoint(progress.history, report.id);
  const open = report.gaps.filter((g) => !g.closed);
  const important = open.filter(
    (g) => g.priority === "CRITICAL" || g.priority === "HIGH",
  ).length;
  return (
    <section
      aria-label="Progress"
      className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
    >
      <Stat
        label="Required skills you have"
        value={`${report.requiredCoverage.toFixed(1)}%`}
        change={delta(report.requiredCoverage, prev?.requiredCoverage)}
        hint={`Across your top ${report.targetJobCount} matches`}
      />
      <Stat
        label="Average top-match score"
        value={report.averageScore.toFixed(1)}
        change={delta(report.averageScore, prev?.averageScore)}
        hint="Compatibility estimate, out of 100"
      />
      <Stat
        label="Important gaps open"
        value={String(important)}
        hint="Critical and high priority"
      />
      <Stat
        label="Closed since first analysis"
        value={String(progress.closedSinceFirst.length)}
        hint={
          progress.closedSinceFirst.length
            ? progress.closedSinceFirst.slice(0, 3).join(", ")
            : `${progress.openFromFirst.length} important gaps from your first analysis still open`
        }
      />
    </section>
  );
}

function GapList({ report }: { report: GapReport }) {
  if (report.gaps.length === 0) {
    return (
      <EmptyState
        title="No skill gaps"
        description="You have every skill your best-matching jobs list. Keep your CV and skills up to date."
      />
    );
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>Missing skills</CardTitle>
        <CardDescription>
          Priority comes from how many of your top matches require each skill.
          The point gain is measured by re-scoring those jobs as if you had the
          skill.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {groupByPriority(report.gaps).map(([priority, gaps]) => (
          <section
            key={priority}
            aria-label={`${PRIORITY_META[priority].label} priority`}
          >
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold">
              <Badge
                variant="outline"
                className={PRIORITY_META[priority].className}
              >
                {PRIORITY_META[priority].label}
              </Badge>
              <span className="font-normal text-muted-foreground">
                {PRIORITY_META[priority].description}
              </span>
            </h3>
            <ul className="space-y-2">
              {gaps.map((g) => (
                <GapItem
                  key={g.skillId}
                  gap={g}
                  targetCount={report.targetJobCount}
                />
              ))}
            </ul>
          </section>
        ))}
      </CardContent>
    </Card>
  );
}

function GapItem({ gap, targetCount }: { gap: SkillGap; targetCount: number }) {
  const gain = gainText(gap.averageGain);
  return (
    <li
      className={cn("rounded-xl border p-3", gap.closed && "bg-muted/50")}
      data-testid="skill-gap"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-medium">{gap.name}</p>
        {gap.closed ? (
          <Badge variant="outline" className="border-green-200 text-green-700">
            <CheckCircle2 aria-hidden /> Now confirmed
          </Badge>
        ) : (
          gain && (
            <span className="text-xs font-medium text-primary">{gain}</span>
          )
        )}
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        {gapReason(gap, targetCount)}
      </p>
      {gap.jobs.length > 0 && (
        <p className="mt-1 text-xs text-muted-foreground">
          Listed by{" "}
          {gap.jobs.slice(0, 3).map((j, i) => (
            <span key={j.jobId}>
              {i > 0 && ", "}
              <Link
                className="underline-offset-2 hover:underline"
                href={`/jobs/${j.jobId}`}
              >
                {j.title}
              </Link>
            </span>
          ))}
          {gap.jobs.length > 3 && ` and ${gap.jobs.length - 3} more`}
        </p>
      )}
      {!gap.closed && gap.resources.length > 0 && (
        <Resources resources={gap.resources} />
      )}
    </li>
  );
}

function Resources({ resources }: { resources: LearningResource[] }) {
  return (
    <ul className="mt-2 flex flex-wrap gap-2" aria-label="Learning resources">
      {resources.map((r) => (
        <li key={r.id}>
          <a
            href={r.url}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs hover:border-primary/40 hover:text-primary"
          >
            <BookOpen className="size-3" aria-hidden />
            {r.title}{" "}
            <span className="text-muted-foreground">· {r.provider}</span>
            <ExternalLink className="size-3" aria-hidden />
          </a>
        </li>
      ))}
    </ul>
  );
}

function RoadmapCard({ report }: { report: GapReport }) {
  const create = useGenerateRoadmap(report.id);
  const roadmap = report.roadmap;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Map className="size-4" aria-hidden /> Learning roadmap
        </CardTitle>
        <CardDescription>
          {roadmap
            ? roadmap.provider === "local"
              ? "Built from your skill gaps (no AI)."
              : "Written by AI from your skill gaps and checked before it's shown."
            : "A step-by-step plan for your most important gaps, using free official resources."}
        </CardDescription>
        {roadmap && (
          <CardAction>
            <span className="text-xs text-muted-foreground">
              ~{totalWeeks(roadmap.steps)} weeks left
            </span>
          </CardAction>
        )}
      </CardHeader>
      <CardContent>
        {!roadmap ? (
          report.gaps.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              Nothing to plan: you have no skill gaps.
            </p>
          ) : (
            <>
              <Button
                onClick={() => create.mutate()}
                disabled={create.isPending}
              >
                {create.isPending ? (
                  <Loader2 className="animate-spin" />
                ) : (
                  <Target />
                )}
                Create my roadmap
              </Button>
              {create.isError && (
                <p role="alert" className="mt-2 text-sm text-destructive">
                  {errorText(create.error)}
                </p>
              )}
            </>
          )
        ) : (
          <div className="space-y-4" aria-live="polite">
            <p className="text-sm leading-relaxed">{roadmap.summary}</p>
            <ol className="space-y-3">
              {roadmap.steps.map((s, i) => (
                <li
                  key={s.skill}
                  className={cn(
                    "rounded-xl border p-3",
                    s.done && "bg-muted/50",
                  )}
                  data-testid="roadmap-step"
                >
                  <div className="flex items-start gap-2">
                    {s.done ? (
                      <CheckCircle2
                        className="mt-0.5 size-4 shrink-0 text-green-600"
                        aria-label="Done"
                      />
                    ) : (
                      <Circle
                        className="mt-0.5 size-4 shrink-0 text-muted-foreground"
                        aria-label="Not done"
                      />
                    )}
                    <div className="min-w-0 flex-1">
                      <p
                        className={cn(
                          "font-medium",
                          s.done &&
                            "line-through decoration-muted-foreground/50",
                        )}
                      >
                        {i + 1}. {s.title}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {s.skill} · about {s.estimatedWeeks}{" "}
                        {s.estimatedWeeks === 1 ? "week" : "weeks"}
                      </p>
                      {!s.done && (
                        <>
                          <p className="mt-1 text-sm">{s.why}</p>
                          <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-muted-foreground">
                            {s.activities.map((a) => (
                              <li key={a}>{a}</li>
                            ))}
                          </ul>
                          {s.resources.length > 0 && (
                            <Resources resources={s.resources} />
                          )}
                        </>
                      )}
                    </div>
                  </div>
                </li>
              ))}
            </ol>
            <p className="text-xs text-muted-foreground">
              Steps are marked done when you confirm the skill on{" "}
              <Link className="underline" href="/skills">
                My skills
              </Link>
              . Scores are estimates: learning a skill improves your
              compatibility, but doesn&apos;t guarantee an interview or job.
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Strengths({ report }: { report: GapReport }) {
  if (report.strengths.length === 0) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Your strengths</CardTitle>
        <CardDescription>
          Required skills you already have in your top matches.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2">
        {report.strengths.map((s) => (
          <Badge key={s.name} variant="secondary">
            {s.name} · {s.requiredIn} {s.requiredIn === 1 ? "job" : "jobs"}
          </Badge>
        ))}
      </CardContent>
    </Card>
  );
}

function History({
  progress,
  selected,
}: {
  progress: GapProgress;
  selected: string;
}) {
  if (progress.history.length < 2) return null;
  const items = [...progress.history].reverse();
  return (
    <Card>
      <CardHeader>
        <CardTitle>Analysis history</CardTitle>
        <CardDescription>
          Each analysis is saved, so you can see how your gaps change over time.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ol className="divide-y" aria-label="Analysis history">
          {items.map((h) => (
            <li key={h.id}>
              <Link
                href={`?report=${h.id}`}
                scroll={false}
                aria-current={h.id === selected ? "true" : undefined}
                className={cn(
                  "flex flex-wrap items-center justify-between gap-2 px-2 py-2 text-sm hover:bg-muted/50",
                  h.id === selected && "bg-accent/50 font-medium",
                )}
              >
                <span>{new Date(h.createdAt).toLocaleString()}</span>
                <span className="flex gap-4 text-muted-foreground tabular-nums">
                  <span>{h.requiredCoverage.toFixed(1)}% coverage</span>
                  <span>avg {h.averageScore.toFixed(1)}</span>
                  <span>
                    {h.criticalCount + h.highCount} important / {h.gapCount}{" "}
                    gaps
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}
