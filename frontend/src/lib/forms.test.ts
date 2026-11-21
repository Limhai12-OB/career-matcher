import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api/errors";

import { applyServerFieldErrors } from "./forms";

describe("applyServerFieldErrors", () => {
  it("maps known fields (including indexed array paths) and ignores unknown ones", () => {
    const setError = vi.fn();
    const err = new ApiError(400, "VALIDATION_FAILED", "invalid", [
      { field: "desiredRoles[2]", message: "too long" },
      { field: "somethingElse", message: "x" },
    ]);
    expect(applyServerFieldErrors(err, setError, ["desiredRoles"])).toBe(true);
    expect(setError).toHaveBeenCalledOnce();
    expect(setError).toHaveBeenCalledWith("desiredRoles", { type: "server", message: "too long" });
  });

  it("returns false for non-field errors", () => {
    expect(applyServerFieldErrors(new Error("x"), vi.fn(), ["a"])).toBe(false);
  });
});
