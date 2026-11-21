"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { homeFor } from "@/components/auth/require-auth";
import { useSession } from "@/lib/auth/use-session";

/** Redirects signed-in users away from login/register. */
export function GuestOnly({ children }: { children: React.ReactNode }) {
  const { status, user } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (status === "authenticated" && user) {
      router.replace(homeFor(user));
    }
  }, [status, user, router]);

  return <>{children}</>;
}

/** Only allows same-origin relative paths as post-login redirect targets (no open redirects). */
export function safeNextPath(next: string | null): string | null {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) {
    return null;
  }
  return next;
}
