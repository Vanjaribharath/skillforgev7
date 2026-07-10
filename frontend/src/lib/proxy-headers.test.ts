import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { forwardRequestHeaders, forwardResponseHeaders } from "./proxy-headers";

describe("forwardResponseHeaders", () => {
  it("strips Content-Encoding so the browser never tries to re-decode an already-decoded body", () => {
    // fetch() transparently gunzips the upstream body for us, but leaves
    // response.headers exactly as the upstream sent them. If we forwarded
    // this verbatim, the browser would see "Content-Encoding: gzip" on a
    // plain-text body and fail with net::ERR_CONTENT_DECODING_FAILED --
    // which is exactly what made every login look like "Invalid email or
    // password" even when the backend returned a valid 200 with a token.
    const upstream = new Response("{}", {
      status: 200,
      headers: {
        "Content-Type": "application/json",
        "Content-Encoding": "gzip",
        "Content-Length": "9999",
        "Transfer-Encoding": "chunked",
        Connection: "keep-alive",
        "X-Custom-Header": "keep-me",
      },
    });

    const filtered = forwardResponseHeaders(upstream);

    expect(filtered.get("content-encoding")).toBeNull();
    expect(filtered.get("content-length")).toBeNull();
    expect(filtered.get("transfer-encoding")).toBeNull();
    expect(filtered.get("connection")).toBeNull();
    // Headers that aren't hop-by-hop / encoding-related must survive.
    expect(filtered.get("content-type")).toBe("application/json");
    expect(filtered.get("x-custom-header")).toBe("keep-me");
  });
});

describe("forwardRequestHeaders", () => {
  it("strips accept-encoding/host/connection/content-length before re-issuing the request upstream", () => {
    const incoming = new NextRequest("http://localhost:3000/api/skillforge/auth/login", {
      method: "POST",
      headers: {
        Host: "localhost:3000",
        Connection: "keep-alive",
        "Content-Length": "42",
        "Accept-Encoding": "gzip, deflate, br",
        Authorization: "Bearer some.jwt.token",
        "Content-Type": "application/json",
      },
    });

    const filtered = forwardRequestHeaders(incoming);

    expect(filtered.get("host")).toBeNull();
    expect(filtered.get("connection")).toBeNull();
    expect(filtered.get("content-length")).toBeNull();
    expect(filtered.get("accept-encoding")).toBeNull();
    // Auth and content-type must still reach the backend.
    expect(filtered.get("authorization")).toBe("Bearer some.jwt.token");
    expect(filtered.get("content-type")).toBe("application/json");
  });
});
