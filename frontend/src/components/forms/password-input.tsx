"use client";

import { Eye, EyeOff } from "lucide-react";
import * as React from "react";

import { Input } from "@/components/ui/input";

export const PasswordInput = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  function PasswordInput(props, ref) {
    const [visible, setVisible] = React.useState(false);
    return (
      <div className="relative">
        <Input ref={ref} type={visible ? "text" : "password"} className="pr-10" {...props} />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          className="absolute top-1/2 right-2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
          aria-label={visible ? "Hide password" : "Show password"}
          aria-pressed={visible}
        >
          {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
    );
  },
);

/** Live checklist matching the backend password policy. */
export function PasswordRequirements({ value }: { value: string }) {
  const rules = [
    { ok: value.length >= 10, label: "At least 10 characters" },
    { ok: /\p{L}/u.test(value), label: "A letter" },
    { ok: /\p{Nd}/u.test(value), label: "A number" },
  ];
  return (
    <ul className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs" aria-label="Password requirements">
      {rules.map((r) => (
        <li
          key={r.label}
          className={r.ok ? "text-emerald-600" : "text-muted-foreground"}
          aria-label={`${r.label}: ${r.ok ? "met" : "not met"}`}
        >
          {r.ok ? "✓" : "○"} {r.label}
        </li>
      ))}
    </ul>
  );
}
