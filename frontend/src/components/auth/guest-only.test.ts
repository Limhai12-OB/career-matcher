import { describe, expect, it } from "vitest";

import { safeNextPath } from "./guest-only";

describe("safeNextPath", () => {
  it.each([
    ["/profile", "/profile"],
    ["/settings?tab=1", "/settings?tab=1"],
    [null, null],
    ["https://evil.example", null],
    ["//evil.example", null],
    ["/\\evil.example", null],
    ["javascript:alert(1)", null],
  ])("%s -> %s", (input, expected) => {
    expect(safeNextPath(input)).toBe(expected);
  });
});
