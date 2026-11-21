"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Check, Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { FormAlert } from "@/components/forms/form-alert";
import { PreferencesForm } from "@/components/forms/preferences-form";
import { ProfileForm } from "@/components/forms/profile-form";
import { ErrorState, PageLoading } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { onboardingApi } from "@/lib/api/endpoints";
import { ApiError, userMessage } from "@/lib/api/errors";
import { queryKeys, usePreferences, useProfile } from "@/lib/api/hooks";
import type { Preferences, Profile } from "@/lib/api/types";
import { updateCurrentUser } from "@/lib/auth/session";
import { countryName } from "@/lib/countries";
import {
  CAREER_STAGE_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  MISSING_FIELD_LABELS,
  REMOTE_LABELS,
} from "@/lib/labels";
import { cn } from "@/lib/utils";
import { preferencesToValues, profileToValues } from "@/lib/validation/schemas";

const STEPS = ["About you", "Career preferences", "Review"] as const;

export function OnboardingWizard() {
  const profile = useProfile();
  const prefs = usePreferences();
  if (profile.isPending || prefs.isPending) return <PageLoading label="Loading your profile" />;
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

  // Resume at the first incomplete step.
  const p = profile.data;
  const initialStep = !p.countryCode || !p.careerStage ? 0 : prefs.data.desiredRoles.length === 0 ? 1 : 2;
  return <WizardBody profile={p} prefs={prefs.data} initialStep={initialStep} />;
}

function WizardBody({
  profile,
  prefs,
  initialStep,
}: {
  profile: Profile;
  prefs: Preferences;
  initialStep: number;
}) {
  const [step, setStep] = useState(initialStep);
  return (
    <div className="animate-fade-in">
      <h1 className="text-2xl font-semibold tracking-tight">
        Welcome{profile.fullName ? `, ${profile.fullName.split(" ")[0]}` : ""}! Let&apos;s set up your profile.
      </h1>
      <p className="mt-1 text-sm text-muted-foreground">
        This helps us match you with suitable entry-level roles. You can change everything later.
      </p>

      <ol className="my-8 flex items-center gap-2" aria-label="Onboarding progress">
        {STEPS.map((label, i) => (
          <li key={label} className="flex flex-1 items-center gap-2" aria-current={i === step ? "step" : undefined}>
            <span
              className={cn(
                "grid size-7 shrink-0 place-items-center rounded-full border text-xs font-semibold transition-colors",
                i < step && "border-primary bg-primary text-primary-foreground",
                i === step && "border-primary text-primary",
                i > step && "text-muted-foreground",
              )}
            >
              {i < step ? <Check className="size-4" /> : i + 1}
            </span>
            <span className={cn("hidden text-sm sm:inline", i === step ? "font-medium" : "text-muted-foreground")}>
              {label}
            </span>
            {i < STEPS.length - 1 && <span className="h-px flex-1 bg-border" aria-hidden />}
          </li>
        ))}
      </ol>

      <Card>
        {step === 0 && (
          <>
            <CardHeader>
              <CardTitle>About you</CardTitle>
              <CardDescription>Country and current stage are required.</CardDescription>
            </CardHeader>
            <CardContent>
              <ProfileForm
                mode="onboarding"
                initial={profileToValues(profile)}
                submitLabel="Save and continue"
                onSaved={() => setStep(1)}
              />
            </CardContent>
          </>
        )}
        {step === 1 && (
          <>
            <CardHeader>
              <CardTitle>Career preferences</CardTitle>
              <CardDescription>Tell us what kind of work you&apos;re looking for.</CardDescription>
            </CardHeader>
            <CardContent>
              <PreferencesForm
                mode="onboarding"
                initial={preferencesToValues(prefs)}
                submitLabel="Save and continue"
                onSaved={() => setStep(2)}
                secondaryAction={
                  <Button type="button" variant="ghost" onClick={() => setStep(0)}>
                    Back
                  </Button>
                }
              />
            </CardContent>
          </>
        )}
        {step === 2 && (
          <ReviewStep profile={profile} prefs={prefs} onEdit={setStep} />
        )}
      </Card>
    </div>
  );
}

function ReviewStep({
  profile,
  prefs,
  onEdit,
}: {
  profile: Profile;
  prefs: Preferences;
  onEdit: (step: number) => void;
}) {
  const router = useRouter();
  const qc = useQueryClient();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function finish() {
    setSubmitting(true);
    setError(null);
    try {
      await onboardingApi.complete();
      updateCurrentUser({ onboardingCompleted: true });
      await qc.invalidateQueries({ queryKey: queryKeys.onboarding });
      router.replace("/dashboard");
    } catch (err) {
      const missing =
        err instanceof ApiError && Array.isArray(err.extra.missingRequired)
          ? (err.extra.missingRequired as string[])
          : [];
      setError(
        missing.length
          ? `Still missing: ${missing.map((m) => MISSING_FIELD_LABELS[m] ?? m).join(", ")}.`
          : userMessage(err),
      );
      setSubmitting(false);
    }
  }

  const rows: [string, string, number][] = [
    ["Name", profile.fullName, 0],
    ["Current stage", profile.careerStage ? CAREER_STAGE_LABELS[profile.careerStage] : "—", 0],
    ["Location", [profile.city, countryName(profile.countryCode)].filter(Boolean).join(", ") || "—", 0],
    ["Desired roles", prefs.desiredRoles.join(", ") || "—", 1],
    ["Employment types", prefs.employmentTypes.map((t) => EMPLOYMENT_TYPE_LABELS[t]).join(", ") || "—", 1],
    ["Work arrangement", prefs.remotePreference ? REMOTE_LABELS[prefs.remotePreference] : "—", 1],
  ];

  return (
    <>
      <CardHeader>
        <CardTitle>Review</CardTitle>
        <CardDescription>Check your details before finishing.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <FormAlert message={error} />
        <dl className="divide-y rounded-lg border">
          {rows.map(([label, value, step]) => (
            <div key={label} className="flex items-start justify-between gap-4 px-4 py-3 text-sm">
              <dt className="w-40 shrink-0 text-muted-foreground">{label}</dt>
              <dd className="flex-1 break-words">{value}</dd>
              <button
                type="button"
                className="text-xs font-medium text-primary hover:underline"
                onClick={() => onEdit(step)}
                aria-label={`Edit ${label}`}
              >
                Edit
              </button>
            </div>
          ))}
        </dl>
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="ghost" onClick={() => onEdit(1)}>
            Back
          </Button>
          <Button onClick={() => void finish()} disabled={submitting}>
            {submitting && <Loader2 className="animate-spin" />}
            Finish setup
          </Button>
        </div>
      </CardContent>
    </>
  );
}
