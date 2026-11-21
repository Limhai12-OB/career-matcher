import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { StatusBadge } from "@/components/status-badge";
import { fetchSystemStatus } from "@/lib/system-status";

export const metadata: Metadata = { title: "System status" };

async function StatusRows() {
  const status = await fetchSystemStatus();
  const rows = [
    { name: "Frontend", state: "UP" as const },
    { name: "Backend API", state: status.backend },
    ...status.dependencies,
  ];
  return (
    <ul className="divide-y divide-slate-200">
      {rows.map((row) => (
        <li key={row.name} className="flex items-center justify-between py-3">
          <span className="text-sm font-medium text-slate-700">{row.name}</span>
          <StatusBadge state={row.state} />
        </li>
      ))}
    </ul>
  );
}

function StatusSkeleton() {
  return (
    <div className="space-y-3 py-2" aria-busy="true" aria-label="Loading status">
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="h-8 animate-pulse rounded-md bg-slate-100" />
      ))}
    </div>
  );
}

export default function StatusPage() {
  return (
    <main className="mx-auto w-full max-w-xl flex-1 px-4 py-16">
      <Link href="/" className="text-sm text-brand-600 hover:underline">
        ← Back
      </Link>
      <h1 className="mt-4 text-2xl font-semibold text-navy-900">System status</h1>
      <p className="mt-1 text-sm text-slate-500">Live health of platform services.</p>
      <div className="mt-6 rounded-2xl border border-slate-200 bg-white px-6 py-2 shadow-sm">
        <Suspense fallback={<StatusSkeleton />}>
          <StatusRows />
        </Suspense>
      </div>
    </main>
  );
}
