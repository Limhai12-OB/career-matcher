import { API_BASE_URL } from "@/lib/config";

import { networkError, toApiError } from "./errors";

export interface HttpOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** JSON-serialized unless it is FormData (sent as multipart). */
  body?: unknown;
  /** "blob" returns the raw response body (file downloads). */
  responseType?: "json" | "blob";
  accessToken?: string | null;
  /** Send cookies (only needed for the refresh-cookie auth endpoints). */
  withCredentials?: boolean;
  signal?: AbortSignal;
}

/** Low-level JSON request. Throws {@link ApiError} for non-2xx and network failures. */
export async function http<T>(path: string, options: HttpOptions = {}): Promise<T> {
  const isForm = typeof FormData !== "undefined" && options.body instanceof FormData;
  const headers: Record<string, string> = { Accept: "application/json, */*" };
  // For FormData the browser sets the multipart boundary itself.
  if (options.body !== undefined && !isForm) headers["Content-Type"] = "application/json";
  if (options.accessToken) headers.Authorization = `Bearer ${options.accessToken}`;

  let res: Response;
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method: options.method ?? "GET",
      headers,
      body:
        options.body === undefined
          ? undefined
          : isForm
            ? (options.body as FormData)
            : JSON.stringify(options.body),
      credentials: options.withCredentials ? "include" : "omit",
      cache: "no-store",
      signal: options.signal,
    });
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw networkError();
  }
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;
  if (options.responseType === "blob") return (await res.blob()) as T;
  return (await res.json()) as T;
}
