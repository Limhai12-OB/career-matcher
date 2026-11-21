import { Suspense } from "react";

import { RequireAuth } from "@/components/auth/require-auth";
import { AppShell } from "@/components/shell/app-shell";
import { FullScreenLoading } from "@/components/states/states";

/**
 * Signed-in area. Rendered client-side behind the auth guard; the Suspense boundary lets dynamic
 * routes (e.g. /cv/analyses/[id]) read URL data during prerendering.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense fallback={<FullScreenLoading />}>
      <RequireAuth>
        <AppShell>{children}</AppShell>
      </RequireAuth>
    </Suspense>
  );
}
