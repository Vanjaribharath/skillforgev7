"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={null}>
      <VerifyEmailForm />
    </Suspense>
  );
}

function VerifyEmailForm() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [token, setToken] = useState(searchParams.get("token") ?? "");
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">("idle");
  const [message, setMessage] = useState<string | null>(null);

  async function verify(tokenValue: string) {
    setStatus("loading");
    setMessage(null);
    try {
      await api.post("/auth/verify-email", { token: tokenValue });
      setStatus("success");
    } catch (err: any) {
      setStatus("error");
      setMessage(err?.response?.data?.error || "Verification link is invalid or has expired.");
    }
  }

  // Auto-verify if the link already carried a token in the URL — the
  // manual input below is only needed if someone copies just the token
  // string instead of clicking the full link.
  useEffect(() => {
    const urlToken = searchParams.get("token");
    if (urlToken) {
      void verify(urlToken);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface p-4">
      <Card className="w-full max-w-md space-y-4 p-6">
        <h1 className="text-xl font-semibold">Verify your email</h1>

        {status === "success" && (
          <>
            <p className="text-sm text-green-700">Your account is now active.</p>
            <Button onClick={() => router.replace("/login")}>Go to sign in</Button>
          </>
        )}

        {status !== "success" && (
          <>
            <p className="text-sm text-muted">
              Paste the verification token from your email if it wasn't filled in automatically.
            </p>
            <Input value={token} onChange={(e) => setToken(e.target.value)} placeholder="Verification token" />
            {message && <p className="text-sm text-red-600">{message}</p>}
            <Button disabled={status === "loading" || !token} onClick={() => verify(token)}>
              {status === "loading" ? "Verifying…" : "Verify email"}
            </Button>
          </>
        )}
      </Card>
    </div>
  );
}
