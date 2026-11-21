import { ArrowRight, BarChart3, FileSearch, Route, ShieldCheck } from "lucide-react";
import Link from "next/link";

import { Logo } from "@/components/brand/logo";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

const FEATURES = [
  {
    icon: FileSearch,
    title: "CV analysis",
    body: "Upload a PDF or DOCX CV and get your skills, education and projects extracted into a structured profile you can review.",
    soon: true,
  },
  {
    icon: BarChart3,
    title: "Transparent job matching",
    body: "Entry-level IT roles ranked by a deterministic score, with every factor shown: skills, experience, projects, education and preferences.",
    soon: true,
  },
  {
    icon: Route,
    title: "Skill gaps and a learning plan",
    body: "See which required and preferred skills you're missing, and what to learn next.",
    soon: true,
  },
  {
    icon: ShieldCheck,
    title: "Private by design",
    body: "Your data is only ever visible to you. Delete your account and everything in it at any time.",
    soon: false,
  },
];

export default function LandingPage() {
  return (
    <div className="flex flex-1 flex-col">
      <header className="absolute inset-x-0 top-0 z-10">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Logo inverted />
          <nav className="flex items-center gap-2">
            <Button asChild variant="ghost" className="text-white hover:bg-white/10 hover:text-white">
              <Link href="/login">Sign in</Link>
            </Button>
            <Button asChild className="bg-white text-navy-900 hover:bg-white/90">
              <Link href="/register">Get started</Link>
            </Button>
          </nav>
        </div>
      </header>

      <section className="relative overflow-hidden bg-gradient-to-b from-navy-950 via-navy-900 to-navy-800 pt-32 pb-24 text-white">
        <div
          aria-hidden
          className="pointer-events-none absolute -top-40 left-1/2 size-[42rem] -translate-x-1/2 rounded-full bg-brand-500/25 blur-3xl"
        />
        <div className="relative mx-auto max-w-3xl px-4 text-center sm:px-6">
          <Badge className="mb-6 border-white/15 bg-white/10 text-brand-100 hover:bg-white/10">
            For students, graduates and entry-level IT job seekers
          </Badge>
          <h1 className="animate-fade-in text-4xl font-semibold tracking-tight text-balance sm:text-6xl">
            Find the IT roles that fit the skills you already have.
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-lg text-pretty text-slate-300">
            AI Career Matcher reads your CV, explains how well you match real entry-level jobs, and
            shows exactly which skills to build next.
          </p>
          <div className="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <Button asChild size="lg" className="h-11 bg-brand-500 px-6 hover:bg-brand-400">
              <Link href="/register">
                Create your free account <ArrowRight />
              </Link>
            </Button>
            <Button
              asChild
              size="lg"
              variant="ghost"
              className="h-11 px-6 text-white hover:bg-white/10 hover:text-white"
            >
              <Link href="/login">I already have an account</Link>
            </Button>
          </div>
        </div>
      </section>

      <section className="mx-auto w-full max-w-6xl px-4 py-20 sm:px-6">
        <h2 className="text-center text-2xl font-semibold tracking-tight sm:text-3xl">
          Career guidance you can actually verify
        </h2>
        <p className="mx-auto mt-3 max-w-2xl text-center text-muted-foreground">
          Scores are calculated by a transparent algorithm; AI is only used to explain results that
          are grounded in your real data.
        </p>
        <div className="mt-12 grid gap-5 sm:grid-cols-2">
          {FEATURES.map(({ icon: Icon, title, body, soon }) => (
            <div key={title} className="rounded-2xl border bg-card p-6 shadow-xs transition-shadow hover:shadow-md">
              <div className="flex items-center gap-3">
                <span className="grid size-10 place-items-center rounded-xl bg-accent text-accent-foreground">
                  <Icon className="size-5" aria-hidden />
                </span>
                <h3 className="font-semibold">{title}</h3>
                {soon && (
                  <Badge variant="secondary" className="ml-auto">
                    Coming soon
                  </Badge>
                )}
              </div>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{body}</p>
            </div>
          ))}
        </div>
      </section>

      <footer className="mt-auto border-t">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-2 px-4 py-6 text-sm text-muted-foreground sm:flex-row sm:px-6">
          <span>AI Career Matcher</span>
          <Link href="/status" className="hover:text-foreground">
            System status
          </Link>
        </div>
      </footer>
    </div>
  );
}
