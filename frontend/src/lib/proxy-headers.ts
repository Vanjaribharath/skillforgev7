import type { NextRequest } from "next/server";

// Headers that must never be blindly copied from the incoming request onto
// the upstream fetch. In particular "accept-encoding" tells the backend it's
// safe to gzip the response, and "host"/"content-length"/"connection" are
// hop-by-hop or request-specific and don't apply once we re-issue the call.
const SKIP_REQUEST_HEADERS = new Set(["host", "connection", "content-length", "accept-encoding"]);

// Headers that must never be blindly copied from the upstream response onto
// the response we hand back to the browser. Node's fetch() transparently
// gunzips the upstream body for us, so response.body is already plain text --
// but response.headers still says "content-encoding: gzip" (and carries the
// original compressed content-length). Forwarding those verbatim makes the
// browser try to gunzip data that isn't gzipped anymore, which fails with
// net::ERR_CONTENT_DECODING_FAILED and looks like the request never
// succeeded (surfacing as "Invalid email or password" on the login page).
const SKIP_RESPONSE_HEADERS = new Set(["content-encoding", "content-length", "transfer-encoding", "connection"]);

export function forwardRequestHeaders(request: NextRequest): Headers {
  const headers = new Headers();
  request.headers.forEach((value, key) => {
    if (!SKIP_REQUEST_HEADERS.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  });
  return headers;
}

export function forwardResponseHeaders(response: Response): Headers {
  const headers = new Headers();
  response.headers.forEach((value, key) => {
    if (!SKIP_RESPONSE_HEADERS.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  });
  return headers;
}
