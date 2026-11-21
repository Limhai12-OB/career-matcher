import { describe, expect, it } from "vitest";

import { parseBackendHealth } from "./health";

describe("parseBackendHealth", () => {
  it("maps actuator components to labelled dependencies", () => {
    const result = parseBackendHealth({
      status: "DOWN",
      components: { db: { status: "UP" }, aiService: { status: "DOWN" } },
    });
    expect(result).toEqual({
      backend: "UP",
      dependencies: [
        { name: "Database", state: "UP" },
        { name: "AI service", state: "DOWN" },
      ],
    });
  });

  it("treats malformed payloads as backend DOWN", () => {
    expect(parseBackendHealth(null)).toEqual({ backend: "DOWN", dependencies: [] });
    expect(parseBackendHealth({ foo: 1 })).toEqual({ backend: "DOWN", dependencies: [] });
  });

  it("treats unknown component statuses as DOWN", () => {
    const result = parseBackendHealth({ status: "UP", components: { db: { status: "WEIRD" } } });
    expect(result.dependencies).toEqual([{ name: "Database", state: "DOWN" }]);
  });
});
