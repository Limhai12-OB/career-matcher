"use client";

import { Button } from "@/components/ui/button";

export default function GlobalRouteError({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="grid flex-1 place-items-center px-4 py-24 text-center">
      <div role="alert">
        <h1 className="text-2xl font-semibold tracking-tight">Something went wrong</h1>
        <p className="mt-2 text-muted-foreground">An unexpected error occurred. Please try again.</p>
        <Button className="mt-6" onClick={reset}>
          Try again
        </Button>
      </div>
    </main>
  );
}
