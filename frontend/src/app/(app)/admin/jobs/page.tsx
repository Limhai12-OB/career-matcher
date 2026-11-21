import type { Metadata } from "next";
import { Suspense } from "react";

import { PageLoading } from "@/components/states/states";

import { AdminJobsView } from "./admin-jobs-view";

export const metadata: Metadata = { title: "Manage jobs" };

export default function AdminJobsPage() {
  return (
    <Suspense fallback={<PageLoading label="Loading jobs" />}>
      <AdminJobsView />
    </Suspense>
  );
}
