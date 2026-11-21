import type { Metadata } from "next";

import { RegisterForm } from "./register-form";

export const metadata: Metadata = { title: "Create account" };

export default function RegisterPage() {
  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Create your account</h1>
      <p className="mt-1 mb-8 text-sm text-muted-foreground">
        It takes a minute. You&apos;ll set up your profile next.
      </p>
      <RegisterForm />
    </>
  );
}
