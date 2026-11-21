import type { Metadata } from "next";

import { EditJobView } from "./edit-job-view";

export const metadata: Metadata = { title: "Edit job" };

export default function EditJobPage() {
  return <EditJobView />;
}
