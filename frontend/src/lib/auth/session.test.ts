import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api/errors";

import {
  __resetSessionForTests,
  apiRequest,
  login,
  logout,
  refreshSession,
  sessionStore,
} from "./session";

const user = {
  id: "u1",
  email: "a@example.com",
  role: "CANDIDATE",
  onboardingCompleted: false,
  createdAt: "2026-01-01T00:00:00Z",
};
const auth = (token: string) => ({ accessToken: token, tokenType: "Bearer", expiresIn: 900, user });

function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>;
let handler: Handler;
const fetchMock = vi.fn((url: string, init: RequestInit) => handler(url, init));

beforeEach(() => {
  __resetSessionForTests();
  fetchMock.mockClear();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

const calls = (path: string) => fetchMock.mock.calls.filter(([u]) => String(u).endsWith(path));

describe("session", () => {
  it("coalesces concurrent refreshes into one request", async () => {
    handler = () => json(200, auth("t1"));
    const results = await Promise.all([refreshSession(), refreshSession(), refreshSession()]);
    expect(results).toEqual([true, true, true]);
    expect(calls("/api/v1/auth/refresh")).toHaveLength(1);
    expect(sessionStore.getSnapshot()).toMatchObject({ status: "authenticated", user });
  });

  it("sends the refresh request with cookies but login/API calls without", async () => {
    handler = (url) => (url.endsWith("/auth/refresh") ? json(200, auth("t1")) : json(200, {}));
    await refreshSession();
    await apiRequest("/api/v1/me/profile");
    expect(calls("/auth/refresh")[0][1].credentials).toBe("include");
    expect(calls("/me/profile")[0][1].credentials).toBe("omit");
  });

  it("marks the session unauthenticated when refresh is rejected", async () => {
    handler = () => json(401, { code: "INVALID_REFRESH_TOKEN", detail: "expired" });
    await expect(refreshSession()).resolves.toBe(false);
    expect(sessionStore.getSnapshot().status).toBe("unauthenticated");
  });

  it("retries once when another tab rotated the refresh cookie", async () => {
    let n = 0;
    handler = () =>
      ++n === 1 ? json(401, { code: "REFRESH_TOKEN_ROTATED" }) : json(200, auth("t2"));
    await expect(refreshSession()).resolves.toBe(true);
    expect(n).toBe(2);
  });

  it("attaches the access token and refreshes + retries once on 401", async () => {
    let profileCalls = 0;
    let token = "t1";
    handler = (url, init) => {
      if (url.endsWith("/auth/login")) return json(200, auth("t1"));
      if (url.endsWith("/auth/refresh")) {
        token = "t2";
        return json(200, auth("t2"));
      }
      profileCalls++;
      const header = (init.headers as Record<string, string>).Authorization;
      return header === `Bearer ${token}` && token === "t2"
        ? json(200, { fullName: "A" })
        : json(401, { code: "INVALID_TOKEN" });
    };
    await login("a@example.com", "pw");
    await expect(apiRequest("/api/v1/me/profile")).resolves.toEqual({ fullName: "A" });
    expect(profileCalls).toBe(2);
    expect(calls("/auth/refresh")).toHaveLength(1);
  });

  it("propagates the 401 when the session cannot be refreshed", async () => {
    handler = (url) =>
      url.endsWith("/auth/login")
        ? json(200, auth("t1"))
        : url.endsWith("/auth/refresh")
          ? json(401, { code: "INVALID_REFRESH_TOKEN" })
          : json(401, { code: "INVALID_TOKEN" });
    await login("a@example.com", "pw");
    await expect(apiRequest("/api/v1/me/profile")).rejects.toMatchObject({ status: 401 });
    expect(sessionStore.getSnapshot().status).toBe("unauthenticated");
  });

  it("clears local state on logout even if the server is unreachable", async () => {
    handler = (url) => {
      if (url.endsWith("/auth/login")) return json(200, auth("t1"));
      throw new TypeError("network down");
    };
    await login("a@example.com", "pw");
    await expect(logout()).rejects.toBeInstanceOf(ApiError);
    expect(sessionStore.getSnapshot().status).toBe("unauthenticated");
  });
});

describe("account endpoints", () => {
  it("send credentials so the rotated refresh cookie is stored", async () => {
    const { accountApi } = await import("@/lib/api/endpoints");
    handler = (url) => (url.endsWith("/auth/login") ? json(200, auth("t1")) : json(200, auth("t2")));
    await login("a@example.com", "pw");
    await accountApi.changePassword("old", "new-password-1");
    expect(calls("/me/password")[0][1].credentials).toBe("include");
  });
});

describe("exit target", () => {
  it("records intentional exits but not expiry", async () => {
    handler = (url) =>
      url.endsWith("/auth/login") ? json(200, auth("t1")) : json(204, undefined);
    await login("a@example.com", "pw");
    await logout();
    expect(sessionStore.getSnapshot().exitTo).toBe("/login");

    handler = () => json(401, { code: "INVALID_REFRESH_TOKEN" });
    await refreshSession();
    expect(sessionStore.getSnapshot().exitTo).toBeUndefined();
  });
});
