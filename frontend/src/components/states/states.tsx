import { AlertTriangle, Inbox, RotateCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { userMessage } from "@/lib/api/errors";

export function PageLoading({ label = "Loading" }: { label?: string }) {
  return (
    <div className="space-y-4" aria-busy="true" aria-label={label}>
      <Skeleton className="h-8 w-56" />
      <Skeleton className="h-4 w-80" />
      <div className="grid gap-4 pt-4 md:grid-cols-2">
        <Skeleton className="h-40 rounded-xl" />
        <Skeleton className="h-40 rounded-xl" />
      </div>
    </div>
  );
}

export function FullScreenLoading() {
  return (
    <div className="grid min-h-svh flex-1 place-items-center" aria-busy="true">
      <div className="flex flex-col items-center gap-3 text-muted-foreground">
        <span className="size-8 animate-spin rounded-full border-2 border-primary/20 border-t-primary" />
        <span className="text-sm">Loading your workspace…</span>
      </div>
    </div>
  );
}

export function ErrorState({
  error,
  onRetry,
  title = "We couldn't load this",
}: {
  error: unknown;
  onRetry?: () => void;
  title?: string;
}) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-xl border border-destructive/20 bg-destructive/5 px-6 py-10 text-center"
    >
      <AlertTriangle className="size-6 text-destructive" aria-hidden />
      <div>
        <p className="font-medium">{title}</p>
        <p className="mt-1 text-sm text-muted-foreground">{userMessage(error)}</p>
      </div>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RotateCw /> Try again
        </Button>
      )}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-10 text-center">
      <Inbox className="size-6 text-muted-foreground" aria-hidden />
      <div>
        <p className="font-medium">{title}</p>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  );
}
