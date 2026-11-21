/**
 * Client-side session store.
 *
 * - The access token lives only in memory (never localStorage) to limit XSS impact.
 * - The refresh token is an HttpOnly cookie managed by the backend; on page load the session is
 *   restored by calling /auth/refresh.
 * - Concurrent refreshes are coalesced (single flight). A 401 on an API call triggers one refresh
 *   and one retry.
 * - Sign-out is broadcast to other tabs.
 */
import { ApiError } from "@/lib/api/errors";
import { http, type HttpOptions } from "@/lib/api/http";
import type { AuthResponse, UserSummary } from "@/lib/api/types";

export type SessionStatus = "loading" | "authenticated" | "unauthenticated";

export interface SessionState {
  status: SessionStatus;
  user: UserSummary | null;
  /** Where to send the user after an intentional sign-out/account deletion (vs. expiry). */
  exitTo?: string;
}

const LOADING: SessionState = { status: "loading", user: null };

let state: SessionState = LOADING;
let accessToken: string | null = null;
let refreshInFlight: Promise<boolean> | null = null;
let initialized = false;
const listeners = new Set<() => void>();

const channel: BroadcastChannel | null =
  typeof window !== "undefined" && "BroadcastChannel" in window
    ? new BroadcastChannel("career-matcher-auth")
    : null;

channel?.addEventListener("message", (event: MessageEvent) => {
  if (event.data === "logout") {
    applyAuth(null);
  } else if (event.data === "login" && state.status !== "authenticated") {
    void refreshSession();
  }
});

function emit() {
  listeners.forEach((l) => l());
}

function applyAuth(auth: AuthResponse | null, exitTo?: string) {
  accessToken = auth?.accessToken ?? null;
  state = auth
    ? { status: "authenticated", user: auth.user }
    : { status: "unauthenticated", user: null, exitTo };
  emit();
}

export const sessionStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  getSnapshot: (): SessionState => state,
  getServerSnapshot: (): SessionState => LOADING,
};

/** Restores the session from the refresh cookie once per page load. */
export function initSession(): void {
  if (initialized) return;
  initialized = true;
  void refreshSession().catch(() => applyAuth(null));
}

/**
 * Rotates the refresh cookie and obtains a new access token.
 * Resolves true on success, false if there is no valid session. Network errors reject.
 */
export function refreshSession(): Promise<boolean> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function doRefresh(attempt = 0): Promise<boolean> {
  try {
    const auth = await http<AuthResponse>("/api/v1/auth/refresh", {
      method: "POST",
      withCredentials: true,
    });
    applyAuth(auth);
    return true;
  } catch (err) {
    // Another tab rotated the cookie a moment ago; the browser now holds the new one.
    if (err instanceof ApiError && err.code === "REFRESH_TOKEN_ROTATED" && attempt === 0) {
      await new Promise((r) => setTimeout(r, 150));
      return doRefresh(1);
    }
    if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
      applyAuth(null);
      return false;
    }
    throw err;
  }
}

export async function login(email: string, password: string): Promise<UserSummary> {
  const auth = await http<AuthResponse>("/api/v1/auth/login", {
    method: "POST",
    body: { email, password },
    withCredentials: true,
  });
  applyAuth(auth);
  channel?.postMessage("login");
  return auth.user;
}

export async function register(
  email: string,
  password: string,
  fullName: string,
): Promise<UserSummary> {
  const auth = await http<AuthResponse>("/api/v1/auth/register", {
    method: "POST",
    body: { email, password, fullName },
    withCredentials: true,
  });
  applyAuth(auth);
  channel?.postMessage("login");
  return auth.user;
}

export async function logout(): Promise<void> {
  try {
    await http<void>("/api/v1/auth/logout", { method: "POST", withCredentials: true });
  } finally {
    // Always clear local state, even if the server call fails.
    applyAuth(null, "/login");
    channel?.postMessage("logout");
  }
}

/** Replaces the session after an operation that issues new tokens (e.g. password change). */
export function replaceSession(auth: AuthResponse): void {
  applyAuth(auth);
}

/** Clears local state after the account was deleted server-side. */
export function endSessionLocally(exitTo = "/"): void {
  applyAuth(null, exitTo);
  channel?.postMessage("logout");
}

export function updateCurrentUser(patch: Partial<UserSummary>): void {
  if (state.user) {
    state = { ...state, user: { ...state.user, ...patch } };
    emit();
  }
}

/** Authorized API request with transparent refresh-and-retry on 401. */
export async function apiRequest<T>(
  path: string,
  options: Omit<HttpOptions, "accessToken"> = {},
): Promise<T> {
  if (!accessToken && state.status !== "unauthenticated") {
    await refreshSession();
  }
  try {
    return await http<T>(path, { ...options, accessToken });
  } catch (err) {
    if (!(err instanceof ApiError) || err.status !== 401) throw err;
    const refreshed = await refreshSession();
    if (!refreshed) throw err;
    return http<T>(path, { ...options, accessToken });
  }
}

/** Test helper: resets module state. */
export function __resetSessionForTests(): void {
  state = LOADING;
  accessToken = null;
  refreshInFlight = null;
  initialized = false;
  listeners.clear();
}
