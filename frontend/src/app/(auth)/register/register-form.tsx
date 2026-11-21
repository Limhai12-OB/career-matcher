"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";

import { FormAlert } from "@/components/forms/form-alert";
import { PasswordInput, PasswordRequirements } from "@/components/forms/password-input";
import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ApiError, userMessage } from "@/lib/api/errors";
import { register as registerAccount } from "@/lib/auth/session";
import { applyServerFieldErrors } from "@/lib/forms";
import { registerSchema, type RegisterValues } from "@/lib/validation/schemas";

export function RegisterForm() {
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);
  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", password: "", confirmPassword: "" },
    mode: "onTouched",
  });
  const { errors, isSubmitting } = form.formState;
  const password = useWatch({ control: form.control, name: "password" });

  async function onSubmit(values: RegisterValues) {
    setFormError(null);
    try {
      await registerAccount(values.email, values.password, values.fullName);
      router.replace("/onboarding");
    } catch (err) {
      if (err instanceof ApiError && err.code === "EMAIL_ALREADY_REGISTERED") {
        form.setError("email", { message: "An account with this email already exists." });
      } else if (!applyServerFieldErrors(err, form.setError, ["fullName", "email", "password"])) {
        setFormError(userMessage(err));
      }
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="space-y-6">
      <FormAlert message={formError} />
      <FieldGroup>
        <Field data-invalid={!!errors.fullName || undefined}>
          <FieldLabel htmlFor="fullName">Full name</FieldLabel>
          <Input id="fullName" autoComplete="name" autoFocus aria-invalid={!!errors.fullName} {...form.register("fullName")} />
          <FieldError errors={[errors.fullName]} />
        </Field>
        <Field data-invalid={!!errors.email || undefined}>
          <FieldLabel htmlFor="email">Email</FieldLabel>
          <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...form.register("email")} />
          <FieldError errors={[errors.email]} />
          {errors.email?.message?.includes("already exists") && (
            <p className="text-sm">
              <Link href="/login" className="font-medium text-primary hover:underline">
                Sign in instead
              </Link>
            </p>
          )}
        </Field>
        <Field data-invalid={!!errors.password || undefined}>
          <FieldLabel htmlFor="password">Password</FieldLabel>
          <PasswordInput id="password" autoComplete="new-password" aria-invalid={!!errors.password} {...form.register("password")} />
          <PasswordRequirements value={password} />
          <FieldError errors={[errors.password]} />
        </Field>
        <Field data-invalid={!!errors.confirmPassword || undefined}>
          <FieldLabel htmlFor="confirmPassword">Confirm password</FieldLabel>
          <PasswordInput
            id="confirmPassword"
            autoComplete="new-password"
            aria-invalid={!!errors.confirmPassword}
            {...form.register("confirmPassword")}
          />
          <FieldError errors={[errors.confirmPassword]} />
        </Field>
      </FieldGroup>
      <Button type="submit" className="w-full" disabled={isSubmitting}>
        {isSubmitting && <Loader2 className="animate-spin" />}
        Create account
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        Already registered?{" "}
        <Link href="/login" className="font-medium text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </form>
  );
}
