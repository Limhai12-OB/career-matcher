import { Info } from "lucide-react";
import Link from "next/link";

import type { CandidateDataStatus } from "@/lib/api/types";

/** Tells the candidate which missing information is lowering their scores, with a fix. */
export function DataHints({ data }: { data: CandidateDataStatus }) {
  const hints: { text: string; href: string; cta: string }[] = [];
  if (!data.hasCvAnalysis) {
    hints.push({ text: "Your experience, projects and education aren't known yet.", href: "/cv", cta: "Analyze your CV" });
  } else if (data.cvExtractionLimited) {
    hints.push({
      text: "Your CV was read in basic mode, so experience and projects couldn't be counted.",
      href: "/cv",
      cta: "View CV analysis",
    });
  }
  if (!data.hasConfirmedSkills) {
    hints.push({ text: "You haven't confirmed any skills, so skill scores are 0.", href: "/cv", cta: "Confirm skills" });
  }
  if (!data.hasPreferences) {
    hints.push({ text: "Without career preferences a neutral value is used.", href: "/preferences", cta: "Set preferences" });
  }
  if (hints.length === 0) return null;
  return (
    <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900" role="note">
      <p className="flex items-center gap-2 font-medium">
        <Info className="size-4" aria-hidden /> Your scores are less accurate than they could be
      </p>
      <ul className="mt-2 space-y-1">
        {hints.map((h) => (
          <li key={h.text}>
            {h.text}{" "}
            <Link href={h.href} className="font-medium underline underline-offset-2">
              {h.cta}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
