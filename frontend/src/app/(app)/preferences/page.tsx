import type { Metadata } from "next";

import { PreferencesView } from "./preferences-view";

export const metadata: Metadata = { title: "Career preferences" };

export default function PreferencesPage() {
  return <PreferencesView />;
}
