/**
 * Public, build-time configuration (inlined into the browser bundle).
 * Never put secrets in NEXT_PUBLIC_* variables.
 */
export const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"
).replace(/\/+$/, "");
