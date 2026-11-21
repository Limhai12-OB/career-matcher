import type { Metadata } from "next";

import { AnalysisView } from "./analysis-view";

export const metadata: Metadata = { title: "CV analysis result" };

export default function AnalysisPage() {
  return <AnalysisView />;
}
