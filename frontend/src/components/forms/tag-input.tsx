"use client";

import { Plus, X } from "lucide-react";
import { useId, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";

interface TagInputProps {
  value: string[];
  onChange: (value: string[]) => void;
  placeholder?: string;
  max?: number;
  maxLength?: number;
  suggestions?: string[];
  invalid?: boolean;
  id?: string;
  "aria-describedby"?: string;
}

/** Free-text list input: type and press Enter (or comma) to add; click × to remove. */
export function TagInput({
  value,
  onChange,
  placeholder,
  max = 10,
  maxLength = 80,
  suggestions = [],
  invalid,
  id,
  ...aria
}: TagInputProps) {
  const [draft, setDraft] = useState("");
  const hintId = useId();
  const lower = new Set(value.map((v) => v.toLowerCase()));
  const full = value.length >= max;

  function add(raw: string) {
    const tag = raw.trim().slice(0, maxLength);
    if (!tag || lower.has(tag.toLowerCase()) || full) return;
    onChange([...value, tag]);
    setDraft("");
  }

  return (
    <div className="space-y-2">
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label="Selected">
          {value.map((tag) => (
            <li key={tag}>
              <Badge variant="secondary" className="gap-1 py-1 pr-1 pl-2.5 text-sm font-normal">
                {tag}
                <button
                  type="button"
                  className="rounded-full p-0.5 hover:bg-foreground/10 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                  onClick={() => onChange(value.filter((v) => v !== tag))}
                  aria-label={`Remove ${tag}`}
                >
                  <X className="size-3" />
                </button>
              </Badge>
            </li>
          ))}
        </ul>
      )}
      <Input
        id={id}
        value={draft}
        disabled={full}
        placeholder={full ? `Maximum of ${max} reached` : placeholder}
        maxLength={maxLength}
        aria-invalid={invalid || undefined}
        aria-describedby={[aria["aria-describedby"], hintId].filter(Boolean).join(" ")}
        onChange={(e) => setDraft(e.target.value.replace(",", ""))}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === ",") {
            e.preventDefault();
            add(draft);
          } else if (e.key === "Backspace" && !draft && value.length) {
            onChange(value.slice(0, -1));
          }
        }}
        onBlur={() => add(draft)}
      />
      <p id={hintId} className="sr-only">
        Press Enter to add.
      </p>
      {suggestions.some((s) => !lower.has(s.toLowerCase())) && !full && (
        <div className="flex flex-wrap gap-1.5">
          {suggestions
            .filter((s) => !lower.has(s.toLowerCase()))
            .map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => add(s)}
                className="inline-flex items-center gap-1 rounded-full border border-dashed px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:border-primary hover:text-primary"
              >
                <Plus className="size-3" aria-hidden /> {s}
              </button>
            ))}
        </div>
      )}
    </div>
  );
}
