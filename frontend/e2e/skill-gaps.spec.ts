import path from "node:path";

import { expect, test } from "@playwright/test";

import { completeOnboarding, register, uniqueEmail } from "./helpers";

test("skill gap analysis, learning roadmap and progress after adding skills", async ({ page }) => {
  await register(page, uniqueEmail("gaps"));
  await completeOnboarding(page);

  await page.getByRole("link", { name: "Skill gaps" }).first().click();
  await expect(page.getByRole("heading", { name: "Skill gaps", level: 1 })).toBeVisible();
  await expect(page.getByText("No analysis yet", { exact: true }).first()).toBeVisible();

  await page.getByRole("button", { name: "Analyze my skill gaps" }).click();
  await expect(page.getByRole("heading", { name: "Missing skills" })).toBeVisible();
  const gaps = page.getByTestId("skill-gap");
  await expect(gaps.first()).toBeVisible();
  await expect(gaps.first()).toContainText(/Required by \d+ of your top \d+ matches/);
  await expect(page.getByText("Required skills you have")).toBeVisible();

  // Curated resources open safely in a new tab.
  const resource = page.getByRole("list", { name: "Learning resources" }).first().getByRole("link").first();
  await expect(resource).toHaveAttribute("href", /^https:\/\//);
  await expect(resource).toHaveAttribute("rel", "noopener noreferrer");

  // Local mode: a deterministic roadmap, clearly labelled as not AI.
  await page.getByRole("button", { name: "Create my roadmap" }).click();
  await expect(page.getByText("Built from your skill gaps (no AI).")).toBeVisible();
  const steps = page.getByTestId("roadmap-step");
  await expect(steps.first()).toBeVisible();
  await expect(steps.first()).toContainText(/about \d+ weeks?/);
  await expect(page.getByText(/doesn't guarantee an interview or job/)).toBeVisible();

  // The roadmap is stored with the report.
  await page.reload();
  await expect(page.getByText("Built from your skill gaps (no AI).")).toBeVisible();
  await expect(page.getByRole("button", { name: "Create my roadmap" })).toHaveCount(0);

  // Gain skills from a CV, then re-analyze: progress is tracked across reports.
  await page.goto("/cv");
  await page.getByTestId("cv-file-input").setInputFiles(path.join(__dirname, "fixtures", "sample-cv.pdf"));
  await expect(page.getByRole("heading", { name: "Skills found in your CV" })).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "Add selected skills to my profile" }).click();
  await expect(page.getByText(/skills added to your profile/)).toBeVisible();

  await page.goto("/skill-gaps");
  await expect(page.getByText("Now confirmed").first()).toBeVisible(); // first report, gaps since closed
  await page.getByRole("button", { name: "Run a new analysis" }).click();
  await expect(page.getByRole("list", { name: "Analysis history" }).getByRole("listitem")).toHaveCount(2);
  const closed = page.getByText("Closed since first analysis").locator("..");
  await expect(closed.locator("p").nth(1)).not.toHaveText("0");

  // The dashboard summarizes the latest analysis.
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Skill gaps" })).toBeVisible();
  await expect(page.getByText(/of required skills/)).toBeVisible();
});
