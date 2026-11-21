export interface FieldErrorItem {
  field: string;
  message: string;
}

/** A failed API call, normalized from RFC 7807 problem responses. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: FieldErrorItem[] = [],
    readonly extra: Record<string, unknown> = {},
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function networkError(): ApiError {
  return new ApiError(0, "NETWORK_ERROR", "Unable to reach the server.");
}

const FALLBACK_MESSAGES: Record<number, string> = {
  400: "The request was invalid.",
  401: "Please sign in to continue.",
  403: "You don't have permission to do that.",
  404: "Not found.",
  409: "That conflicts with existing data.",
  429: "Too many requests. Please try again later.",
};

export async function toApiError(res: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    body = (await res.json()) as Record<string, unknown>;
  } catch {
    // Non-JSON error body: fall back to status-based message.
  }
  const { code, detail, errors, ...extra } = body;
  const retryAfter = Number(res.headers.get("Retry-After"));
  return new ApiError(
    res.status,
    typeof code === "string" ? code : `HTTP_${res.status}`,
    typeof detail === "string"
      ? detail
      : (FALLBACK_MESSAGES[res.status] ?? "Something went wrong. Please try again."),
    Array.isArray(errors) ? (errors as FieldErrorItem[]) : [],
    extra,
    Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
  );
}

/** A user-facing message for any error thrown by the API layer. */
export function userMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "NETWORK_ERROR":
        return "Can't reach the server. Check your connection and try again.";
      case "RATE_LIMITED":
        return error.retryAfterSeconds
          ? `Too many attempts. Try again in ${formatWait(error.retryAfterSeconds)}.`
          : "Too many attempts. Please try again later.";
      case "INTERNAL_ERROR":
        return "Something went wrong on our side. Please try again.";
      case "VALIDATION_FAILED":
        // Surface the specific field messages when the form can't attach them to inputs.
        return error.fieldErrors.length
          ? error.fieldErrors.map((e) => e.message).join(". ")
          : error.message;
      default:
        return error.message;
    }
  }
  return "Something went wrong. Please try again.";
}

function formatWait(seconds: number): string {
  if (seconds < 60) return `${seconds} second${seconds === 1 ? "" : "s"}`;
  const minutes = Math.ceil(seconds / 60);
  return `${minutes} minute${minutes === 1 ? "" : "s"}`;
}
