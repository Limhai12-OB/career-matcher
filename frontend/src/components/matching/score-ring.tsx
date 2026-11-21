import { scoreBand } from "@/lib/matching";
import { cn } from "@/lib/utils";

const COLORS = {
  strong: "text-emerald-500",
  good: "text-brand-500",
  partial: "text-amber-500",
  low: "text-slate-400",
} as const;

/** Circular score indicator (0-100). The number is always shown as text, not only as color. */
export function ScoreRing({ score, size = 64, className }: { score: number; size?: number; className?: string }) {
  const { band, label } = scoreBand(score);
  const r = 26;
  const circumference = 2 * Math.PI * r;
  const offset = circumference * (1 - Math.min(100, Math.max(0, score)) / 100);
  return (
    <div
      className={cn("relative inline-grid shrink-0 place-items-center", className)}
      style={{ width: size, height: size }}
      role="img"
      aria-label={`${Math.round(score)}% compatibility, ${label.toLowerCase()}`}
    >
      <svg viewBox="0 0 64 64" className="absolute inset-0 -rotate-90" aria-hidden>
        <circle cx="32" cy="32" r={r} fill="none" strokeWidth="6" className="stroke-muted" />
        <circle
          cx="32"
          cy="32"
          r={r}
          fill="none"
          strokeWidth="6"
          strokeLinecap="round"
          stroke="currentColor"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          className={cn("transition-[stroke-dashoffset] duration-700", COLORS[band])}
        />
      </svg>
      <span className="text-sm font-semibold tabular-nums" aria-hidden>
        {Math.round(score)}%
      </span>
    </div>
  );
}
