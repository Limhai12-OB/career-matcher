"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

import { FullScreenLoading } from "@/components/states/states";
import { useSession } from "@/lib/auth/use-session";

/**
 * Client-side route guard. This is a UX convenience only: every API endpoint enforces
 * authentication and ownership on the server.
 *
 * @param onboarding "required" sends users who haven't finished onboarding to /onboarding;
 *                   "only" sends users who have finished it to /dashboard.
 */
export function RequireAuth({
  children,
  onboarding = "required",
}: {
  children: React.ReactNode;
  onboarding?: "required" | "only";
}) {
  const { status, user, exitTo } = useSession();
  const router = useRouter();
  const pathname = usePathname();

  const redirect =
    status === "unauthenticated"
      ? (exitTo ?? `/login?next=${encodeURIComponent(pathname)}`)
      : status === "authenticated" && user
        ? routeFor(user.role, user.onboardingCompleted, pathname, onboarding)
        : null;

  useEffect(() => {
    if (redirect) router.replace(redirect);
  }, [redirect, router]);

  if (status !== "authenticated" || redirect) return <FullScreenLoading />;
  return <>{children}</>;
}

/** Paths an administrator may use (admins have no candidate profile). */
const ADMIN_PATHS = ["/admin", "/jobs", "/settings"];

export function homeFor(user: { role: string; onboardingCompleted: boolean }): string {
  if (user.role === "ADMIN") return "/admin/jobs";
  return user.onboardingCompleted ? "/dashboard" : "/onboarding";
}

/** Where a signed-in user must be redirected from `pathname`, or null if allowed. */
export function routeFor(
  role: string,
  onboardingCompleted: boolean,
  pathname: string,
  onboarding: "required" | "only",
): string | null {
  const under = (prefix: string) => pathname === prefix || pathname.startsWith(`${prefix}/`);
  if (role === "ADMIN") {
    return ADMIN_PATHS.some(under) ? null : "/admin/jobs";
  }
  if (under("/admin")) return "/dashboard";
  if (onboarding === "required" && !onboardingCompleted) return "/onboarding";
  if (onboarding === "only" && onboardingCompleted) return "/dashboard";
  return null;
}
