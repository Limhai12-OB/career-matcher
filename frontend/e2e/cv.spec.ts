import path from "node:path";

import { expect, test, type Page } from "@playwright/test";

import { completeOnboarding, register, uniqueEmail } from "./helpers";

const fixture = (name: string) => path.join(__dirname, "fixtures", name);

async function uploadCv(page: Page, file: string) {
  await page.goto("/cv");
  await page.getByTestId("cv-file-input").setInputFiles(fixture(file));
}

async function newCandidate(page: Page) {
  await register(page, uniqueEmail("cv"));
  await completeOnboarding(page);
}

test("upload a PDF, review evidence-backed results, confirm skills, manage them", async ({ page }) => {
  await newCandidate(page);
  await page.getByRole("link", { name: "CV analysis" }).first().click();
  await uploadCv(page, "sample-cv.pdf");

  // Upload = validation + encryption + storage; allow for a cold backend under parallel test load.
  await expect(page).toHaveURL(/\/cv\/analyses\/[0-9a-f-]+$/, { timeout: 15_000 });
  await expect(page.getByRole("heading", { name: "Skills found in your CV" })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("Completed", { exact: true })).toBeVisible();
  await expect(page.getByText(/Basic analysis mode/)).toBeVisible();

  // Skills are normalized and each one shows the CV text it came from.
  await expect(page.getByText("Spring Boot", { exact: true })).toBeVisible();
  await expect(page.getByText("Amazon Web Services", { exact: true })).toBeVisible();
  await expect(page.getByText("JavaScript", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("blockquote").first()).toBeVisible();

  // Education with evidence.
  await expect(page.getByText("Royal University of Phnom Penh", { exact: false }).first()).toBeVisible();

  // Deselect one skill, confirm the rest.
  await page.getByRole("checkbox", { name: "Add Teamwork" }).click();
  await page.getByRole("button", { name: "Add selected skills to my profile" }).click();
  await expect(page.getByText(/skills added to your profile/)).toBeVisible();
  await expect(page.getByRole("checkbox", { name: "Add Teamwork" })).toBeVisible();

  await page.getByRole("link", { name: "My skills" }).first().click();
  await expect(page.getByRole("heading", { name: "My skills" })).toBeVisible();
  await expect(page.getByText("Java", { exact: true })).toBeVisible();
  await expect(page.getByText("Teamwork", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Remove Docker" }).click();
  await expect(page.getByText("Docker removed from your profile.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Remove Docker" })).toHaveCount(0);

  // Dashboard reflects real data.
  await page.goto("/dashboard");
  await expect(page.getByText("sample-cv.pdf")).toBeVisible();
});

test("DOCX upload, history, download and delete", async ({ page }) => {
  await newCandidate(page);
  await uploadCv(page, "sample-cv.docx");
  await expect(page.getByRole("heading", { name: "Skills found in your CV" })).toBeVisible({ timeout: 30_000 });

  await page.goto("/cv");
  await expect(page.getByText("sample-cv.docx")).toBeVisible();
  await expect(page.getByText("Completed", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Actions for sample-cv.docx" }).click();
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("menuitem", { name: "Download" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("sample-cv.docx");

  await page.getByRole("button", { name: "Actions for sample-cv.docx" }).click();
  await page.getByRole("menuitem", { name: "Analyze again" }).click();
  await expect(page.getByRole("heading", { name: "Skills found in your CV" })).toBeVisible({ timeout: 30_000 });
  await page.goto("/cv");
  await expect(page.getByText("Earlier analyses (1)")).toBeVisible();

  await page.getByRole("button", { name: "Actions for sample-cv.docx" }).click();
  await page.getByRole("menuitem", { name: "Delete" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click();
  await expect(page.getByText("No CVs yet")).toBeVisible();
});

test("instruction-like text in a CV is ignored and flagged", async ({ page }) => {
  await newCandidate(page);
  await uploadCv(page, "injection-cv.pdf");
  await expect(page.getByText(/looked like instructions to an AI system/)).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("Kubernetes", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Rust", { exact: true })).toHaveCount(0);
});

test("an empty or scanned CV fails with a helpful explanation", async ({ page }) => {
  await newCandidate(page);
  await uploadCv(page, "empty-cv.pdf");
  await expect(page.getByText("The analysis didn't complete")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(/scanned image/)).toBeVisible();
  // Re-running the same file can't help, so only "upload a different file" is offered.
  await expect(page.getByRole("button", { name: "Try again" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Upload a different file" })).toBeVisible();
});

test("unsupported and disguised files are rejected", async ({ page }) => {
  await newCandidate(page);
  await uploadCv(page, "not-a-cv.txt");
  await expect(page.getByText("Please upload a PDF or Word (.docx) file.")).toBeVisible();

  // A PNG renamed to .pdf passes the browser check but is rejected by the server.
  await page.getByTestId("cv-file-input").setInputFiles({
    name: "cv.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0]),
  });
  await expect(page.getByText(/does not match its .pdf extension/)).toBeVisible();
  await expect(page.getByText("No CVs yet")).toBeVisible();
});

test("another user's analysis is not accessible", async ({ page, browser }) => {
  await newCandidate(page);
  await uploadCv(page, "sample-cv.pdf");
  await expect(page.getByRole("heading", { name: "Skills found in your CV" })).toBeVisible({ timeout: 30_000 });
  const url = page.url();

  const context = await browser.newContext();
  const other = await context.newPage();
  await register(other, uniqueEmail("intruder"));
  await completeOnboarding(other);
  await other.goto(url);
  await expect(other.getByText("Analysis not found", { exact: true })).toBeVisible();
  await expect(other.getByText("sample-cv.pdf")).toHaveCount(0);
  await context.close();
});
