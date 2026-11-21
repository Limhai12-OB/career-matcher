import Link from "next/link";

import { cn } from "@/lib/utils";

export function Logo({ className, inverted = false }: { className?: string; inverted?: boolean }) {
  return (
    <Link
      href="/"
      className={cn("inline-flex items-center gap-2 font-semibold tracking-tight", className)}
      aria-label="AI Career Matcher home"
    >
      <span className="grid size-8 place-items-center rounded-lg bg-gradient-to-br from-brand-400 to-brand-700 text-sm font-bold text-white shadow-sm">
        CM
      </span>
      <span className={inverted ? "text-white" : "text-foreground"}>AI Career Matcher</span>
    </Link>
  );
}
