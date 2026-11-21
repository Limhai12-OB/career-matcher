"use client";

import { Plus, X } from "lucide-react";
import { useMemo, useState } from "react";

import { Input } from "@/components/ui/input";
import { useSkillCatalog } from "@/lib/api/hooks";
import type { SkillRequirement } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface Selected {
  skillId: string;
  requirement: SkillRequirement;
}

/** Pick catalog skills and mark each as required or preferred. */
export function SkillPicker({
  value,
  onChange,
  invalid,
}: {
  value: Selected[];
  onChange: (v: Selected[]) => void;
  invalid?: boolean;
}) {
  const catalog = useSkillCatalog();
  const [query, setQuery] = useState("");
  const byId = useMemo(() => new Map((catalog.data ?? []).map((s) => [s.id, s])), [catalog.data]);
  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    const chosen = new Set(value.map((v) => v.skillId));
    return (catalog.data ?? []).filter((s) => !chosen.has(s.id) && s.name.toLowerCase().includes(q)).slice(0, 8);
  }, [query, catalog.data, value]);

  return (
    <div className="space-y-3">
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-2" aria-label="Selected skills">
          {value.map((s) => {
            const skill = byId.get(s.skillId);
            const required = s.requirement === "REQUIRED";
            return (
              <li key={s.skillId} className="inline-flex items-center gap-1 rounded-full border bg-card py-1 pr-1 pl-3 text-sm">
                {skill?.name ?? "…"}
                <button
                  type="button"
                  onClick={() =>
                    onChange(value.map((v) => (v.skillId === s.skillId ? { ...v, requirement: required ? "PREFERRED" : "REQUIRED" } : v)))
                  }
                  className={cn(
                    "rounded-full px-2 py-0.5 text-xs font-medium",
                    required ? "bg-primary text-primary-foreground" : "bg-secondary text-secondary-foreground",
                  )}
                  aria-label={`${skill?.name}: ${required ? "required" : "preferred"}. Toggle`}
                >
                  {required ? "Required" : "Preferred"}
                </button>
                <button
                  type="button"
                  onClick={() => onChange(value.filter((v) => v.skillId !== s.skillId))}
                  className="rounded-full p-1 text-muted-foreground hover:bg-foreground/10"
                  aria-label={`Remove ${skill?.name}`}
                >
                  <X className="size-3.5" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <div className="relative">
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={catalog.isPending ? "Loading skill catalog…" : "Search skills to add"}
          aria-label="Search skills to add"
          aria-invalid={invalid || undefined}
          disabled={value.length >= 30}
        />
        {matches.length > 0 && (
          <ul className="absolute z-10 mt-1 w-full overflow-hidden rounded-lg border bg-popover shadow-md" role="listbox">
            {matches.map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={false}
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-accent"
                  onClick={() => {
                    onChange([...value, { skillId: s.id, requirement: value.some((v) => v.requirement === "REQUIRED") ? "PREFERRED" : "REQUIRED" }]);
                    setQuery("");
                  }}
                >
                  <Plus className="size-3.5" aria-hidden /> {s.name}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
