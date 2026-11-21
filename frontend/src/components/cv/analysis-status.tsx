import { CheckCircle2, Clock, Loader2, XCircle } from "lucide-react";

import type { AnalysisStatus } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const CONFIG: Record<AnalysisStatus, { label: string; className: string; icon: React.ElementType }> = {
  PENDING: { label: "Queued", className: "bg-slate-100 text-slate-700", icon: Clock },
  PROCESSING: { label: "Analyzing", className: "bg-brand-50 text-brand-700", icon: Loader2 },
  COMPLETED: { label: "Completed", className: "bg-emerald-50 text-emerald-700", icon: CheckCircle2 },
  FAILED: { label: "Failed", className: "bg-red-50 text-red-700", icon: XCircle },
};

export function AnalysisStatusBadge({ status }: { status: AnalysisStatus }) {
  const { label, className, icon: Icon } = CONFIG[status];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium",
        className,
      )}
    >
      <Icon className={cn("size-3.5", status === "PROCESSING" && "animate-spin")} aria-hidden />
      {label}
    </span>
  );
}
