"use client";

import { AlertTriangle, ArrowLeft, Briefcase, Check, FolderGit2, GraduationCap, Info, Loader2, ShieldAlert, Award } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { toast } from "sonner";

import { AnalysisStatusBadge } from "@/components/cv/analysis-status";
import { Evidence } from "@/components/cv/evidence";
import { ErrorState, PageLoading } from "@/components/states/states";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { ApiError, userMessage } from "@/lib/api/errors";
import { useAnalysis, useApplySkills, useReanalyze } from "@/lib/api/hooks";
import type { AnalysisDetail, ExtractedSkill } from "@/lib/api/types";
import {
  DOCUMENT_ERRORS,
  analysisErrorMessage,
  dateRange,
  groupByCategory,
  providerLabel,
  WARNING_MESSAGES,
} from "@/lib/cv";

const LEVEL_LABELS: Record<string, string> = {
  HIGH_SCHOOL: "High school",
  VOCATIONAL: "Vocational",
  ASSOCIATE: "Associate degree",
  BACHELOR: "Bachelor's degree",
  MASTER: "Master's degree",
  DOCTORATE: "Doctorate",
  BOOTCAMP: "Bootcamp",
  OTHER: "Other",
};

const TYPE_LABELS: Record<string, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  INTERNSHIP: "Internship",
  CONTRACT: "Contract",
  FREELANCE: "Freelance",
  VOLUNTEER: "Volunteer",
  OTHER: "Other",
};

export function AnalysisView() {
  const { id } = useParams<{ id: string }>();
  const analysis = useAnalysis(id);

  return (
    <div className="animate-fade-in space-y-6">
      <Link href="/cv" className="inline-flex items-center gap-1 text-sm text-primary hover:underline">
        <ArrowLeft className="size-4" aria-hidden /> All CVs
      </Link>
      {analysis.isPending ? (
        <PageLoading label="Loading analysis" />
      ) : analysis.isError ? (
        <ErrorState
          error={analysis.error}
          title={analysis.error instanceof ApiError && analysis.error.status === 404 ? "Analysis not found" : undefined}
          onRetry={() => void analysis.refetch()}
        />
      ) : (
        <AnalysisBody data={analysis.data} />
      )}
    </div>
  );
}

function AnalysisBody({ data }: { data: AnalysisDetail }) {
  return (
    <>
      <header className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight break-all">{data.documentFilename}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Analysis started {new Date(data.createdAt).toLocaleString()}
            {data.provider && ` · ${providerLabel(data.provider, data.model)}`}
          </p>
        </div>
        <AnalysisStatusBadge status={data.status} />
      </header>

      {(data.status === "PENDING" || data.status === "PROCESSING") && <InProgress />}
      {data.status === "FAILED" && <Failed data={data} />}
      {data.status === "COMPLETED" && data.content && <Completed data={data} />}
    </>
  );
}

