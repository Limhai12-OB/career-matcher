"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { safeNextPath } from "@/components/auth/guest-only";
import { homeFor, routeFor } from "@/components/auth/require-auth";
import { FormAlert } from "@/components/forms/form-alert";
import { PasswordInput } from "@/components/forms/password-input";
import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { userMessage } from "@/lib/api/errors";
import { login } from "@/lib/auth/session";
import { loginSchema, type LoginValues } from "@/lib/validation/schemas";

export function LoginForm() {
  const router = useRouter();
  const next = safeNextPath(useSearchParams().get("next"));
  const [formError, setFormError] = useState<string | null>(null);
  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });
  const { errors, isSubmitting } = form.formState;

  async function onSubmit(values: LoginValues) {
    setFormError(null);
    try {
      const user = await login(values.email, values.password);
      // Honour ?next= only if this user may actually open that page.
      const target = next && !routeFor(user.role, user.onboardingCompleted, next, "required") ? next : null;
      router.replace(target ?? homeFor(user));
    } catch (err) {
      setFormError(userMessage(err));
      form.resetField("password");
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="space-y-6">
      <FormAlert message={formError} />
      <FieldGroup>
        <Field data-invalid={!!errors.email || undefined}>
          <FieldLabel htmlFor="email">Email</FieldLabel>
          <Input id="email" type="email" autoComplete="email" autoFocus aria-invalid={!!errors.email} {...form.register("email")} />
          <FieldError errors={[errors.email]} />
        </Field>
        <Field data-invalid={!!errors.password || undefined}>
          <FieldLabel htmlFor="password">Password</FieldLabel>
          <PasswordInput id="password" autoComplete="current-password" aria-invalid={!!errors.password} {...form.register("password")} />
          <FieldError errors={[errors.password]} />
        </Field>
      </FieldGroup>
      <Button type="submit" className="w-full" disabled={isSubmitting}>
        {isSubmitting && <Loader2 className="animate-spin" />}
        Sign in
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        New here?{" "}
        <Link href="/register" className="font-medium text-primary hover:underline">
          Create an account
        </Link>
      </p>
    </form>
  );
}
