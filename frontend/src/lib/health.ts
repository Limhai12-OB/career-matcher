/**
 * Pure helpers for interpreting Spring Boot Actuator health responses.
 * Kept free of server/browser APIs so they are unit-testable.
 */

export type ServiceState = "UP" | "DOWN" | "UNREACHABLE";

export interface DependencyStatus {
  name: string;
  state: ServiceState;
}

export interface SystemStatus {
  backend: ServiceState;
  dependencies: DependencyStatus[];
}

const DEPENDENCY_LABELS: Record<string, string> = {
  db: "Database",
  aiService: "AI service",
};

function toState(value: unknown): ServiceState {
  return value === "UP" ? "UP" : "DOWN";
}

export function parseBackendHealth(body: unknown): SystemStatus {
  if (typeof body !== "object" || body === null || !("status" in body)) {
    return { backend: "DOWN", dependencies: [] };
  }
  const components =
    "components" in body && typeof body.components === "object" && body.components !== null
      ? (body.components as Record<string, { status?: unknown }>)
      : {};

  return {
    // If the backend answered at all, its process is up; dependency states are reported separately.
    backend: "UP",
    dependencies: Object.entries(components).map(([key, value]) => ({
      name: DEPENDENCY_LABELS[key] ?? key,
      state: toState(value?.status),
    })),
  };
}

export const UNREACHABLE_STATUS: SystemStatus = {
  backend: "UNREACHABLE",
  dependencies: [],
};
