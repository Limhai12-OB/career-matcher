import "server-only";

import { parseBackendHealth, UNREACHABLE_STATUS, type SystemStatus } from "@/lib/health";
import { getBackendInternalUrl } from "@/lib/server-env";

/** Fetches backend health including its dependencies (database, AI service). */
export async function fetchSystemStatus(): Promise<SystemStatus> {
  try {
    // Actuator returns 503 with a JSON body when a dependency is down, so parse regardless of status.
    const res = await fetch(`${getBackendInternalUrl()}/actuator/health/dependencies`, {
      cache: "no-store",
      signal: AbortSignal.timeout(3000),
      headers: { Accept: "application/json" },
    });
    return parseBackendHealth(await res.json());
  } catch {
    return UNREACHABLE_STATUS;
  }
}
