"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { Controller, useForm, type Resolver } from "react-hook-form";

import { NativeSelect } from "@/components/forms/native-select";
import { TagInput } from "@/components/forms/tag-input";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { userMessage } from "@/lib/api/errors";
import { useUpdatePreferences } from "@/lib/api/hooks";
import { EMPLOYMENT_TYPES, REMOTE_PREFERENCES, type Preferences } from "@/lib/api/types";
import { CURRENCY_CODES } from "@/lib/countries";
import { applyServerFieldErrors } from "@/lib/forms";
import { EMPLOYMENT_TYPE_LABELS, REMOTE_LABELS, ROLE_SUGGESTIONS } from "@/lib/labels";
import {
  onboardingPreferencesSchema,
  preferencesSchema,
  preferencesToPayload,
  type PreferencesValues,
} from "@/lib/validation/schemas";

import { FormAlert } from "./form-alert";

const FIELDS = Object.keys(preferencesSchema.shape);

interface PreferencesFormProps {
  initial: PreferencesValues;
  mode?: "edit" | "onboarding";
  submitLabel?: string;
  onSaved?: (prefs: Preferences) => void;
  secondaryAction?: React.ReactNode;
}

export function PreferencesForm({
  initial,
  mode = "edit",
  submitLabel = "Save preferences",
  onSaved,
  secondaryAction,
}: PreferencesFormProps) {
  const schema = mode === "onboarding" ? onboardingPreferencesSchema : preferencesSchema;
  const form = useForm<PreferencesValues>({
    resolver: zodResolver(schema) as unknown as Resolver<PreferencesValues>,
    defaultValues: initial,
    mode: "onTouched",
  });
  const mutation = useUpdatePreferences();
  const [formError, setFormError] = useState<string | null>(null);
  const { errors } = form.formState;
  const currencies = Array.from(new Set([...CURRENCY_CODES, initial.salaryCurrency].filter(Boolean)));

  async function onSubmit(values: PreferencesValues) {
    setFormError(null);
    try {
      const saved = await mutation.mutateAsync(preferencesToPayload(values));
      form.reset(values);
      onSaved?.(saved);
    } catch (err) {
      if (!applyServerFieldErrors(err, form.setError, FIELDS)) setFormError(userMessage(err));
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="space-y-6">
      <FormAlert message={formError} />
      <FieldGroup>
        <Controller
          control={form.control}
          name="desiredRoles"
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid || undefined}>
              <FieldLabel htmlFor="desiredRoles">Roles you&apos;re interested in</FieldLabel>
              <TagInput
                id="desiredRoles"
                value={field.value}
                onChange={field.onChange}
                placeholder="Type a role and press Enter"
                suggestions={ROLE_SUGGESTIONS}
                invalid={fieldState.invalid}
              />
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />

        <Controller
          control={form.control}
          name="employmentTypes"
          render={({ field, fieldState }) => (
            <FieldSet data-invalid={fieldState.invalid || undefined}>
              <FieldLegend variant="label">Employment types</FieldLegend>
              <div className="grid gap-2 sm:grid-cols-4">
                {EMPLOYMENT_TYPES.map((t) => {
                  const checked = field.value.includes(t);
                  return (
                    <label
                      key={t}
                      className="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2.5 text-sm transition-colors has-data-[state=checked]:border-primary has-data-[state=checked]:bg-accent"
                    >
                      <Checkbox
                        checked={checked}
                        onCheckedChange={(c) =>
                          field.onChange(c ? [...field.value, t] : field.value.filter((v) => v !== t))
                        }
                      />
                      {EMPLOYMENT_TYPE_LABELS[t]}
                    </label>
                  );
                })}
              </div>
              <FieldError errors={[fieldState.error]} />
            </FieldSet>
          )}
        />

        <Controller
          control={form.control}
          name="remotePreference"
          render={({ field, fieldState }) => (
            <FieldSet data-invalid={fieldState.invalid || undefined}>
              <FieldLegend variant="label">Work arrangement</FieldLegend>
              <RadioGroup
                value={field.value}
                onValueChange={field.onChange}
                className="grid gap-2 sm:grid-cols-4"
              >
                {REMOTE_PREFERENCES.map((r) => (
                  <label
                    key={r}
                    className="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2.5 text-sm transition-colors has-data-[state=checked]:border-primary has-data-[state=checked]:bg-accent"
                  >
                    <RadioGroupItem value={r} />
                    {REMOTE_LABELS[r]}
                  </label>
                ))}
              </RadioGroup>
              <FieldError errors={[fieldState.error]} />
            </FieldSet>
          )}
        />

        <Controller
          control={form.control}
          name="preferredLocations"
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid || undefined}>
              <FieldLabel htmlFor="preferredLocations">Preferred locations</FieldLabel>
              <TagInput
                id="preferredLocations"
                value={field.value}
                onChange={field.onChange}
                maxLength={100}
                placeholder="e.g. Phnom Penh, Singapore"
                invalid={fieldState.invalid}
              />
              <FieldDescription>Optional. Leave empty if location doesn&apos;t matter.</FieldDescription>
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />

        <Controller
          control={form.control}
          name="willingToRelocate"
          render={({ field }) => (
            <label className="flex cursor-pointer items-center gap-2 text-sm">
              <Checkbox checked={field.value} onCheckedChange={(c) => field.onChange(c === true)} />
              I&apos;m willing to relocate for the right role
            </label>
          )}
        />

        <div className="grid gap-6 sm:grid-cols-3">
          <Field data-invalid={!!errors.expectedMinSalary || undefined}>
            <FieldLabel htmlFor="expectedMinSalary">Minimum monthly salary</FieldLabel>
            <Input
              id="expectedMinSalary"
              inputMode="numeric"
              placeholder="Optional"
              aria-invalid={!!errors.expectedMinSalary}
              {...form.register("expectedMinSalary")}
            />
            <FieldError errors={[errors.expectedMinSalary]} />
          </Field>
          <Field data-invalid={!!errors.salaryCurrency || undefined}>
            <FieldLabel htmlFor="salaryCurrency">Currency</FieldLabel>
            <NativeSelect id="salaryCurrency" aria-invalid={!!errors.salaryCurrency} {...form.register("salaryCurrency")}>
              <option value="">—</option>
              {currencies.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </NativeSelect>
            <FieldError errors={[errors.salaryCurrency]} />
          </Field>
          <Field data-invalid={!!errors.availableFrom || undefined}>
            <FieldLabel htmlFor="availableFrom">Available from</FieldLabel>
            <Input id="availableFrom" type="date" aria-invalid={!!errors.availableFrom} {...form.register("availableFrom")} />
            <FieldError errors={[errors.availableFrom]} />
          </Field>
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
