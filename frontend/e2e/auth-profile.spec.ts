import { expect, test } from "@playwright/test";

import { completeOnboarding, login, PASSWORD, register, uniqueEmail } from "./helpers";

test("new candidate registers, onboards, edits profile, survives reload, signs out and back in", async ({
  page,
}) => {
  const email = uniqueEmail();
  await page.goto("/");
  await page.getByRole("link", { name: "Get started" }).click();
  await expect(page).toHaveURL(/\/register$/);

  await register(page, email);
  await completeOnboarding(page);

  // Dashboard shows real data from the API.
  await expect(page.getByRole("heading", { name: /Hi Ada/ })).toBeVisible();
  await expect(page.getByText("Backend Developer")).toBeVisible();
  await expect(page.getByLabel("Profile completeness")).toBeVisible();

  // Edit profile.
  await page.getByRole("link", { name: "Profile" }).first().click();
  await expect(page).toHaveURL(/\/profile$/);
  await page.getByLabel("Headline").fill("Aspiring backend engineer");
  await page.getByRole("button", { name: "Save changes" }).click();
  await expect(page.getByText("Profile saved")).toBeVisible();

  // Reload: the access token is in memory only, so the session must be restored via the
  // HttpOnly refresh cookie.
  await page.reload();
  await expect(page.getByLabel("Headline")).toHaveValue("Aspiring backend engineer");

  // The refresh cookie is HttpOnly and not readable from JavaScript.
  expect(await page.evaluate(() => document.cookie)).not.toContain("cm_refresh");

  // Sign out.
  await page.getByRole("button", { name: "Account menu" }).click();
  await page.getByRole("menuitem", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login$/);

  // Protected pages redirect to login and come back after sign-in.
  await page.goto("/settings");
  await expect(page).toHaveURL(/\/login\?next=%2Fsettings$/);
  await login(page, email);
  await expect(page).toHaveURL(/\/settings$/);
  await expect(page.getByRole("main").getByText(email)).toBeVisible();
});

test("shows validation and credential errors", async ({ page }) => {
  await page.goto("/register");
  await page.getByLabel("Full name").fill("A");
  await page.getByLabel("Email").fill("not-an-email");
  await page.getByLabel("Password", { exact: true }).fill("short");
  await page.getByLabel("Confirm password").fill("different");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByText("Full name must be at least 2 characters")).toBeVisible();
  await expect(page.getByText("Enter a valid email address")).toBeVisible();
  await expect(page.getByText("Use at least 10 characters")).toBeVisible();
  await expect(page.getByText("Passwords do not match")).toBeVisible();

  await page.goto("/login");
  await login(page, uniqueEmail("nobody"), "Wrong-password-1");
  await expect(page.getByRole("alert").filter({ hasText: "Invalid email or password." })).toBeVisible();
});

test("duplicate registration points the user to sign in", async ({ page, browser }) => {
  const email = uniqueEmail("dup");
  await register(page, email);

  const other = await browser.newPage();
  await other.goto("/register");
  await other.getByLabel("Full name").fill("Someone Else");
  await other.getByLabel("Email").fill(email);
  await other.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await other.getByLabel("Confirm password").fill(PASSWORD);
  await other.getByRole("button", { name: "Create account" }).click();
  await expect(other.getByText("An account with this email already exists.")).toBeVisible();
  await expect(other.getByRole("link", { name: "Sign in instead" })).toBeVisible();
});

test("change password, then delete account", async ({ page }) => {
  const email = uniqueEmail("settings");
  await register(page, email);
  await completeOnboarding(page);

  await page.goto("/settings");
  await page.getByLabel("Current password").fill(PASSWORD);
  await page.getByLabel("New password", { exact: true }).fill("Brand-new-pass-7");
  await page.getByLabel("Confirm new password").fill("Brand-new-pass-7");
  await page.getByRole("button", { name: "Update password" }).click();
  await expect(page.getByText(/Password changed/)).toBeVisible();

  // Session continues with the new tokens.
  await page.reload();
  await expect(page.getByRole("heading", { name: "Settings" })).toBeVisible();

  await page.getByRole("button", { name: "Delete my account" }).click();
  const dialog = page.getByRole("alertdialog");
  await dialog.getByLabel("Password", { exact: true }).fill("wrong-password-1");
  await dialog.getByRole("button", { name: "Delete permanently" }).click();
  await expect(dialog.getByText("Password is incorrect")).toBeVisible();
  await dialog.getByLabel("Password", { exact: true }).fill("Brand-new-pass-7");
  await dialog.getByRole("button", { name: "Delete permanently" }).click();
  await expect(page).toHaveURL(/\/$/);

  await page.goto("/login");
  await login(page, email, "Brand-new-pass-7");
  await expect(page.getByText("Invalid email or password.")).toBeVisible();
});

test("onboarding requires country, stage, roles, type and arrangement", async ({ page }) => {
  await register(page, uniqueEmail("onb"));
  await page.getByRole("button", { name: "Save and continue" }).click();
  await expect(page.getByText("Select your country")).toBeVisible();
  await expect(page.getByText("Select your current stage")).toBeVisible();
  // Dashboard is not reachable before onboarding is finished.
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/onboarding$/);
});

test("mobile navigation works @mobile", async ({ page }) => {
  await register(page, uniqueEmail("mobile"));
  await completeOnboarding(page);
  await page.getByRole("button", { name: "Open navigation" }).click();
  await page.getByRole("menuitem", { name: "Career preferences" }).click();
  await expect(page).toHaveURL(/\/preferences$/);
  await expect(page.getByRole("heading", { name: "Career preferences" })).toBeVisible();
});
