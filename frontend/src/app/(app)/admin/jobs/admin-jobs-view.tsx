"use client";

import { ChevronLeft, ChevronRight, Plus, Search } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";

import { NativeSelect } from "@/components/forms/native-select";
import { OriginBadge } from "@/components/jobs/origin-badge";
import { PageHeader } from "@/components/shell/app-shell";
import { EmptyState, ErrorState, PageLoading } from "@/components/states/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAdminJobs } from "@/lib/api/hooks";
import type { JobStatus } from "@/lib/api/types";
import { STATUS_LABELS } from "@/lib/jobs";

const STATUS_STYLE: Record<JobStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  PUBLISHED: "bg-emerald-50 text-emerald-700",
  CLOSED: "bg-red-50 text-red-700",
};

export function AdminJobsView() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const status = ["DRAFT", "PUBLISHED", "CLOSED"].includes(sp.get("status") ?? "") ? sp.get("status")! : "";
  const origin = ["REAL", "DEMO"].includes(sp.get("origin") ?? "") ? sp.get("origin")! : "";
  const page = Math.max(0, Number(sp.get("page")) || 0);
  const q = (sp.get("q") ?? "").slice(0, 100);
  const [query, setQuery] = useState(q);

  const params = useMemo(() => {
    const p = new URLSearchParams();
    if (q) p.set("q", q);
    if (status) p.set("status", status);
    if (origin) p.set("origin", origin);
    if (page) p.set("page", String(page));
    return p;
  }, [q, status, origin, page]);
  const jobs = useAdminJobs(params);
  const latest = useRef(params);
  useEffect(() => {
    latest.current = params;
  }, [params]);

  function update(patch: Record<string, string>) {
    const p = new URLSearchParams(latest.current);
    Object.entries(patch).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    if (!("page" in patch)) p.delete("page");
    router.replace(p.size ? `${pathname}?${p}` : pathname, { scroll: false });
  }

  useEffect(() => {
    const t = setTimeout(() => {
      if (query.trim() !== (latest.current.get("q") ?? "")) update({ q: query.trim() });
    }, 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  return (
    <div className="animate-fade-in space-y-6">
      <PageHeader
        title="Manage jobs"
        description="Create, publish and close listings. Real listings must link to the actual employer or job board."
        actions={
          <Button asChild>
            <Link href="/admin/jobs/new">
              <Plus /> New job
            </Link>
          </Button>
        }
      />
      <div className="grid gap-3 sm:grid-cols-[1fr_180px_180px]">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search title, company…" className="pl-9" aria-label="Search jobs" />
        </div>
        <NativeSelect aria-label="Status" value={status} onChange={(e) => update({ status: e.target.value })}>
          <option value="">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="PUBLISHED">Published</option>
          <option value="CLOSED">Closed</option>
        </NativeSelect>
        <NativeSelect aria-label="Origin" value={origin} onChange={(e) => update({ origin: e.target.value })}>
          <option value="">Real and demo</option>
          <option value="REAL">Real</option>
          <option value="DEMO">Demo</option>
        </NativeSelect>
      </div>

      {jobs.isPending ? (
        <PageLoading label="Loading jobs" />
      ) : jobs.isError ? (
        <ErrorState error={jobs.error} onRetry={() => void jobs.refetch()} />
      ) : jobs.data.content.length === 0 ? (
        <EmptyState
          title="No jobs found"
          description="Create the first listing, or adjust the filters."
          action={
            <Button asChild>
              <Link href="/admin/jobs/new">New job</Link>
            </Button>
          }
        />
      ) : (
        <>
          <div className="overflow-x-auto rounded-xl border bg-card">
            <table className="w-full text-sm">
              <thead className="border-b bg-muted/40 text-left text-xs text-muted-foreground uppercase">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">Job</th>
                  <th scope="col" className="px-4 py-3 font-medium">Status</th>
                  <th scope="col" className="px-4 py-3 font-medium">Origin</th>
                  <th scope="col" className="px-4 py-3 font-medium">Posted</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {jobs.data.content.map((j) => (
                  <tr key={j.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3">
                      <Link href={`/admin/jobs/${j.id}`} className="font-medium text-primary hover:underline">
                        {j.title}
                      </Link>
                      <div className="text-xs text-muted-foreground">{j.companyName}</div>
                    </td>
                    <td className="px-4 py-3">
                      <Badge className={STATUS_STYLE[j.status]}>{STATUS_LABELS[j.status]}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <OriginBadge origin={j.dataOrigin} />
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-muted-foreground">{j.postedOn}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {jobs.data.page.totalPages > 1 && (
            <nav aria-label="Pagination" className="flex items-center justify-center gap-3">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>
                <ChevronLeft /> Previous
              </Button>
              <span className="text-sm text-muted-foreground">
                Page {page + 1} of {jobs.data.page.totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={page + 1 >= jobs.data.page.totalPages} onClick={() => update({ page: String(page + 1) })}>
                Next <ChevronRight />
              </Button>
            </nav>
          )}
        </>
      )}
    </div>
  );
}
