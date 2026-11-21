import { expect, type Page } from "@playwright/test";

export const PASSWORD = "Correct-Horse-42";

export function uniqueEmail(prefix = "e2e"): string {
  return `${prefix}.${Date.now()}.${Math.random().toString(36).slice(2, 8)}@example.com`;
}

export async function register(page: Page, email: string, fullName = "Ada Lovelace") {
  await page.goto("/register");
  await page.getByLabel("Full name").fill(fullName);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByLabel("Confirm password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/onboarding$/);
}

export async function completeOnboarding(page: Page) {
  await expect(page.getByRole("heading", { name: /let's set up your profile/i })).toBeVisible();
  // Step 1
  await page.getByLabel("Current stage").selectOption("RECENT_GRADUATE");
  await page.getByLabel("Country").selectOption("KH");
  await page.getByLabel("City").fill("Phnom Penh");
  await page.getByRole("button", { name: "Save and continue" }).click();
  // Step 2
  const roles = page.getByLabel("Roles you're interested in");
  await roles.fill("Backend Developer");
  await roles.press("Enter");
  await page.getByRole("checkbox", { name: "Internship" }).click();
  await page.getByRole("radio", { name: "Hybrid" }).click();
  await page.getByRole("button", { name: "Save and continue" }).click();
  // Step 3
  await expect(page.getByText("Backend Developer")).toBeVisible();
  await page.getByRole("button", { name: "Finish setup" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

export async function login(page: Page, email: string, password = PASSWORD) {
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
}
