"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { Controller, useForm, useWatch, type Resolver } from "react-hook-form";

import { SkillPicker } from "@/components/admin/skill-picker";
import { FormAlert } from "@/components/forms/form-alert";
import { NativeSelect } from "@/components/forms/native-select";
import { TagInput } from "@/components/forms/tag-input";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";
import { userMessage } from "@/lib/api/errors";
import { EDUCATION_LEVELS, EMPLOYMENT_TYPES, JOB_REMOTE_TYPES, JOB_SENIORITIES } from "@/lib/api/types";
import { COUNTRY_OPTIONS, CURRENCY_CODES } from "@/lib/countries";
import { applyServerFieldErrors } from "@/lib/forms";
import { EDUCATION_LABELS, REMOTE_TYPE_LABELS, SENIORITY_LABELS } from "@/lib/jobs";
import { EMPLOYMENT_TYPE_LABELS } from "@/lib/labels";
import { jobFormSchema, type JobFormValues } from "@/lib/validation/job-schema";

const FIELDS = Object.keys(jobFormSchema.shape) as string[];

export function JobForm({
  initial,
  submitLabel,
  onSubmit,
}: {
  initial: JobFormValues;
  submitLabel: string;
  onSubmit: (values: JobFormValues) => Promise<void>;
}) {
  const form = useForm<JobFormValues>({
    resolver: zodResolver(jobFormSchema) as unknown as Resolver<JobFormValues>,
    defaultValues: initial,
    mode: "onTouched",
  });
  const [formError, setFormError] = useState<string | null>(null);
  const { errors, isSubmitting } = form.formState;
  const origin = useWatch({ control: form.control, name: "dataOrigin" });

  async function submit(values: JobFormValues) {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (err) {
      if (!applyServerFieldErrors(err, form.setError, FIELDS)) setFormError(userMessage(err));
    }
  }

  const text = (name: keyof JobFormValues, label: string, props: React.ComponentProps<typeof Input> = {}, help?: string) => (
    <Field data-invalid={!!errors[name] || undefined}>
      <FieldLabel htmlFor={name}>{label}</FieldLabel>
      <Input id={name} aria-invalid={!!errors[name]} {...props} {...form.register(name)} />
      {help && <FieldDescription>{help}</FieldDescription>}
      <FieldError errors={[errors[name] as { message?: string } | undefined]} />
    </Field>
  );

  return (
    <form onSubmit={form.handleSubmit(submit)} noValidate className="space-y-6">
      <FormAlert message={formError} />

      <Card>
        <CardHeader>
          <CardTitle>Listing type and source</CardTitle>
          <CardDescription>Every listing must say where it comes from and where candidates apply.</CardDescription>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <Controller
              control={form.control}
              name="dataOrigin"
              render={({ field, fieldState }) => (
                <FieldSet data-invalid={fieldState.invalid || undefined}>
                  <FieldLegend variant="label">Is this a real opportunity?</FieldLegend>
                  <RadioGroup value={field.value} onValueChange={field.onChange} className="grid gap-2 sm:grid-cols-2">
                    <label className="flex cursor-pointer items-start gap-2 rounded-lg border p-3 text-sm has-data-[state=checked]:border-primary has-data-[state=checked]:bg-accent">
                      <RadioGroupItem value="REAL" className="mt-0.5" />
                      <span>
                        <span className="font-medium">Real opportunity</span>
                        <span className="block text-muted-foreground">A verified, currently open position at a real employer.</span>
                      </span>
                    </label>
                    <label className="flex cursor-pointer items-start gap-2 rounded-lg border p-3 text-sm has-data-[state=checked]:border-primary has-data-[state=checked]:bg-accent">
                      <RadioGroupItem value="DEMO" className="mt-0.5" />
                      <span>
                        <span className="font-medium">Demo listing</span>
                        <span className="block text-muted-foreground">Fictional sample data. Links must use example.com.</span>
                      </span>
                    </label>
                  </RadioGroup>
                  <FieldError errors={[fieldState.error]} />
                </FieldSet>
              )}
            />
            <div className="grid gap-6 sm:grid-cols-2">
              {text("applicationUrl", "Application URL", {
                type: "url",
                placeholder: origin === "DEMO" ? "https://careers.example.com/demo/…" : "https://careers.employer.com/jobs/…",
              })}
              {text("sourceName", "Source", { placeholder: "e.g. Employer careers page" })}
            </div>
            {text("sourceUrl", "Source URL (optional)", { type: "url" }, "The page where this listing was published.")}
          </FieldGroup>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Role</CardTitle>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <div className="grid gap-6 sm:grid-cols-2">
              {text("title", "Job title")}
              {text("companyName", "Company")}
            </div>
            {text("companyWebsite", "Company website (optional)", { type: "url" })}
            <Field data-invalid={!!errors.description || undefined}>
              <FieldLabel htmlFor="description">Description</FieldLabel>
              <Textarea id="description" rows={10} aria-invalid={!!errors.description} {...form.register("description")} />
              <FieldDescription>Plain text. Use blank lines and “- ” for lists.</FieldDescription>
              <FieldError errors={[errors.description]} />
            </Field>
            <div className="grid gap-6 sm:grid-cols-3">
              <Field>
                <FieldLabel htmlFor="employmentType">Employment type</FieldLabel>
                <NativeSelect id="employmentType" {...form.register("employmentType")}>
                  {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{EMPLOYMENT_TYPE_LABELS[t]}</option>)}
                </NativeSelect>
              </Field>
              <Field>
                <FieldLabel htmlFor="seniority">Seniority</FieldLabel>
                <NativeSelect id="seniority" {...form.register("seniority")}>
                  {JOB_SENIORITIES.map((s) => <option key={s} value={s}>{SENIORITY_LABELS[s]}</option>)}
                </NativeSelect>
              </Field>
              <Field>
                <FieldLabel htmlFor="remoteType">Work arrangement</FieldLabel>
                <NativeSelect id="remoteType" {...form.register("remoteType")}>
                  {JOB_REMOTE_TYPES.map((r) => <option key={r} value={r}>{REMOTE_TYPE_LABELS[r]}</option>)}
                </NativeSelect>
              </Field>
            </div>
            <div className="grid gap-6 sm:grid-cols-2">
              <Field data-invalid={!!errors.countryCode || undefined}>
                <FieldLabel htmlFor="countryCode">Country</FieldLabel>
                <NativeSelect id="countryCode" {...form.register("countryCode")}>
                  <option value="">Not specified</option>
                  {COUNTRY_OPTIONS.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
                </NativeSelect>
                <FieldError errors={[errors.countryCode]} />
              </Field>
              {text("city", "City")}
            </div>
          </FieldGroup>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Requirements</CardTitle>
          <CardDescription>Required skills weigh most in matching; preferred skills add to the score.</CardDescription>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <Controller
              control={form.control}
              name="skills"
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid || undefined}>
                  <FieldLabel>Skills</FieldLabel>
                  <SkillPicker value={field.value} onChange={field.onChange} invalid={fieldState.invalid} />
                  <FieldDescription>Click Required/Preferred to toggle.</FieldDescription>
                  <FieldError errors={[fieldState.error, (fieldState.error as { root?: { message?: string } } | undefined)?.root]} />
                </Field>
              )}
            />
            <div className="grid gap-6 sm:grid-cols-2">
              {text("minExperienceMonths", "Minimum experience (months)", { inputMode: "numeric" })}
              {text("maxExperienceMonths", "Maximum experience (months, optional)", { inputMode: "numeric" })}
            </div>
            <div className="grid gap-6 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="educationLevel">Minimum education</FieldLabel>
                <NativeSelect id="educationLevel" {...form.register("educationLevel")}>
                  <option value="">Not required</option>
                  {EDUCATION_LEVELS.map((e) => <option key={e} value={e}>{EDUCATION_LABELS[e]}</option>)}
                </NativeSelect>
              </Field>
              <Controller
                control={form.control}
                name="educationFields"
                render={({ field }) => (
                  <Field>
                    <FieldLabel htmlFor="educationFields">Fields of study (optional)</FieldLabel>
                    <TagInput id="educationFields" value={field.value} onChange={field.onChange} placeholder="e.g. Computer Science" />
                  </Field>
                )}
              />
            </div>
          </FieldGroup>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Salary and dates</CardTitle>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <div className="grid gap-6 sm:grid-cols-4">
              {text("salaryMin", "Salary from", { inputMode: "numeric" })}
              {text("salaryMax", "Salary to", { inputMode: "numeric" })}
              <Field data-invalid={!!errors.salaryCurrency || undefined}>
                <FieldLabel htmlFor="salaryCurrency">Currency</FieldLabel>
                <NativeSelect id="salaryCurrency" {...form.register("salaryCurrency")}>
                  <option value="">—</option>
                  {CURRENCY_CODES.map((c) => <option key={c} value={c}>{c}</option>)}
                </NativeSelect>
                <FieldError errors={[errors.salaryCurrency]} />
              </Field>
              <Field>
                <FieldLabel htmlFor="salaryPeriod">Per</FieldLabel>
                <NativeSelect id="salaryPeriod" {...form.register("salaryPeriod")}>
                  <option value="">—</option>
                  <option value="MONTH">Month</option>
                  <option value="YEAR">Year</option>
                </NativeSelect>
              </Field>
            </div>
            <div className="grid gap-6 sm:grid-cols-2">
              {text("postedOn", "Posted on", { type: "date" })}
              {text("expiresOn", "Closes on (optional)", { type: "date" })}
            </div>
          </FieldGroup>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <Loader2 className="animate-spin" />}
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
