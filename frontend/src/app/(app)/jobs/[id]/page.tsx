import type { Metadata } from "next";

import { JobDetailView } from "./job-detail-view";

export const metadata: Metadata = { title: "Job details" };

export default function JobDetailPage() {
  return <JobDetailView />;
}
