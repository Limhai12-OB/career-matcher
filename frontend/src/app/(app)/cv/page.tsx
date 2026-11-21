import type { Metadata } from "next";

import { CvView } from "./cv-view";

export const metadata: Metadata = { title: "CV analysis" };

export default function CvPage() {
  return <CvView />;
}
