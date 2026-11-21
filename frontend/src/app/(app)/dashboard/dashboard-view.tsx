"use client";

import { ArrowRight, Briefcase, CheckCircle2, Circle, FileText, MapPin, Target } from "lucide-react";
import Link from "next/link";

import { PageHeader } from "@/components/shell/app-shell";
import { ErrorState, PageLoading } from "@/components/states/states";
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
import { AnalysisStatusBadge } from "@/components/cv/analysis-status";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { useCandidateSkills, useCvDocuments, usePreferences, useProfile } from "@/lib/api/hooks";
import { countryName } from "@/lib/countries";
import { COMPLETENESS_ACTIONS, EMPLOYMENT_TYPE_LABELS, REMOTE_LABELS } from "@/lib/labels";

const PREFERENCE_KEYS = new Set(["desiredRoles", "employmentTypes", "remotePreference"]);

export function DashboardView() {
  const profile = useProfile();
  const prefs = usePreferences();

  if (profile.isPending || prefs.isPending) return <PageLoading label="Loading dashboard" />;
  if (profile.isError || prefs.isError) {
    return (
      <ErrorState
        error={profile.error ?? prefs.error}
        onRetry={() => {
          void profile.refetch();
          void prefs.refetch();
        }}
      />
    );
  }

  const p = profile.data;
  const pr = prefs.data;
  const { percent, missing } = p.completeness;
  const firstName = p.fullName.split(" ")[0];

  return (
    <div className="animate-fade-in">
      <PageHeader title={`Hi ${firstName} 👋`} description="Here's where your career profile stands." />

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Profile strength</CardTitle>
            <CardDescription>
              A complete profile leads to more accurate job matches once matching launches.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="flex items-center gap-4">
              <Progress value={percent} className="h-2.5" aria-label="Profile completeness" />
              <span className="w-12 text-right text-sm font-semibold tabular-nums">{percent}%</span>
            </div>
            {missing.length === 0 ? (
              <p className="flex items-center gap-2 text-sm text-emerald-700">
                <CheckCircle2 className="size-4" /> Your profile is complete.
              </p>
            ) : (
              <ul className="grid gap-2 sm:grid-cols-2">
                {missing.map((key) => (
                  <li key={key}>
                    <Link
                      href={PREFERENCE_KEYS.has(key) ? "/preferences" : "/profile"}
                      className="flex items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-sm text-muted-foreground transition-colors hover:border-primary hover:text-primary"
                    >
                      <Circle className="size-3.5" aria-hidden />
                      {COMPLETENESS_ACTIONS[key] ?? key}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <CvCard />

        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>What you&apos;re looking for</CardTitle>
            <CardDescription>Used to rank job recommendations.</CardDescription>
            <CardAction>
              <Button asChild variant="outline" size="sm">
                <Link href="/preferences">
                  Edit <ArrowRight />
                </Link>
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent className="grid gap-6 sm:grid-cols-3">
            <Summary icon={Target} label="Roles">
              {pr.desiredRoles.length ? (
                <div className="flex flex-wrap gap-1.5">
                  {pr.desiredRoles.map((r) => (
                    <Badge key={r} variant="secondary">
                      {r}
                    </Badge>
                  ))}
                </div>
              ) : (
                <Missing />
              )}
            </Summary>
            <Summary icon={Briefcase} label="Type & arrangement">
              {pr.employmentTypes.length || pr.remotePreference ? (
                <p>
                  {[
                    pr.employmentTypes.map((t) => EMPLOYMENT_TYPE_LABELS[t]).join(", "),
                    pr.remotePreference && REMOTE_LABELS[pr.remotePreference],
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              ) : (
                <Missing />
              )}
            </Summary>
            <Summary icon={MapPin} label="Location">
              <p>
                {[p.city, countryName(p.countryCode)].filter(Boolean).join(", ") || <Missing />}
                {pr.preferredLocations.length > 0 && (
                  <span className="block text-muted-foreground">
                    Prefers: {pr.preferredLocations.join(", ")}
                  </span>
                )}
                {pr.willingToRelocate && (
                  <span className="block text-muted-foreground">Open to relocation</span>
                )}
              </p>
            </Summary>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function Summary({
  icon: Icon,
  label,
  children,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-2 text-sm">
      <p className="flex items-center gap-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">
        <Icon className="size-3.5" /> {label}
      </p>
      {children}
    </div>
  );
}

function Missing() {
  return <span className="text-muted-foreground">Not set</span>;
}

function CvCard() {
  const docs = useCvDocuments();
  const skills = useCandidateSkills();
  const latestDoc = docs.data?.[0];
  const latest = latestDoc?.analyses[0];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <FileText className="size-4 text-primary" aria-hidden /> CV & skills
        </CardTitle>
        <CardDescription>Your skills come from CVs you analyze and confirm.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {docs.isPending || skills.isPending ? (
          <Skeleton className="h-16 w-full" />
        ) : docs.isError || skills.isError ? (
          <p className="text-muted-foreground">Couldn&apos;t load your CV status.</p>
        ) : !latestDoc ? (
          <>
            <p className="text-muted-foreground">You haven&apos;t uploaded a CV yet.</p>
            <Button asChild size="sm">
              <Link href="/cv">Upload your CV</Link>
            </Button>
          </>
        ) : (
          <>
            <div className="flex items-center justify-between gap-2">
              <span className="truncate font-medium" title={latestDoc.filename}>
                {latestDoc.filename}
              </span>
              {latest && <AnalysisStatusBadge status={latest.status} />}
            </div>
            <p className="text-muted-foreground">
              <span className="font-semibold text-foreground">{skills.data.length}</span> confirmed
              skill{skills.data.length === 1 ? "" : "s"} in your profile
            </p>
            <div className="flex flex-wrap gap-2">
              {latest && (
                <Button asChild size="sm" variant="outline">
                  <Link href={`/cv/analyses/${latest.id}`}>View analysis</Link>
                </Button>
              )}
              <Button asChild size="sm" variant="ghost">
                <Link href="/skills">My skills</Link>
              </Button>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