function InProgress() {
  return (
    <Card aria-live="polite" aria-busy="true">
      <CardContent className="flex items-center gap-4 py-6">
        <Loader2 className="size-6 shrink-0 animate-spin text-primary" aria-hidden />
        <div>
          <p className="font-medium">Analyzing your CV…</p>
          <p className="text-sm text-muted-foreground">
            We&apos;re reading your document and checking every result against its text. This usually
            takes less than a minute; you can leave this page and come back.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

function Failed({ data }: { data: AnalysisDetail }) {
  const router = useRouter();
  const reanalyze = useReanalyze();
  const fileProblem = data.errorCode !== null && DOCUMENT_ERRORS.has(data.errorCode);

  async function retry() {
    try {
      const a = await reanalyze.mutateAsync(data.documentId);
      router.push(`/cv/analyses/${a.id}`);
    } catch (err) {
      toast.error(userMessage(err));
    }
  }

  return (
    <Alert variant="destructive" role="alert">
      <AlertTriangle />
      <AlertTitle>The analysis didn&apos;t complete</AlertTitle>
      <AlertDescription>
        <p>{analysisErrorMessage(data.errorCode)}</p>
        <div className="mt-3 flex flex-wrap gap-2">
          {!fileProblem && (
            <Button size="sm" onClick={() => void retry()} disabled={reanalyze.isPending}>
              {reanalyze.isPending && <Loader2 className="animate-spin" />}
              Try again
            </Button>
          )}
          <Button size="sm" variant="outline" asChild>
            <Link href="/cv">Upload a different file</Link>
          </Button>
        </div>
      </AlertDescription>
    </Alert>
  );
}

function Completed({ data }: { data: AnalysisDetail }) {
  const c = data.content!;
  const dropped = Object.values(c.droppedItems).reduce((a, b) => a + b, 0);
  return (
    <div className="space-y-6">
      {data.warnings.map((w) => (
        <Alert key={w} className={w === "LIMITED_EXTRACTION" ? "" : "border-amber-300 bg-amber-50 text-amber-900"}>
          {w === "LIMITED_EXTRACTION" ? <Info /> : <ShieldAlert />}
          <AlertDescription className={w === "LIMITED_EXTRACTION" ? "" : "text-amber-900"}>
            {WARNING_MESSAGES[w]}
            {w === "UNVERIFIED_ITEMS_REMOVED" && dropped > 0 && ` (${dropped} item${dropped === 1 ? "" : "s"})`}
          </AlertDescription>
        </Alert>
      ))}

      <SkillsCard data={data} />

      <Section icon={GraduationCap} title="Education" empty={c.education.length === 0}>
        {c.education.map((e, i) => (
          <Item
            key={i}
            title={e.degree ?? LEVEL_LABELS[e.level] ?? e.level}
            subtitle={[e.institution, e.fieldOfStudy].filter(Boolean).join(" · ")}
            meta={dateRange(e.startDate, e.endDate, e.ongoing)}
            evidence={e.evidence}
          />
        ))}
      </Section>

      <Section icon={Briefcase} title="Experience" empty={c.experience.length === 0}>
        {c.experience.map((x, i) => (
          <Item
            key={i}
            title={x.title}
            subtitle={`${x.organization} · ${TYPE_LABELS[x.employmentType] ?? x.employmentType}`}
            meta={dateRange(x.startDate, x.endDate, x.current)}
            tags={x.skills}
            evidence={x.evidence}
          />
        ))}
      </Section>

      <Section icon={FolderGit2} title="Projects" empty={c.projects.length === 0}>
        {c.projects.map((p, i) => (
          <Item key={i} title={p.name} tags={p.skills} evidence={p.evidence} />
        ))}
      </Section>

      <Section icon={Award} title="Certifications" empty={c.certifications.length === 0}>
        {c.certifications.map((x, i) => (
          <Item key={i} title={x.name} subtitle={x.issuer} meta={x.year} evidence={x.evidence} />
        ))}
      </Section>
    </div>
  );
}

function SkillsCard({ data }: { data: AnalysisDetail }) {
  const recognized = useMemo(() => data.skills.filter((s) => s.skillId), [data.skills]);
  const unrecognized = data.skills.filter((s) => !s.skillId);
  const [selected, setSelected] = useState<Set<string>>(
    () => new Set(recognized.filter((s) => !s.inProfile).map((s) => s.skillId!)),
  );
  const apply = useApplySkills(data.id);
  const pending = recognized.filter((s) => !s.inProfile);

  async function confirm() {
    try {
      await apply.mutateAsync([...selected]);
      toast.success(`${selected.size} skill${selected.size === 1 ? "" : "s"} added to your profile.`);
      setSelected(new Set());
    } catch (err) {
      toast.error(userMessage(err));
    }
  }

  function toggle(s: ExtractedSkill, on: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (on) next.add(s.skillId!);
      else next.delete(s.skillId!);
      return next;
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Skills found in your CV</CardTitle>
        <CardDescription>
          Choose which skills to add to your profile. Only skills in your profile are used for job
          matching.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {recognized.length === 0 ? (
          <p className="text-sm text-muted-foreground">No recognized skills were found in this CV.</p>
        ) : (
          groupByCategory(recognized).map((group) => (
            <fieldset key={group.category}>
              <legend className="mb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">
                {group.label}
              </legend>
              <ul className="grid gap-2 sm:grid-cols-2">
                {group.items.map((s) => (
                  <li key={s.skillId} className="rounded-lg border p-3">
                    <label className="flex cursor-pointer items-start gap-3">
                      {s.inProfile ? (
                        <Check className="mt-0.5 size-4 text-emerald-600" aria-label="In your profile" />
                      ) : (
                        <Checkbox
                          checked={selected.has(s.skillId!)}
                          onCheckedChange={(v) => toggle(s, v === true)}
                          aria-label={`Add ${s.canonicalName}`}
                        />
                      )}
                      <span className="min-w-0 flex-1">
                        <span className="font-medium">{s.canonicalName}</span>
                        {s.rawName.toLowerCase() !== s.canonicalName!.toLowerCase() && (
                          <span className="ml-1 text-xs text-muted-foreground">(“{s.rawName}”)</span>
                        )}
                        {s.inProfile && <Badge variant="secondary" className="ml-2">In profile</Badge>}
                        <Evidence text={s.evidence} />
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            </fieldset>
          ))
        )}

        {unrecognized.length > 0 && (
          <div>
            <p className="mb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">
              Not in our skill catalog yet
            </p>
            <p className="mb-2 text-sm text-muted-foreground">
              These were found in your CV but can&apos;t be used for matching yet.
            </p>
            <div className="flex flex-wrap gap-1.5">
              {unrecognized.map((s) => (
                <Badge key={s.rawName} variant="outline" title={s.evidence}>
                  {s.rawName}
                </Badge>
              ))}
            </div>
          </div>
        )}

        {pending.length > 0 ? (
          <div className="flex flex-col gap-2 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-muted-foreground">
              {selected.size} of {pending.length} new skill{pending.length === 1 ? "" : "s"} selected
            </p>
            <Button onClick={() => void confirm()} disabled={selected.size === 0 || apply.isPending}>
              {apply.isPending && <Loader2 className="animate-spin" />}
              Add selected skills to my profile
            </Button>
          </div>
        ) : (
          recognized.length > 0 && (
            <p className="flex items-center gap-2 border-t pt-4 text-sm text-emerald-700">
              <Check className="size-4" /> All recognized skills are in your profile.{" "}
              <Link href="/skills" className="text-primary hover:underline">
                View skills
              </Link>
            </p>
          )
        )}
      </CardContent>
    </Card>
  );
}

function Section({
  icon: Icon,
  title,
  empty,
  children,
}: {
  icon: React.ElementType;
  title: string;
  empty: boolean;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Icon className="size-4 text-primary" aria-hidden /> {title}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {empty ? (
          <p className="text-sm text-muted-foreground">Nothing found in your CV for this section.</p>
        ) : (
          <ul className="divide-y">{children}</ul>
        )}
      </CardContent>
    </Card>
  );
}

function Item({
  title,
  subtitle,
  meta,
  tags,
  evidence,
}: {
  title: string;
  subtitle?: string | null;
  meta?: string | null;
  tags?: string[];
  evidence: string;
}) {
  return (
    <li className="py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-medium">{title}</p>
        {meta && <p className="text-xs text-muted-foreground">{meta}</p>}
      </div>
      {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
      {tags && tags.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {tags.map((t) => (
            <Badge key={t} variant="secondary" className="font-normal">
              {t}
            </Badge>
          ))}
        </div>
      )}
      <Evidence text={evidence} />
    </li>
  );
}
