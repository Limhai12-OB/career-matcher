"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";

import { FormAlert } from "@/components/forms/form-alert";
import { PasswordInput, PasswordRequirements } from "@/components/forms/password-input";
import { PageHeader } from "@/components/shell/app-shell";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { accountApi } from "@/lib/api/endpoints";
import { userMessage } from "@/lib/api/errors";
import { endSessionLocally, replaceSession } from "@/lib/auth/session";
import { useSession } from "@/lib/auth/use-session";
import { applyServerFieldErrors } from "@/lib/forms";
import { changePasswordSchema, type ChangePasswordValues } from "@/lib/validation/schemas";

export function SettingsView() {
  const { user } = useSession();
  return (
    <div className="animate-fade-in space-y-6">
      <PageHeader title="Settings" description="Manage your account and security." />
      <Card>
        <CardHeader>
          <CardTitle>Account</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">Email</dt>
              <dd className="mt-1 font-medium break-all">{user?.email}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Member since</dt>
              <dd className="mt-1 font-medium">
                {user ? new Date(user.createdAt).toLocaleDateString(undefined, { dateStyle: "long" }) : "—"}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
      <ChangePasswordCard />
      <DeleteAccountCard />
    </div>
  );
}

function ChangePasswordCard() {
  const [formError, setFormError] = useState<string | null>(null);
  const form = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
    mode: "onTouched",
  });
  const { errors, isSubmitting } = form.formState;
  const newPassword = useWatch({ control: form.control, name: "newPassword" });

  async function onSubmit(v: ChangePasswordValues) {
    setFormError(null);
    try {
      const auth = await accountApi.changePassword(v.currentPassword, v.newPassword);
      replaceSession(auth);
      form.reset();
      toast.success("Password changed. Other devices have been signed out.");
    } catch (err) {
      if (!applyServerFieldErrors(err, form.setError, ["currentPassword", "newPassword"])) {
        setFormError(userMessage(err));
      }
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Change password</CardTitle>
        <CardDescription>Changing your password signs you out on all other devices.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="max-w-md space-y-6">
          <FormAlert message={formError} />
          <FieldGroup>
            <Field data-invalid={!!errors.currentPassword || undefined}>
              <FieldLabel htmlFor="currentPassword">Current password</FieldLabel>
              <PasswordInput id="currentPassword" autoComplete="current-password" aria-invalid={!!errors.currentPassword} {...form.register("currentPassword")} />
              <FieldError errors={[errors.currentPassword]} />
            </Field>
            <Field data-invalid={!!errors.newPassword || undefined}>
              <FieldLabel htmlFor="newPassword">New password</FieldLabel>
              <PasswordInput id="newPassword" autoComplete="new-password" aria-invalid={!!errors.newPassword} {...form.register("newPassword")} />
              <PasswordRequirements value={newPassword} />
              <FieldError errors={[errors.newPassword]} />
            </Field>
            <Field data-invalid={!!errors.confirmPassword || undefined}>
              <FieldLabel htmlFor="confirmPassword">Confirm new password</FieldLabel>
              <PasswordInput id="confirmPassword" autoComplete="new-password" aria-invalid={!!errors.confirmPassword} {...form.register("confirmPassword")} />
              <FieldError errors={[errors.confirmPassword]} />
            </Field>
          </FieldGroup>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting && <Loader2 className="animate-spin" />}
            Update password
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function DeleteAccountCard() {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function confirmDelete() {
    setPending(true);
    setError(null);
    try {
      await accountApi.deleteAccount(password);
      qc.clear();
      endSessionLocally("/"); // RequireAuth navigates to the landing page
      toast.success("Your account and data have been deleted.");
    } catch (err) {
      setError(userMessage(err));
      setPending(false);
    }
  }

  return (
    <Card className="border-destructive/30">
      <CardHeader>
        <CardTitle className="text-destructive">Delete account</CardTitle>
        <CardDescription>
          Permanently deletes your account, profile and preferences. This cannot be undone.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <AlertDialog
          open={open}
          onOpenChange={(o) => {
            setOpen(o);
            if (!o) {
              setPassword("");
              setError(null);
            }
          }}
        >
          <AlertDialogTrigger asChild>
            <Button variant="destructive">Delete my account</Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (password) void confirmDelete();
              }}
              className="space-y-4"
            >
              <AlertDialogHeader>
                <AlertDialogTitle>Delete your account?</AlertDialogTitle>
                <AlertDialogDescription>
                  All of your data will be permanently removed. Enter your password to confirm.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <FormAlert message={error} />
              <Field>
                <FieldLabel htmlFor="deletePassword">Password</FieldLabel>
                <PasswordInput
                  id="deletePassword"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </Field>
              <AlertDialogFooter>
                <AlertDialogCancel type="button" disabled={pending}>
                  Cancel
                </AlertDialogCancel>
                <Button type="submit" variant="destructive" disabled={!password || pending}>
                  {pending && <Loader2 className="animate-spin" />}
                  Delete permanently
                </Button>
              </AlertDialogFooter>
            </form>
          </AlertDialogContent>
        </AlertDialog>
      </CardContent>
    </Card>
  );
}
