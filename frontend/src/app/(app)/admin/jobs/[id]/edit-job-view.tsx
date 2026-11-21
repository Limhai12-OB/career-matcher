"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ExternalLink } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { JobForm } from "@/components/admin/job-form";
import { OriginBadge } from "@/components/jobs/origin-badge";
import { PageHeader } from "@/components/shell/app-shell";
import { ErrorState, PageLoading } from "@/components/states/states";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { adminJobsApi } from "@/lib/api/endpoints";
import { userMessage } from "@/lib/api/errors";
import { jobKeys, useAdminJob, useInvalidateJobs } from "@/lib/api/hooks";
import type { JobDetail, JobStatus } from "@/lib/api/types";
import { STATUS_LABELS } from "@/lib/jobs";
import { formToPayload, jobToForm } from "@/lib/validation/job-schema";

export function EditJobView() {
  const { id } = useParams<{ id: string }>();
  const job = useAdminJob(id);
  return (
    <div className="animate-fade-in">
      <Link href="/admin/jobs" className="text-sm text-primary hover:underline">← Manage jobs</Link>
      <div className="mt-4">
        {job.isPending ? (
          <PageLoading label="Loading job" />
        ) : job.isError ? (
          <ErrorState error={job.error} onRetry={() => void job.refetch()} />
        ) : (
          <Editor job={job.data} />
        )}
      </div>
    </div>
  );
}

function Editor({ job }: { job: JobDetail }) {
  const router = useRouter();
  const qc = useQueryClient();
  const invalidate = useInvalidateJobs();
  const [busy, setBusy] = useState(false);

  async function setStatus(status: JobStatus) {
    setBusy(true);
    try {
      const updated = await adminJobsApi.setStatus(job.id, status);
      qc.setQueryData(jobKeys.adminDetail(job.id), updated);
      invalidate();
      toast.success(status === "PUBLISHED" ? "Job published." : status === "CLOSED" ? "Job closed." : "Moved to draft.");
    } catch (err) {
      toast.error(userMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    try {
      await adminJobsApi.remove(job.id);
      invalidate();
      toast.success("Job deleted.");
      router.replace("/admin/jobs");
    } catch (err) {
      toast.error(userMessage(err));
    }
  }

  return (
    <>
      <PageHeader
        title={job.title}
        description={`${job.companyName} · last updated ${new Date(job.updatedAt).toLocaleString()}`}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline">{STATUS_LABELS[job.status]}</Badge>
            <OriginBadge origin={job.dataOrigin} />
            {job.status === "PUBLISHED" && (
              <Button asChild variant="outline" size="sm">
                <Link href={`/jobs/${job.id}`}>
                  View public page <ExternalLink />
                </Link>
              </Button>
            )}
            {job.status !== "PUBLISHED" && (
              <Button size="sm" disabled={busy} onClick={() => void setStatus("PUBLISHED")}>
                Publish
              </Button>
            )}
            {job.status === "PUBLISHED" && (
              <Button size="sm" variant="outline" disabled={busy} onClick={() => void setStatus("CLOSED")}>
                Close
              </Button>
            )}
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button size="sm" variant="destructive">Delete</Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Delete this job?</AlertDialogTitle>
                  <AlertDialogDescription>
                    The listing is removed permanently. To hide it from candidates but keep it, close it instead.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction className="bg-destructive text-white hover:bg-destructive/90" onClick={() => void remove()}>
                    Delete
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        }
      />
      <JobForm
        key={job.updatedAt}
        initial={jobToForm(job)}
        submitLabel="Save changes"
        onSubmit={async (values) => {
          const updated = await adminJobsApi.update(job.id, formToPayload(values));
          qc.setQueryData(jobKeys.adminDetail(job.id), updated);
          invalidate();
          toast.success("Changes saved.");
        }}
      />
    </>
  );
}
