"use client";

import { ChevronLeft, ChevronRight, FlaskConical, Search, X } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";

import { NativeSelect } from "@/components/forms/native-select";
import { JobCard } from "@/components/jobs/job-card";
import { PageHeader } from "@/components/shell/app-shell";
import { EmptyState, ErrorState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useJobSearch, useSkillCatalog } from "@/lib/api/hooks";
import { EMPLOYMENT_TYPES, JOB_REMOTE_TYPES, JOB_SENIORITIES } from "@/lib/api/types";
import { COUNTRY_OPTIONS } from "@/lib/countries";
import { parseFilters, REMOTE_TYPE_LABELS, SENIORITY_LABELS, toSearchParams, type JobFilters } from "@/lib/jobs";
import { EMPLOYMENT_TYPE_LABELS } from "@/lib/labels";

export function JobsView() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const filters = useMemo(() => parseFilters(new URLSearchParams(searchParams.toString())), [searchParams]);
  const apiParams = useMemo(() => toSearchParams(filters), [filters]);
  const jobs = useJobSearch(apiParams);
  const catalog = useSkillCatalog();
  const [query, setQuery] = useState(filters.q);
  // Latest URL filters, so delayed updates never overwrite newer changes with stale values.
  const latest = useRef(filters);
  useEffect(() => {
    latest.current = filters;
  }, [filters]);

  // Debounced free-text search.
  useEffect(() => {
    const t = setTimeout(() => {
      if (query.trim() !== latest.current.q.trim()) update({ q: query });
    }, 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  function update(patch: Partial<JobFilters>) {
    const next = { ...latest.current, ...patch, page: patch.page ?? 0 };
    const qs = toSearchParams(next).toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }

  const active = Object.entries(filters).some(([k, v]) => k !== "page" && v !== "" && v !== 0);
  const data = jobs.data;
  const hasDemo = data?.content.some((j) => j.dataOrigin === "DEMO");

  return (
    <div className="animate-fade-in space-y-6">
      <PageHeader title="Jobs" description="Entry-level IT and software roles. Every listing shows its source." />

      <section aria-label="Search and filters" className="space-y-3 rounded-2xl border bg-card p-4">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value.slice(0, 100))}
            placeholder="Search by title, company or keyword"
            className="pl-9"
            aria-label="Search jobs"
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
          <NativeSelect aria-label="Country" value={filters.country} onChange={(e) => update({ country: e.target.value })}>
            <option value="">Any country</option>
            {COUNTRY_OPTIONS.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </NativeSelect>
          <NativeSelect aria-label="Work arrangement" value={filters.remote} onChange={(e) => update({ remote: e.target.value as JobFilters["remote"] })}>
            <option value="">Any arrangement</option>
            {JOB_REMOTE_TYPES.map((r) => (
              <option key={r} value={r}>
                {REMOTE_TYPE_LABELS[r]}
              </option>
            ))}
          </NativeSelect>
          <NativeSelect aria-label="Employment type" value={filters.type} onChange={(e) => update({ type: e.target.value as JobFilters["type"] })}>
            <option value="">Any type</option>
            {EMPLOYMENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {EMPLOYMENT_TYPE_LABELS[t]}
              </option>
            ))}
          </NativeSelect>
          <NativeSelect aria-label="Seniority" value={filters.seniority} onChange={(e) => update({ seniority: e.target.value as JobFilters["seniority"] })}>
            <option value="">Any level</option>
            {JOB_SENIORITIES.map((s) => (
              <option key={s} value={s}>
                {SENIORITY_LABELS[s]}
              </option>
            ))}
          </NativeSelect>
          <NativeSelect aria-label="Skill" value={filters.skill} onChange={(e) => update({ skill: e.target.value })}>
            <option value="">Any skill</option>
            {catalog.data?.map((s) => (
              <option key={s.slug} value={s.slug}>
                {s.name}
              </option>
            ))}
          </NativeSelect>
          <NativeSelect aria-label="Listing type" value={filters.origin} onChange={(e) => update({ origin: e.target.value as JobFilters["origin"] })}>
            <option value="">Real and demo</option>
            <option value="REAL">Real opportunities only</option>
            <option value="DEMO">Demo listings only</option>
          </NativeSelect>
        </div>
        {active && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setQuery("");
              router.replace(pathname, { scroll: false });
            }}
          >
            <X /> Clear filters
          </Button>
        )}
      </section>

      {hasDemo && (
        <p className="flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <FlaskConical className="mt-0.5 size-4 shrink-0" aria-hidden />
          Listings marked “Demo listing” are demonstration data from fictional companies. They are not
          real openings and can&apos;t be applied to.
        </p>
      )}

      <section
        aria-label="Results"
        aria-busy={jobs.isFetching}
        data-testid="job-results"
        // Previous results stay visible (dimmed) while a new search loads.
        className={jobs.isPlaceholderData ? "opacity-60 transition-opacity" : "transition-opacity"}
      >
        {jobs.isPending ? (
          <div className="grid gap-4 md:grid-cols-2">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-44 rounded-2xl" />
            ))}
          </div>
        ) : jobs.isError ? (
          <ErrorState error={jobs.error} onRetry={() => void jobs.refetch()} />
        ) : data!.content.length === 0 ? (
          <EmptyState
            title="No jobs match your search"
            description={active ? "Try removing a filter or searching for something broader." : "There are no published jobs yet. Check back soon."}
          />
        ) : (
          <>
            <p className="mb-3 text-sm text-muted-foreground" aria-live="polite">
              {data!.page.totalElements} job{data!.page.totalElements === 1 ? "" : "s"} found
            </p>
            <ul className="grid gap-4 md:grid-cols-2">
              {data!.content.map((job) => (
                <li key={job.id}>
                  <JobCard job={job} />
                </li>
              ))}
            </ul>
            {data!.page.totalPages > 1 && (
              <nav aria-label="Pagination" className="mt-6 flex items-center justify-center gap-3">
                <Button variant="outline" size="sm" disabled={filters.page === 0} onClick={() => update({ page: filters.page - 1 })}>
                  <ChevronLeft /> Previous
                </Button>
                <span className="text-sm text-muted-foreground">
                  Page {filters.page + 1} of {data!.page.totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={filters.page + 1 >= data!.page.totalPages}
                  onClick={() => update({ page: filters.page + 1 })}
                >
                  Next <ChevronRight />
                </Button>
              </nav>
            )}
          </>
        )}
      </section>
    </div>
  );
}

