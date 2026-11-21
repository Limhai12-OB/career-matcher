"use client";

import { Download, FileText, MoreHorizontal, RotateCw, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { AnalysisStatusBadge } from "@/components/cv/analysis-status";
import { CvDropzone } from "@/components/cv/dropzone";
import { PageHeader } from "@/components/shell/app-shell";
import { EmptyState, ErrorState, PageLoading } from "@/components/states/states";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cvApi } from "@/lib/api/endpoints";
import { ApiError, userMessage } from "@/lib/api/errors";
import { useCvDocuments, useDeleteCv, useReanalyze, useUploadCv } from "@/lib/api/hooks";
import type { CvDocument } from "@/lib/api/types";
import { analysisErrorMessage, formatBytes } from "@/lib/cv";
import { saveBlob } from "@/lib/download";

const UPLOAD_ERRORS: Record<string, string> = {
  ANALYSIS_IN_PROGRESS: "Please wait for your current analysis to finish before uploading another CV.",
  CV_LIMIT_REACHED: "You've reached the maximum number of stored CVs. Delete one to upload another.",
  PAYLOAD_TOO_LARGE: "The file is larger than 5 MB.",
  RATE_LIMITED: "You've uploaded several CVs recently. Please try again later.",
};

function uploadErrorMessage(err: unknown): string {
  if (err instanceof ApiError && UPLOAD_ERRORS[err.code]) return UPLOAD_ERRORS[err.code];
  return userMessage(err);
}

const dateTime = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });

export function CvView() {
  const router = useRouter();
  const docs = useCvDocuments();
  const upload = useUploadCv();
  const [uploadError, setUploadError] = useState<string | null>(null);

  const running = docs.data?.some((d) => d.analyses.some((a) => a.status === "PENDING" || a.status === "PROCESSING"));

  async function onFile(file: File) {
    setUploadError(null);
    try {
      const analysis = await upload.mutateAsync(file);
      toast.success("CV uploaded. Analysis started.");
      router.push(`/cv/analyses/${analysis.id}`);
    } catch (err) {
      setUploadError(uploadErrorMessage(err));
    }
  }

  return (
    <div className="animate-fade-in space-y-8">
      <PageHeader
        title="CV analysis"
        description="Upload your CV to extract your skills, education and experience. You review everything before it's added to your profile."
      />

      <section aria-label="Upload a CV">
        <CvDropzone
          onFile={(f) => void onFile(f)}
          busy={upload.isPending}
          disabled={upload.isPending || !!running}
          disabledReason={running ? "An analysis is in progress. You can upload again when it finishes." : undefined}
        />
        {uploadError && (
          <p role="alert" className="mt-2 text-sm text-destructive">
            {uploadError}
          </p>
        )}
      </section>

      <section aria-labelledby="history-heading" className="space-y-4">
        <h2 id="history-heading" className="text-lg font-semibold">
          Your CVs
        </h2>
        {docs.isPending ? (
          <PageLoading label="Loading your CVs" />
        ) : docs.isError ? (
          <ErrorState error={docs.error} onRetry={() => void docs.refetch()} />
        ) : docs.data.length === 0 ? (
          <EmptyState title="No CVs yet" description="Upload your first CV above to get started." />
        ) : (
          <ul className="space-y-3">
            {docs.data.map((doc) => (
              <li key={doc.id}>
                <DocumentCard doc={doc} busy={!!running} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function DocumentCard({ doc, busy }: { doc: CvDocument; busy: boolean }) {
  const router = useRouter();
  const reanalyze = useReanalyze();
  const remove = useDeleteCv();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const latest = doc.analyses[0];
  const older = doc.analyses.slice(1);

  async function download() {
    try {
      saveBlob(await cvApi.download(doc.id), doc.filename);
    } catch (err) {
      toast.error(userMessage(err));
    }
  }

  async function runAgain() {
    try {
      const a = await reanalyze.mutateAsync(doc.id);
      router.push(`/cv/analyses/${a.id}`);
    } catch (err) {
      toast.error(uploadErrorMessage(err));
    }
  }

  async function confirmRemove() {
    try {
      await remove.mutateAsync(doc.id);
      toast.success("CV deleted.");
    } catch (err) {
      toast.error(userMessage(err));
    } finally {
      setConfirmDelete(false);
    }
  }

  return (
    <Card className="gap-4 py-5">
      <CardHeader>
        <div className="flex min-w-0 items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-accent text-accent-foreground">
            <FileText className="size-5" aria-hidden />
          </span>
          <div className="min-w-0">
            <CardTitle className="truncate text-base">{doc.filename}</CardTitle>
            <CardDescription>
              {doc.type} · {formatBytes(doc.sizeBytes)} · Uploaded {dateTime(doc.uploadedAt)}
            </CardDescription>
          </div>
        </div>
        <CardAction>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" aria-label={`Actions for ${doc.filename}`}>
                <MoreHorizontal />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={() => void download()}>
                <Download /> Download
              </DropdownMenuItem>
              <DropdownMenuItem disabled={busy} onSelect={() => void runAgain()}>
                <RotateCw /> Analyze again
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onSelect={() => setConfirmDelete(true)}>
                <Trash2 /> Delete
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </CardAction>
      </CardHeader>
      {latest && (
        <CardContent className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/40 px-4 py-3">
            <div className="flex items-center gap-3">
              <AnalysisStatusBadge status={latest.status} />
              <span className="text-sm text-muted-foreground">
                {latest.status === "FAILED" ? analysisErrorMessage(latest.errorCode) : dateTime(latest.createdAt)}
              </span>
            </div>
            <Button asChild size="sm" variant="outline">
              <Link href={`/cv/analyses/${latest.id}`}>View analysis</Link>
            </Button>
          </div>
          {older.length > 0 && (
            <details className="text-sm">
              <summary className="cursor-pointer text-muted-foreground hover:text-foreground">
                Earlier analyses ({older.length})
              </summary>
              <ul className="mt-2 space-y-1">
                {older.map((a) => (
                  <li key={a.id} className="flex items-center gap-3">
                    <AnalysisStatusBadge status={a.status} />
                    <Link href={`/cv/analyses/${a.id}`} className="text-primary hover:underline">
                      {dateTime(a.createdAt)}
                    </Link>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </CardContent>
      )}
      <AlertDialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this CV?</AlertDialogTitle>
            <AlertDialogDescription>
              The file and all its analyses will be permanently deleted. Skills you already added to
              your profile are kept.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault();
                void confirmRemove();
              }}
              disabled={remove.isPending}
              className="bg-destructive text-white hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
