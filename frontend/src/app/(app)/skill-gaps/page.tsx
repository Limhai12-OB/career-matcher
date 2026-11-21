import type { Metadata } from "next";
import { Suspense } from "react";

import { PageLoading } from "@/components/states/states";

import { SkillGapsView } from "./skill-gaps-view";

export const metadata: Metadata = { title: "Skill gaps" };

export default function SkillGapsPage() {
  return (
    <Suspense fallback={<PageLoading label="Loading skill gaps" />}>
      <SkillGapsView />
    </Suspense>
  );
}
