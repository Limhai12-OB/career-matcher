import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against a running stack (docker compose up).
 * E2E_BASE_URL defaults to the standard frontend port.
 * E2E_IGNORE_HTTPS_ERRORS=1 trusts self-signed certificates (local production rehearsal only).
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  fullyParallel: true,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    ignoreHTTPSErrors: process.env.E2E_IGNORE_HTTPS_ERRORS === "1",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] }, grepInvert: /@mobile/ },
    { name: "mobile", use: { ...devices["Pixel 7"] }, grep: /@mobile/ },
  ],
});
