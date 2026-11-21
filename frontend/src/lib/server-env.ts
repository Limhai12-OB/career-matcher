import "server-only";

/**
 * Server-side configuration. Read at request time so a single Docker image can be configured
 * per environment. Never put secrets in NEXT_PUBLIC_* variables.
 */
export function getBackendInternalUrl(): string {
  return process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080";
}
