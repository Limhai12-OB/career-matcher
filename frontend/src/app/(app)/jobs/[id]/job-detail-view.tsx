"use client";

import { ArrowLeft, Building2, CalendarDays, Check, ExternalLink, FlaskConical, GraduationCap, MapPin, Timer, Wallet } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";

import { OriginBadge } from "@/components/jobs/origin-badge";
import { CompatibilityCard } from "@/components/matching/compatibility-card";
import { ErrorState, PageLoading } from "@/components/states/states";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ApiError } from "@/lib/api/errors";
import { useCandidateSkills, useJob } from "@/lib/api/hooks";
import type { JobDetail, JobSkill } from "@/lib/api/types";
import { useSession } from "@/lib/auth/use-session";
import { EMPLOYMENT_TYPE_LABELS } from "@/lib/labels";
import {
  EDUCATION_LABELS,
  formatExperience,
  formatLocation,
  formatSalary,
  isSafeExternalUrl,
  postedAgo,
  REMOTE_TYPE_LABELS,
  SENIORITY_LABELS,
} from "@/lib/jobs";

export function JobDetailView() {
  const { id } = useParams<{ id: string }>();
  const job = useJob(id);

  return (
    <div className="animate-fade-in space-y-6">
      <Link href="/jobs" className="inline-flex items-center gap-1 text-sm text-primary hover:underline">
        <ArrowLeft className="size-4" aria-hidden /> All jobs
      </Link>
      {job.isPending ? (
        <PageLoading label="Loading job" />
      ) : job.isError ? (
        <ErrorState
          error={job.error}
          title={job.error instanceof ApiError && job.error.status === 404 ? "This job is no longer available" : undefined}
          onRetry={() => void job.refetch()}
        />
      ) : (
        <JobBody job={job.data} />
      )}
    </div>
  );
}

function JobBody({ job }: { job: JobDetail }) {
  const { user } = useSession();
  const isCandidate = user?.role === "CANDIDATE";
  const mySkills = useCandidateSkills();
  const owned = new Set(isCandidate ? (mySkills.data ?? []).map((s) => s.skillId) : []);
  const salary = formatSalary(job);
  const demo = job.dataOrigin === "DEMO";

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
      <div className="space-y-6">
        <header>
          <div className="flex flex-wrap items-center gap-2">
            <OriginBadge origin={job.dataOrigin} />
            <Badge variant="outline">{SENIORITY_LABELS[job.seniority]}</Badge>
          </div>
          <h1 className="mt-3 text-2xl font-semibold tracking-tight sm:text-3xl">{job.title}</h1>
          <p className="mt-1 flex items-center gap-1.5 text-muted-foreground">
            <Building2 className="size-4" aria-hidden /> {job.companyName}
          </p>
        </header>

        {demo && (
          <Alert className="border-amber-300 bg-amber-50 text-amber-900">
            <FlaskConical />
            <AlertTitle>Demonstration listing</AlertTitle>
            <AlertDescription className="text-amber-900">
              This is sample data from a fictional company, included to demonstrate the platform. It is
              not a real job opening and cannot be applied to.
            </AlertDescription>
          </Alert>
        )}

        {isCandidate && <CompatibilityCard jobId={job.id} />}

        <Card>
          <CardHeader>
            <CardTitle>Skills</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <SkillGroup title="Required" skills={job.skills.filter((s) => s.requirement === "REQUIRED")} owned={owned} />
            <SkillGroup title="Preferred" skills={job.skills.filter((s) => s.requirement === "PREFERRED")} owned={owned} />
            {isCandidate && mySkills.data && (
              <p className="text-xs text-muted-foreground">
                <Check className="mr-1 inline size-3 text-emerald-600" aria-hidden />
                marks skills already confirmed in your profile.{" "}
                {mySkills.data.length === 0 && (
                  <Link href="/cv" className="text-primary hover:underline">
                    Analyze your CV to add skills.
                  </Link>
                )}
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>About this role</CardTitle>
          </CardHeader>
          <CardContent>
            {/* Plain text only: never rendered as HTML. */}
            <div className="text-sm leading-relaxed whitespace-pre-line">{job.description}</div>
          </CardContent>
        </Card>
      </div>

      <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
        <Card>
          <CardContent className="space-y-3 text-sm">
            <Fact icon={MapPin} label="Location" value={formatLocation(job)} />
            <Fact icon={Timer} label="Type" value={`${EMPLOYMENT_TYPE_LABELS[job.employmentType]} · ${REMOTE_TYPE_LABELS[job.remoteType]}`} />
            {salary && <Fact icon={Wallet} label="Salary" value={salary} />}
            <Fact icon={Timer} label="Experience" value={formatExperience(job.minExperienceMonths, job.maxExperienceMonths)} />
            <Fact
              icon={GraduationCap}
              label="Education"
              value={
                job.educationLevel
                  ? `${EDUCATION_LABELS[job.educationLevel]}${job.educationFields.length ? ` (${job.educationFields.join(", ")})` : ""}`
                  : "Not specified"
              }
            />
            <Fact
              icon={CalendarDays}
              label="Posted"
              value={`${postedAgo(job.postedOn)}${job.expiresOn ? ` · closes ${new Date(job.expiresOn).toLocaleDateString()}` : ""}`}
            />
            {demo ? (
              <Button className="w-full" disabled>
                Demo listing — no real application
              </Button>
            ) : isSafeExternalUrl(job.applicationUrl) ? (
              <Button asChild className="w-full">
                <a href={job.applicationUrl} target="_blank" rel="noopener noreferrer nofollow">
                  Apply on employer site <ExternalLink />
                </a>
              </Button>
            ) : null}
            <p className="text-xs text-muted-foreground">
              Applications are handled by the employer, not by AI Career Matcher.
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="space-y-1 text-xs text-muted-foreground">
            <p>
              <span className="font-medium text-foreground">Source:</span>{" "}
              {isSafeExternalUrl(job.sourceUrl) && !demo ? (
                <a href={job.sourceUrl} target="_blank" rel="noopener noreferrer nofollow" className="text-primary hover:underline">
                  {job.sourceName}
                </a>
              ) : (
                job.sourceName
              )}
            </p>
            <p className="break-all">
              <span className="font-medium text-foreground">Application link:</span> {job.applicationUrl}
            </p>
            <p>Last updated {new Date(job.updatedAt).toLocaleDateString()}</p>
          </CardContent>
        </Card>
      </aside>
    </div>
  );
}

function SkillGroup({ title, skills, owned }: { title: string; skills: JobSkill[]; owned: Set<string> }) {
  if (skills.length === 0) return null;
  return (
    <div>
      <p className="mb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">{title}</p>
      <ul className="flex flex-wrap gap-1.5">
        {skills.map((s) => (
          <li key={s.skillId}>
            <Badge variant={owned.has(s.skillId) ? "default" : "secondary"} className="gap-1">
              {owned.has(s.skillId) && <Check className="size-3" aria-label="In your profile" />}
              {s.name}
            </Badge>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Fact({ icon: Icon, label, value }: { icon: React.ElementType; label: string; value: string }) {
  return (
    <div className="flex gap-3">
      <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
      <div>
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className="font-medium">{value}</p>
      </div>
    </div>
  );
}
