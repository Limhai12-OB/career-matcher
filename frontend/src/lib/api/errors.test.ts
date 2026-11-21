import { describe, expect, it } from "vitest";

import { ApiError, networkError, toApiError, userMessage } from "./errors";

describe("toApiError", () => {
  it("parses RFC 7807 problems with field errors and extra properties", async () => {
    const res = new Response(
      JSON.stringify({
        status: 422,
        code: "ONBOARDING_INCOMPLETE",
        detail: "Missing",
        errors: [{ field: "email", message: "bad" }],
        missingRequired: ["countryCode"],
      }),
      { status: 422 },
    );
    const err = await toApiError(res);
    expect(err).toMatchObject({ status: 422, code: "ONBOARDING_INCOMPLETE", message: "Missing" });
    expect(err.fieldErrors).toEqual([{ field: "email", message: "bad" }]);
    expect(err.extra.missingRequired).toEqual(["countryCode"]);
  });

  it("falls back gracefully for non-JSON bodies", async () => {
    const err = await toApiError(new Response("<html>", { status: 502 }));
    expect(err.code).toBe("HTTP_502");
    expect(err.message).toMatch(/something went wrong/i);
  });

  it("reads Retry-After for rate limiting", async () => {
    const err = await toApiError(
      new Response(JSON.stringify({ code: "RATE_LIMITED" }), {
        status: 429,
        headers: { "Retry-After": "120" },
      }),
    );
    expect(err.retryAfterSeconds).toBe(120);
    expect(userMessage(err)).toBe("Too many attempts. Try again in 2 minutes.");
  });
});

describe("userMessage", () => {
  it("uses field messages for validation failures", () => {
    const err = new ApiError(400, "VALIDATION_FAILED", "One or more fields are invalid.", [
      { field: "password", message: "Password is incorrect" },
    ]);
    expect(userMessage(err)).toBe("Password is incorrect");
  });

  it("is friendly for network and unknown errors", () => {
    expect(userMessage(networkError())).toMatch(/can't reach the server/i);
    expect(userMessage(new Error("boom"))).toMatch(/something went wrong/i);
    expect(userMessage(new ApiError(401, "INVALID_CREDENTIALS", "Invalid email or password."))).toBe(
      "Invalid email or password.",
    );
  });
});
