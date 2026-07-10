import { NextRequest, NextResponse } from "next/server";
import { forwardRequestHeaders, forwardResponseHeaders } from "@/lib/proxy-headers";

const API_URL = process.env.BACKEND_API_URL ?? "http://localhost:8080/api/v1";

async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const params = await context.params;
  const url = `${API_URL}/${params.path.join("/")}${request.nextUrl.search}`;
  const hasBody = !(request.method === "GET" || request.method === "HEAD");

  const response = await fetch(url, {
    method: request.method,
    headers: forwardRequestHeaders(request),
    body: hasBody ? await request.text() : undefined,
    cache: "no-store",
  });

  // Buffer the (already-decoded) body ourselves instead of streaming
  // response.body straight through, so NextResponse can compute a correct
  // Content-Length for the bytes we're actually sending.
  const body = await response.arrayBuffer();

  return new NextResponse(body, {
    status: response.status,
    headers: forwardResponseHeaders(response),
  });
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as PATCH, proxy as DELETE };
