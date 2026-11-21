import { expect, test, type Browser, type Page } from "@playwright/test";

import { completeOnboarding, login, register, uniqueEmail } from "./helpers";

const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? "";
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? "";

async function candidate(page: Page) {
  await register(page, uniqueEmail("jobs"));
  await completeOnboarding(page);
}

async function adminPage(browser: Browser) {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto("/login");
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await expect(page).toHaveURL(/\/admin\/jobs$/);
  return { ctx, page };
}

test("candidates browse, search and filter clearly labelled demo listings", async ({ page }) => {
  await candidate(page);
  await page.getByRole("link", { name: "Jobs" }).first().click();
  await expect(page.getByRole("heading", { name: "Jobs", exact: true })).toBeVisible();
  await expect(page.getByText(/demonstration data from fictional companies/)).toBeVisible();
  await expect(page.getByText("Demo listing", { exact: true }).first()).toBeVisible();

  await page.getByLabel("Search jobs").fill("Flutter");
  await expect(page).toHaveURL(/q=Flutter/);
  await expect(page.getByRole("heading", { name: "Junior Mobile Developer (Flutter)" })).toBeVisible();
  await expect(page.getByText("1 job found")).toBeVisible();

  // Clear, then immediately filter: a pending search update must not undo the new filter.
  await page.getByRole("button", { name: "Clear filters" }).click();
  await page.getByLabel("Country").selectOption("SG");
  await page.waitForTimeout(600); // longer than the search debounce
  await expect(page).toHaveURL(/country=SG/);
  await expect(page).toHaveURL(/country=SG/);
  await expect(page.getByTestId("job-results")).toHaveAttribute("aria-busy", "false");
  const cards = page.locator("ul li a[href^='/jobs/']");
  await expect
    .poll(async () => {
      const texts = await cards.allInnerTexts();
      return texts.length > 0 && texts.every((t) => t.includes("Singapore"));
    }, { timeout: 15_000 })
    .toBe(true);

  // Filters survive a reload (they live in the URL).
  await page.reload();
  await expect(page.getByLabel("Country")).toHaveValue("SG");

  await cards.first().click();
  await expect(page.getByText("Demonstration listing", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Demo listing — no real application" })).toBeDisabled();
  await expect(page.getByRole("link", { name: /Apply on employer site/ })).toHaveCount(0);
  await expect(page.getByText("AI Career Matcher demo dataset")).toBeVisible();
  await expect(page.getByText("Required", { exact: true })).toBeVisible();
});

test("an empty search shows a helpful empty state", async ({ page }) => {
  await candidate(page);
  await page.goto("/jobs?q=zzzz-no-such-job-zzzz");
  await expect(page.getByText("No jobs match your search")).toBeVisible();
});

test("admin publishes a real listing that candidates can apply to, then closes it", async ({ page, browser }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD not set");
  const title = `Junior Kotlin Developer ${Date.now()}`;
  const { ctx, page: admin } = await adminPage(browser);

  await admin.getByRole("link", { name: "New job" }).first().click();
  await admin.getByRole("radio", { name: /Real opportunity/ }).click();
  await admin.getByLabel("Application URL").fill("https://jobs.acme-e2e.test/apply/42");
  await admin.getByLabel("Source", { exact: true }).fill("Acme careers page");
  await admin.getByLabel("Job title").fill(title);
  await admin.getByLabel("Company", { exact: true }).fill("Acme E2E Ltd");
  await admin.getByLabel("Description").fill("Build Android apps in Kotlin with a small, supportive team. ".repeat(2));
  await admin.getByLabel("Country").selectOption("KH");
  await admin.getByLabel("Search skills to add").fill("Kotl");
  await admin.getByRole("option", { name: "Kotlin" }).click();
  await admin.getByLabel("Search skills to add").fill("Git");
  await admin.getByRole("option", { name: "Git", exact: true }).click();
  await admin.getByRole("button", { name: "Save draft" }).click();
  await expect(admin.getByText("Draft saved.")).toBeVisible();
  await expect(admin).toHaveURL(/\/admin\/jobs\/[0-9a-f-]+$/);
  const jobUrl = admin.url().replace("/admin/jobs/", "/jobs/");

  // Drafts are invisible to candidates.
  await candidate(page);
  await page.goto(jobUrl);
  await expect(page.getByText("This job is no longer available")).toBeVisible();

  await admin.getByRole("button", { name: "Publish" }).click();
  await expect(admin.getByText("Job published.")).toBeVisible();

  await page.goto(`/jobs?q=${encodeURIComponent(title)}`);
  await expect(page.getByText("Real opportunity").first()).toBeVisible();
  await page.getByRole("heading", { name: title }).click();
  const apply = page.getByRole("link", { name: /Apply on employer site/ });
  await expect(apply).toHaveAttribute("href", "https://jobs.acme-e2e.test/apply/42");
  await expect(apply).toHaveAttribute("target", "_blank");
  await expect(apply).toHaveAttribute("rel", /noopener/);
  await expect(page.getByText("Acme careers page")).toBeVisible();

  await admin.getByRole("button", { name: "Close" }).click();
  await expect(admin.getByText("Job closed.")).toBeVisible();
  await page.reload();
  await expect(page.getByText("This job is no longer available")).toBeVisible();

  // Clean up: test data must not linger in the catalogue labelled as a real opportunity.
  await admin.getByRole("button", { name: "Delete" }).click();
  await admin.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click();
  await expect(admin).toHaveURL(/\/admin\/jobs$/);
  await expect(admin.getByText(title)).toHaveCount(0);
  await ctx.close();
});

test("admin form enforces source rules for demo and real listings", async ({ browser }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD not set");
  const { ctx, page: admin } = await adminPage(browser);
  await admin.goto("/admin/jobs/new");
  await admin.getByRole("button", { name: "Save draft" }).click();
  await expect(admin.getByText("Choose whether this is a real or demo listing")).toBeVisible();
  await expect(admin.getByText("Add at least one skill")).toBeVisible();

  await admin.getByRole("radio", { name: /Demo listing/ }).click();
  await admin.getByLabel("Application URL").fill("https://jobs.real-company.io/1");
  await admin.getByRole("button", { name: "Save draft" }).click();
  await expect(admin.getByText("Demo listings must link to example.com/.org/.net")).toBeVisible();
  await ctx.close();
});

test("roles are kept apart", async ({ page, browser }) => {
  await candidate(page);
  await page.goto("/admin/jobs");
  await expect(page).toHaveURL(/\/dashboard$/);

  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD not set");
  const { ctx, page: admin } = await adminPage(browser);
  await admin.goto("/dashboard");
  await expect(admin).toHaveURL(/\/admin\/jobs$/);
  await expect(admin.getByRole("link", { name: "CV analysis" })).toHaveCount(0);
  await ctx.close();
});
