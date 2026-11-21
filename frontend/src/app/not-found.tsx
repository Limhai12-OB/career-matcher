import Link from "next/link";

import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="grid flex-1 place-items-center px-4 py-24 text-center">
      <div>
        <p className="text-sm font-semibold text-primary">404</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">Page not found</h1>
        <p className="mt-2 text-muted-foreground">The page you&apos;re looking for doesn&apos;t exist.</p>
        <Button asChild className="mt-6">
          <Link href="/">Go home</Link>
        </Button>
      </div>
    </main>
  );
}
