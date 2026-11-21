"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { Controller, useForm, useWatch, type Resolver } from "react-hook-form";

import { NativeSelect } from "@/components/forms/native-select";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { userMessage } from "@/lib/api/errors";
import { useUpdateProfile } from "@/lib/api/hooks";
import { CAREER_STAGES, type Profile } from "@/lib/api/types";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import { applyServerFieldErrors } from "@/lib/forms";
import { CAREER_STAGE_LABELS } from "@/lib/labels";
import {
  onboardingProfileSchema,
  profileSchema,
  profileToPayload,
  type ProfileValues,
} from "@/lib/validation/schemas";

import { FormAlert } from "./form-alert";

const FIELDS = Object.keys(profileSchema.shape);

interface ProfileFormProps {
  initial: ProfileValues;
  /** Onboarding mode enforces the fields required to finish onboarding. */
  mode?: "edit" | "onboarding";
  submitLabel?: string;
  onSaved?: (profile: Profile) => void;
  secondaryAction?: React.ReactNode;
}

export function ProfileForm({
  initial,
  mode = "edit",
  submitLabel = "Save changes",
  onSaved,
  secondaryAction,
}: ProfileFormProps) {
  const schema = mode === "onboarding" ? onboardingProfileSchema : profileSchema;
  const form = useForm<ProfileValues>({
    resolver: zodResolver(schema) as unknown as Resolver<ProfileValues>,
    defaultValues: initial,
    mode: "onTouched",
  });
  const mutation = useUpdateProfile();
  const [formError, setFormError] = useState<string | null>(null);
  const { errors } = form.formState;

  async function onSubmit(values: ProfileValues) {
    setFormError(null);
    try {
      const saved = await mutation.mutateAsync(profileToPayload(values));
      form.reset(values);
      onSaved?.(saved);
    } catch (err) {
      if (!applyServerFieldErrors(err, form.setError, FIELDS)) setFormError(userMessage(err));
    }
  }

  const bio = useWatch({ control: form.control, name: "bio" });

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="space-y-6">
      <FormAlert message={formError} />
      <FieldGroup>
        <Field data-invalid={!!errors.fullName || undefined}>
          <FieldLabel htmlFor="fullName">Full name</FieldLabel>
          <Input id="fullName" autoComplete="name" aria-invalid={!!errors.fullName} {...form.register("fullName")} />
          <FieldError errors={[errors.fullName]} />
        </Field>

        <Field data-invalid={!!errors.headline || undefined}>
          <FieldLabel htmlFor="headline">Headline</FieldLabel>
          <Input
            id="headline"
            placeholder="e.g. Computer Science student focused on backend development"
            aria-invalid={!!errors.headline}
            {...form.register("headline")}
          />
          <FieldError errors={[errors.headline]} />
        </Field>

        <div className="grid gap-6 sm:grid-cols-2">
          <Field data-invalid={!!errors.careerStage || undefined}>
            <FieldLabel htmlFor="careerStage">Current stage</FieldLabel>
            <NativeSelect id="careerStage" aria-invalid={!!errors.careerStage} {...form.register("careerStage")}>
              <option value="">Select…</option>
              {CAREER_STAGES.map((s) => (
                <option key={s} value={s}>
                  {CAREER_STAGE_LABELS[s]}
                </option>
              ))}
            </NativeSelect>
            <FieldError errors={[errors.careerStage]} />
          </Field>
          <Field data-invalid={!!errors.graduationYear || undefined}>
            <FieldLabel htmlFor="graduationYear">Graduation year</FieldLabel>
            <Input
              id="graduationYear"
              inputMode="numeric"
              placeholder="e.g. 2026"
              maxLength={4}
              aria-invalid={!!errors.graduationYear}
              {...form.register("graduationYear")}
            />
            <FieldDescription>Expected or completed.</FieldDescription>
            <FieldError errors={[errors.graduationYear]} />
          </Field>
        </div>

        <div className="grid gap-6 sm:grid-cols-2">
          <Field data-invalid={!!errors.countryCode || undefined}>
            <FieldLabel htmlFor="countryCode">Country</FieldLabel>
            <NativeSelect id="countryCode" autoComplete="country" aria-invalid={!!errors.countryCode} {...form.register("countryCode")}>
              <option value="">Select…</option>
              {COUNTRY_OPTIONS.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.name}
                </option>
              ))}
            </NativeSelect>
            <FieldError errors={[errors.countryCode]} />
          </Field>
          <Field data-invalid={!!errors.city || undefined}>
            <FieldLabel htmlFor="city">City</FieldLabel>
            <Input id="city" autoComplete="address-level2" aria-invalid={!!errors.city} {...form.register("city")} />
            <FieldError errors={[errors.city]} />
          </Field>
        </div>

        <Field data-invalid={!!errors.bio || undefined}>
          <FieldLabel htmlFor="bio">About you</FieldLabel>
          <Textarea
            id="bio"
            rows={5}
            maxLength={2000}
            placeholder="Your interests, strengths and the kind of work you're looking for."
            aria-invalid={!!errors.bio}
            {...form.register("bio")}
          />
          <FieldDescription>{bio.length}/2000</FieldDescription>
          <FieldError errors={[errors.bio]} />
        </Field>

        <div className="grid gap-6 sm:grid-cols-3">
          {(
            [
              ["linkedinUrl", "LinkedIn", "https://www.linkedin.com/in/…"],
              ["githubUrl", "GitHub", "https://github.com/…"],
              ["portfolioUrl", "Portfolio", "https://…"],
            ] as const
          ).map(([name, label, placeholder]) => (
            <Controller
              key={name}
              control={form.control}
              name={name}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid || undefined}>
                  <FieldLabel htmlFor={name}>{label}</FieldLabel>
                  <Input id={name} type="url" inputMode="url" placeholder={placeholder} aria-invalid={fieldState.invalid} {...field} />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          ))}
        </div>
      </FieldGroup>

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        {secondaryAction}
        <Button type="submit" disabled={form.formState.isSubmitting || (mode === "edit" && !form.formState.isDirty)}>
          {form.formState.isSubmitting && <Loader2 className="animate-spin" />}
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
