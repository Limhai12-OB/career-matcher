import path from "node:path";

import { expect, test, type Page } from "@playwright/test";

import { completeOnboarding, login, register, uniqueEmail } from "./helpers";

const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? "";
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? "";

async function topScore(page: Page): Promise<number> {
  const label = await page.getByRole("img", { name: /% compatibility/ }).first().getAttribute("aria-label");
  return Number(label!.match(/(\d+)%/)![1]);
}

test("recommendations improve after CV analysis and explain each score", async ({ page }) => {
  await register(page, uniqueEmail("match"));
  await completeOnboarding(page); // Backend Developer, internship, hybrid, Cambodia

  await page.getByRole("link", { name: "Recommendations" }).first().click();
  await expect(page.getByRole("heading", { name: "Recommended for you" })).toBeVisible();
  await expect(page.getByText("Your scores are less accurate than they could be")).toBeVisible();
  await expect(page.getByRole("link", { name: "Analyze your CV" })).toBeVisible();
  const before = await topScore(page);

  // Analyze a CV and confirm all recognized skills.
  await page.goto("/cv");
  await page.getByTestId("cv-file-input").setInputFiles(path.join(__dirname, "fixtures", "sample-cv.pdf"));
  await expect(page.getByRole("heading", { name: "Skills found in your CV" })).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "Add selected skills to my profile" }).click();
  await expect(page.getByText(/skills added to your profile/)).toBeVisible();

  await page.goto("/matches");
  await expect(page.getByText("You haven't confirmed any skills")).toHaveCount(0);
  await expect(page.getByText(/basic mode/)).toBeVisible(); // local extractor: experience/projects unknown
  await expect.poll(() => topScore(page)).toBeGreaterThan(before);

  // Scores are ordered best first.
  const labels = await page.getByRole("img", { name: /% compatibility/ }).evaluateAll((els) =>
    els.map((e) => Number(e.getAttribute("aria-label")!.match(/(\d+)%/)![1])),
  );
  expect([...labels].sort((a, b) => b - a)).toEqual(labels);

  // Open the best match: full breakdown, then a grounded explanation.
  await page.locator("ol a[href^='/jobs/']").first().click();
  await expect(page.getByRole("heading", { name: "Your compatibility" })).toBeVisible();
  for (const name of ["Skills score", "Experience score", "Projects score", "Education score", "Your preferences score"]) {
    await expect(page.getByRole("progressbar", { name })).toBeVisible();
  }
  await expect(page.getByText(/algorithm match-v1/)).toBeVisible();
  await expect(page.getByText(/Projects aren't available from your CV analysis/)).toBeVisible();

  await page.getByRole("button", { name: "Explain this match" }).click();
  await expect(page.getByText("Summary generated from your score breakdown (no AI)")).toBeVisible();
  const ringLabel = await page.getByRole("img", { name: /% compatibility/ }).first().getAttribute("aria-label");
  const shown = ringLabel!.match(/(\d+)%/)![1];
  await expect(page.getByText(new RegExp(`is ${shown}%`))).toBeVisible(); // same number as the score ring

  // Explanation is cached for this result.
  await page.reload();
  await expect(page.getByText("Summary generated from your score breakdown (no AI)")).toBeVisible();
  await expect(page.getByRole("button", { name: "Explain this match" })).toHaveCount(0);
});

test("the dashboard shows top matches", async ({ page }) => {
  await register(page, uniqueEmail("dash"));
  await completeOnboarding(page);
  await expect(page.getByRole("heading", { name: "Top matches" })).toBeVisible();
  await expect(page.getByRole("img", { name: /% compatibility/ })).toHaveCount(3);
});

test("admins don't get candidate matching", async ({ browser }) => {
  test.skip(!ADMIN_EMAIL || !ADMIN_PASSWORD, "E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD not set");
  const ctx = await browser.newContext();
  const admin = await ctx.newPage();
  await admin.goto("/login");
  await login(admin, ADMIN_EMAIL, ADMIN_PASSWORD);
  await expect(admin).toHaveURL(/\/admin\/jobs$/);
  await admin.goto("/matches");
  await expect(admin).toHaveURL(/\/admin\/jobs$/);
  // Open a specific, stable demo job (other tests create and delete jobs in parallel).
  await admin.goto("/jobs?q=Flutter");
  await admin.getByRole("heading", { name: "Junior Mobile Developer (Flutter)" }).click();
  await expect(admin.getByRole("heading", { name: "Skills" })).toBeVisible();
  await expect(admin.getByRole("heading", { name: "Your compatibility" })).toHaveCount(0);
  await ctx.close();
});
