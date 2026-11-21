import { FlaskConical, ShieldCheck } from "lucide-react";

import type { DataOrigin } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/** Always rendered next to a job so demo listings can never be mistaken for real ones. */
export function OriginBadge({ origin, className }: { origin: DataOrigin; className?: string }) {
  return origin === "DEMO" ? (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800",
        className,
      )}
      title="Demonstration data, not a real job opening"
    >
      <FlaskConical className="size-3" aria-hidden /> Demo listing
    </span>
  ) : (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700",
        className,
      )}
      title="Real opportunity added by our team"
    >
      <ShieldCheck className="size-3" aria-hidden /> Real opportunity
    </span>
  );
}
