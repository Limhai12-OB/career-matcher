import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { StatusBadge } from "./status-badge";

describe("StatusBadge", () => {
  it("renders the state label", () => {
    render(<StatusBadge state="UNREACHABLE" />);
    expect(screen.getByText("UNREACHABLE")).toBeDefined();
  });
});
