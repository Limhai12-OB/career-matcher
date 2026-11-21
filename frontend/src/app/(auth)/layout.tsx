import { CheckCircle2 } from "lucide-react";

import { GuestOnly } from "@/components/auth/guest-only";
import { Logo } from "@/components/brand/logo";

const POINTS = [
  "Structured CV analysis you can review and correct",
  "Match scores with every factor explained",
  "A clear list of skills to learn next",
];

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <GuestOnly>
      <div className="grid min-h-svh flex-1 lg:grid-cols-2">
        <aside className="relative hidden overflow-hidden bg-gradient-to-br from-navy-950 to-navy-800 p-10 text-white lg:flex lg:flex-col">
          <div aria-hidden className="absolute -right-32 -bottom-32 size-96 rounded-full bg-brand-500/20 blur-3xl" />
          <Logo inverted />
          <div className="relative mt-auto max-w-md">
            <h2 className="text-3xl font-semibold tracking-tight">Start your IT career with clarity.</h2>
            <ul className="mt-6 space-y-3 text-slate-300">
              {POINTS.map((p) => (
                <li key={p} className="flex items-start gap-2">
                  <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-brand-300" aria-hidden />
                  {p}
                </li>
              ))}
            </ul>
          </div>
        </aside>
        <main className="flex flex-col px-4 py-8 sm:px-8">
          <div className="lg:hidden">
            <Logo />
          </div>
          <div className="m-auto w-full max-w-sm animate-fade-in py-10">{children}</div>
        </main>
      </div>
    </GuestOnly>
  );
}
