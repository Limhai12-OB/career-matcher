import type { Metadata } from "next";

import { SkillsView } from "./skills-view";

export const metadata: Metadata = { title: "My skills" };

export default function SkillsPage() {
  return <SkillsView />;
}
