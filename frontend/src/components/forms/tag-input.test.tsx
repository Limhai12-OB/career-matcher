import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { TagInput } from "./tag-input";

function Harness({ initial = [] as string[], max = 3 }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <TagInput value={value} onChange={setValue} max={max} suggestions={["QA Engineer"]} placeholder="Add" />
      <output data-testid="value">{value.join("|")}</output>
    </>
  );
}

describe("TagInput", () => {
  it("adds on Enter, ignores case-insensitive duplicates, removes via button", () => {
    render(<Harness />);
    const input = screen.getByPlaceholderText("Add");
    fireEvent.change(input, { target: { value: "Backend Dev" } });
    fireEvent.keyDown(input, { key: "Enter" });
    fireEvent.change(input, { target: { value: "backend dev" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(screen.getByTestId("value").textContent).toBe("Backend Dev");

    fireEvent.click(screen.getByText("QA Engineer"));
    expect(screen.getByTestId("value").textContent).toBe("Backend Dev|QA Engineer");

    fireEvent.click(screen.getByLabelText("Remove Backend Dev"));
    expect(screen.getByTestId("value").textContent).toBe("QA Engineer");
  });

  it("stops accepting input at the maximum", () => {
    render(<Harness initial={["a", "b", "c"]} />);
    expect(screen.getByPlaceholderText("Maximum of 3 reached")).toHaveProperty("disabled", true);
  });
});
