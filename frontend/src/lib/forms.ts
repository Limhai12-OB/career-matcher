import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

import { ApiError } from "@/lib/api/errors";

/**
 * Copies backend field errors onto a react-hook-form instance.
 * Returns true if at least one error was attached to a known field.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly string[],
): boolean {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) return false;
  let applied = false;
  for (const fe of error.fieldErrors) {
    // Backend paths like "desiredRoles[2]" map to the array field.
    const field = fe.field.replace(/\[\d+\]$/, "");
    if (knownFields.includes(field)) {
      setError(field as Path<T>, { type: "server", message: fe.message });
      applied = true;
    }
  }
  return applied;
}
