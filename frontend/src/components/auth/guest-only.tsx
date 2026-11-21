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

const BASE = "https://same-origin.invalid";

/**
 * Only allows same-origin relative paths as post-login redirect targets (no open redirects).
 * The URL parser strips tabs/newlines and treats "\\" like "/", so the path is resolved against a
 * fixed origin and must stay on it; whitespace and control characters are rejected outright.
 */
export function safeNextPath(next: string | null): string | null {
  if (!next || !next.startsWith("/") || /[\s\u0000-\u001f\u007f\\\\]/.test(next)) {
    return null;
  }
  try {
    const url = new URL(next, BASE);
    if (url.origin !== BASE) return null;
    return url.pathname + url.search + url.hash;
  } catch {
    return null;
  }
}
