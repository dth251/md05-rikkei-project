import { afterEach, describe, expect, it, vi } from "vitest";

const session = {
  accessToken: "old-token",
  refreshToken: "rotation-token",
  expiresIn: 900,
  user: {
    id: 1,
    role: "USER" as const,
    fullName: "Fixture",
    email: "fixture@example.test",
    createdAt: "2026-10-15",
  },
};
const response = (data: unknown, status = 200) =>
  new Response(
    JSON.stringify({ success: status < 400, data, message: "Fixture" }),
    { status, headers: { "Content-Type": "application/json" } },
  );
afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetModules();
});
describe("API session and mutation handling", () => {
  it("rotates refresh once for concurrent expired requests and replays them with the new token", async () => {
    const { api, saveSession } = await import("./api");
    saveSession(session);
    const fetch = vi.fn(
      async (url: string | URL | Request, options?: RequestInit) => {
        if (String(url).endsWith("/refresh")) {
          await new Promise((resolve) => setTimeout(resolve, 10));
          return response({
            ...session,
            accessToken: "new-token",
            refreshToken: "rotated-token",
          });
        }
        return new Headers(options?.headers).get("Authorization") ===
          "Bearer old-token"
          ? response(null, 401)
          : response({ id: 1 });
      },
    );
    vi.stubGlobal("fetch", fetch);
    const [a, b] = await Promise.all([api("/orders/1"), api("/orders/2")]);
    expect(a).toEqual({ id: 1 });
    expect(b).toEqual({ id: 1 });
    expect(
      fetch.mock.calls.filter(([url]) => String(url).endsWith("/refresh")),
    ).toHaveLength(1);
    const stored = JSON.parse(sessionStorage.getItem("md05.session") || "{}");
    expect(stored.refreshToken).toBe("rotated-token");
  });
  it("keeps the mutation idempotency key on the single retry after token refresh", async () => {
    const { api, saveSession } = await import("./api");
    saveSession(session);
    let count = 0;
    const fetch = vi.fn(async (url: string | URL | Request) =>
      String(url).endsWith("/refresh")
        ? response({ ...session, accessToken: "new-token" })
        : ++count === 1
          ? response(null, 401)
          : response({ id: 12 }),
    );
    vi.stubGlobal("fetch", fetch);
    await api("/orders", {
      method: "POST",
      body: {
        idempotencyKey: "same-request",
        items: [{ productId: 1, quantity: 1 }],
      },
    });
    const requests = fetch.mock.calls.filter(
      ([url]) => String(url) === "/api/orders",
    );
    expect(requests).toHaveLength(2);
    const all = fetch.mock.calls as unknown as [string, RequestInit][];
    expect(
      all
        .filter(([url]) => url === "/api/orders")
        .map(([, options]) => JSON.parse(String(options.body)).idempotencyKey),
    ).toEqual(["same-request", "same-request"]);
  });
  it("does not retry a business conflict or claim success for an error response", async () => {
    const { api } = await import("./api");
    const fetch = vi.fn(async () => response(null, 409));
    vi.stubGlobal("fetch", fetch);
    await expect(
      api("/orders", { method: "POST", body: {} }),
    ).rejects.toMatchObject({ status: 409 });
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it("keeps the session if refresh is temporarily unavailable and handles empty 204 responses", async () => {
    const { api, saveSession, getSession } = await import("./api");
    saveSession(session);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        response(null, url.endsWith("/refresh") ? 503 : 401),
      ),
    );
    await expect(api("/orders")).rejects.toMatchObject({ status: 503 });
    expect(getSession()?.accessToken).toBe("old-token");
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(null, { status: 204 })),
    );
    await expect(
      api("/auth/logout", { method: "POST", body: {} }),
    ).resolves.toBeUndefined();
  });
});
