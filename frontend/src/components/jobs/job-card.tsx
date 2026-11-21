import { Building2, MapPin, Wallet } from "lucide-react";
import Link from "next/link";

import { OriginBadge } from "@/components/jobs/origin-badge";
import { Badge } from "@/components/ui/badge";
import type { JobSummary } from "@/lib/api/types";
import { EMPLOYMENT_TYPE_LABELS } from "@/lib/labels";
import { formatLocation, formatSalary, postedAgo, SENIORITY_LABELS } from "@/lib/jobs";

export function JobCard({ job }: { job: JobSummary }) {
  const salary = formatSalary(job);
  return (
    <Link
      href={`/jobs/${job.id}`}
      className="group block rounded-2xl border bg-card p-5 shadow-xs transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="font-semibold group-hover:text-primary">{job.title}</h3>
          <p className="mt-0.5 flex items-center gap-1.5 text-sm text-muted-foreground">
            <Building2 className="size-3.5" aria-hidden /> {job.companyName}
          </p>
        </div>
        <OriginBadge origin={job.dataOrigin} />
      </div>
      <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <MapPin className="size-3.5" aria-hidden /> {formatLocation(job)}
        </span>
        {salary && (
          <span className="flex items-center gap-1.5">
            <Wallet className="size-3.5" aria-hidden /> {salary}
          </span>
        )}
      </div>
      <div className="mt-3 flex flex-wrap gap-1.5">
        <Badge variant="outline">{EMPLOYMENT_TYPE_LABELS[job.employmentType]}</Badge>
        {!(job.seniority === "INTERNSHIP" && job.employmentType === "INTERNSHIP") && (
          <Badge variant="outline">{SENIORITY_LABELS[job.seniority]}</Badge>
        )}
        {job.requiredSkills.slice(0, 4).map((s) => (
          <Badge key={s} variant="secondary">
            {s}
          </Badge>
        ))}
        {job.requiredSkills.length > 4 && (
          <Badge variant="secondary">+{job.requiredSkills.length - 4}</Badge>
        )}
      </div>
      <p className="mt-3 text-xs text-muted-foreground">{postedAgo(job.postedOn)}</p>
    </Link>
  );
}
