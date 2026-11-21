import type { Metadata } from "next";
import { Suspense } from "react";

import { PageLoading } from "@/components/states/states";

import { RecommendationsView } from "./recommendations-view";

export const metadata: Metadata = { title: "Recommendations" };

export default function MatchesPage() {
  return (
    <Suspense fallback={<PageLoading label="Loading recommendations" />}>
      <RecommendationsView />
    </Suspense>
  );
}
