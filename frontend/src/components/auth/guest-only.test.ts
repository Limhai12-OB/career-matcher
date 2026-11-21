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
    // Parsers strip tab/newline: "/\t/evil.example" would become "//evil.example".
    ["/\t/evil.example/x", null],
    ["/\n/evil.example", null],
    ["/\r//evil.example", null],
    ["/ /evil.example", null],
    ["/\u0000/evil.example", null],
    ["/jobs/../settings", "/settings"],
    ["/jobs?q=a%20b#top", "/jobs?q=a%20b#top"],
  ])("%s -> %s", (input, expected) => {
    expect(safeNextPath(input)).toBe(expected);
  });
});

it("decoded query parameters cannot smuggle a tab", () => {
  const next = new URLSearchParams("next=/%09/evil.example").get("next");
  expect(safeNextPath(next)).toBeNull();
});
