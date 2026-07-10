import { describe, expect, it, beforeEach, afterEach } from "vitest";
import axios from "axios";
import MockAdapter from "axios-mock-adapter";
import { api } from "./api-client";

describe("api-client 401 refresh flow", () => {
  let apiMock: MockAdapter;
  let rawAxiosMock: MockAdapter;

  beforeEach(() => {
    window.localStorage.clear();
    apiMock = new MockAdapter(api);
    // refreshAccessToken() deliberately calls the raw `axios` module (not
    // the `api` instance) so the refresh call itself never re-enters this
    // same 401-handling interceptor.
    rawAxiosMock = new MockAdapter(axios);
    // jsdom throws on real navigation; no-op it so clearSessionAndRedirect()
    // doesn't crash the test when the refresh token is missing/invalid.
    delete (window as any).location;
    (window as any).location = { pathname: "/", href: "" };
  });

  afterEach(() => {
    apiMock.restore();
    rawAxiosMock.restore();
  });

  it("silently refreshes and retries the original request once on a 401", async () => {
    window.localStorage.setItem("executionos.accessToken", "expired-token");
    window.localStorage.setItem("executionos.refreshToken", "valid-refresh-token");

    let firstAttempt = true;
    apiMock.onGet("/candidates").reply((config) => {
      if (firstAttempt) {
        firstAttempt = false;
        return [401, { error: "Token expired" }];
      }
      // Second attempt should carry the newly-refreshed token.
      expect(config.headers?.Authorization).toBe("Bearer new-access-token");
      return [200, [{ id: "1", fullName: "Aarav Sharma" }]];
    });

    rawAxiosMock.onPost(/\/auth\/refresh$/).reply(200, {
      accessToken: "new-access-token",
      refreshToken: "new-refresh-token",
      user: { id: "u1", organizationId: "org1", email: "admin@apex.example", fullName: "Admin", role: "ORG_ADMIN", status: "ACTIVE" },
    });

    const res = await api.get("/candidates");

    expect(res.data).toEqual([{ id: "1", fullName: "Aarav Sharma" }]);
    expect(window.localStorage.getItem("executionos.accessToken")).toBe("new-access-token");
    expect(window.localStorage.getItem("executionos.refreshToken")).toBe("new-refresh-token");
  });

  it("clears the session and stops retrying if the refresh token is also invalid", async () => {
    window.localStorage.setItem("executionos.accessToken", "expired-token");
    window.localStorage.setItem("executionos.refreshToken", "also-expired-refresh-token");

    apiMock.onGet("/candidates").reply(401, { error: "Token expired" });
    rawAxiosMock.onPost(/\/auth\/refresh$/).reply(401, { error: "Invalid refresh token" });

    await expect(api.get("/candidates")).rejects.toBeTruthy();

    expect(window.localStorage.getItem("executionos.accessToken")).toBeNull();
    expect(window.localStorage.getItem("executionos.refreshToken")).toBeNull();
    expect(window.localStorage.getItem("executionos.user")).toBeNull();
  });

  it("does not attempt a refresh loop when there is no refresh token at all", async () => {
    window.localStorage.setItem("executionos.accessToken", "expired-token");
    // No refresh token stored.

    let callCount = 0;
    apiMock.onGet("/candidates").reply(() => {
      callCount++;
      return [401, { error: "Token expired" }];
    });

    await expect(api.get("/candidates")).rejects.toBeTruthy();
    expect(callCount).toBe(1); // never retried
  });
});
