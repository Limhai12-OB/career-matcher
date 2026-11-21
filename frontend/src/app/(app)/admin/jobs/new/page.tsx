"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { JobForm } from "@/components/admin/job-form";
import { PageHeader } from "@/components/shell/app-shell";
import { adminJobsApi } from "@/lib/api/endpoints";
import { useInvalidateJobs } from "@/lib/api/hooks";
import { emptyJobForm, formToPayload } from "@/lib/validation/job-schema";

export default function NewJobPage() {
  const router = useRouter();
  const invalidate = useInvalidateJobs();
  return (
    <div className="animate-fade-in">
      <Link href="/admin/jobs" className="text-sm text-primary hover:underline">← Manage jobs</Link>
      <div className="mt-4">
        <PageHeader title="New job" description="New jobs are saved as drafts. Publish them when they're ready." />
      </div>
      <JobForm
        initial={emptyJobForm()}
        submitLabel="Save draft"
        onSubmit={async (values) => {
          const job = await adminJobsApi.create(formToPayload(values));
          invalidate();
          toast.success("Draft saved.");
          router.replace(`/admin/jobs/${job.id}`);
        }}
      />
    </div>
  );
}
