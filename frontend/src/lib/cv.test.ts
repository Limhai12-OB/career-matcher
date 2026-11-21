import { describe, expect, it } from "vitest";

import {
  analysisErrorMessage,
  dateRange,
  formatBytes,
  formatPartialDate,
  groupByCategory,
  providerLabel,
  validateCvFile,
} from "./cv";

describe("validateCvFile", () => {
  it.each([
    [{ name: "cv.pdf", size: 1000 }, null],
    [{ name: "My CV.DOCX", size: 1000 }, null],
    [{ name: "cv.doc", size: 1000 }, "Please upload a PDF or Word (.docx) file."],
    [{ name: "cv.pdf.exe", size: 1000 }, "Please upload a PDF or Word (.docx) file."],
    [{ name: "cv.pdf", size: 0 }, "This file is empty."],
    [{ name: "cv.pdf", size: 5 * 1024 * 1024 + 1 }, "The file is larger than 5 MB."],
  ])("%o -> %s", (file, expected) => {
    expect(validateCvFile(file)).toBe(expected);
  });
});

describe("formatting", () => {
  it("formats sizes and dates", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(2048)).toBe("2 KB");
    expect(formatBytes(3.5 * 1024 * 1024)).toBe("3.5 MB");
    expect(formatPartialDate("2024")).toBe("2024");
    expect(formatPartialDate("2024-06")).toBe("Jun 2024");
    expect(dateRange("2024-06", null, true)).toBe("Jun 2024 – Present");
    expect(dateRange(null, null, false)).toBeNull();
  });

  it("explains failures with a safe fallback", () => {
    expect(analysisErrorMessage("NO_EXTRACTABLE_TEXT")).toMatch(/scanned image/);
    expect(analysisErrorMessage("SOMETHING_NEW")).toMatch(/Something went wrong/);
    expect(analysisErrorMessage(null)).toMatch(/Something went wrong/);
  });

  it("labels providers honestly", () => {
    expect(providerLabel("local", "rule-based-v1")).toMatch(/no AI/);
    expect(providerLabel("anthropic", "claude-opus-5-5")).toContain("claude-opus-5-5");
  });

  it("groups skills by category in a fixed order and drops empty groups", () => {
    const groups = groupByCategory([
      { category: "TOOL" as const, n: 1 },
      { category: "PROGRAMMING_LANGUAGE" as const, n: 2 },
      { category: "TOOL" as const, n: 3 },
    ]);
    expect(groups.map((g) => g.label)).toEqual(["Languages", "Tools"]);
    expect(groups[1].items).toHaveLength(2);
  });
});
