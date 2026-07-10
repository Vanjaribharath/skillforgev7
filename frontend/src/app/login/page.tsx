"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Landmark, ShieldCheck, UserRoundCheck } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";
import { useAuthStore, type SkillForgeUser } from "@/store/use-auth-store";

type LoginResponse = {
  accessToken: string;
  refreshToken: string;
  user: SkillForgeUser;
};

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

export default function LoginPage() {
  const router = useRouter();
  const setSession = useAuthStore((s) => s.setSession);
  const [mode, setMode] = useState<"staff" | "candidate">("staff");
  const [view, setView] = useState<"login" | "forgot">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [forgotSent, setForgotSent] = useState(false);

  function completeLogin(res: LoginResponse) {
    setSession(res.user, res.accessToken, res.refreshToken);
    router.replace(res.user.role === "CANDIDATE" ? "/candidate" : "/");
  }

  async function performLogin(emailValue: string, passwordValue: string) {
    setLoading(true);
    setError(null);
    try {
      const res = await api.post<LoginResponse>("/auth/login", { email: emailValue, password: passwordValue });
      completeLogin(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.error || "Invalid email or password.");
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    await performLogin(email, password);
  }

  async function handleForgotPassword(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await api.post("/auth/forgot-password", { email });
      setForgotSent(true);
    } catch {
      // Backend deliberately never reveals whether the email matched an
      // account, so this always shows the same confirmation regardless.
      setForgotSent(true);
    } finally {
      setLoading(false);
    }
  }

  // Only wires up Google Sign-In when a public client ID is actually
  // configured -- otherwise the button simply doesn't render, rather than
  // rendering something that can never work.
  useEffect(() => {
    if (!GOOGLE_CLIENT_ID || view !== "login") return;
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.onload = () => {
      // @ts-expect-error -- loaded globally by the script above
      window.google?.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: async (response: { credential: string }) => {
          setLoading(true);
          setError(null);
          try {
            const res = await api.post<LoginResponse>("/auth/google", { idToken: response.credential });
            completeLogin(res.data);
          } catch (err: any) {
            setError(err?.response?.data?.error || "Google sign-in failed.");
          } finally {
            setLoading(false);
          }
        },
      });
      // @ts-expect-error -- loaded globally by the script above
      window.google?.accounts.id.renderButton(document.getElementById("google-signin-button"), {
        theme: "outline",
        size: "large",
        width: 320,
      });
    };
    document.body.appendChild(script);
    return () => { document.body.removeChild(script); };
  }, [view]);

  const DEMO_ACCOUNTS = [
    { role: "Admin", email: "admin@apex.example", mode: "staff" as const },
    { role: "Trainer", email: "trainer@apex.example", mode: "staff" as const },
    { role: "Candidate", email: "candidate@apex.example", mode: "candidate" as const },
  ];
  const DEMO_PASSWORD = "Demo@12345";

  function quickLogin(account: (typeof DEMO_ACCOUNTS)[number]) {
    setMode(account.mode);
    setEmail(account.email);
    setPassword(DEMO_PASSWORD);
    void performLogin(account.email, DEMO_PASSWORD);
  }

  return (
    <div className="grid min-h-screen place-items-center bg-surface px-4">
      <div className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-md bg-blue text-white"><Landmark size={22} /></span>
          <div>
            <div className="text-xl font-semibold text-ink">SkillForge</div>
            <div className="text-xs text-muted">Enterprise assessment platform</div>
          </div>
        </div>

        <Card className="p-6">
          {view === "forgot" ? (
            <div>
              <h2 className="mb-4 text-lg font-semibold text-ink">Reset your password</h2>
              {forgotSent ? (
                <div className="rounded-md border border-green-200 bg-green-50 px-3 py-3 text-sm text-green-800">
                  If that email has an account, a reset link has been sent. It expires in 1 hour.
                </div>
              ) : (
                <form onSubmit={handleForgotPassword} className="space-y-4">
                  <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@organization.com" />
                  <Button type="submit" disabled={loading} className="w-full">{loading ? "Sending…" : "Send reset link"}</Button>
                </form>
              )}
              <button type="button" onClick={() => { setView("login"); setForgotSent(false); }} className="mt-4 text-xs font-medium text-blue hover:underline">
                ← Back to sign in
              </button>
            </div>
          ) : (
            <>
              <div className="mb-5 grid grid-cols-2 gap-1 rounded-md bg-surface p-1">
                <button type="button" onClick={() => setMode("staff")} className={`flex items-center justify-center gap-2 rounded-md py-2 text-sm font-medium transition ${mode === "staff" ? "bg-white text-blue shadow-sm" : "text-muted"}`}>
                  <ShieldCheck size={16} /> Admin / Trainer
                </button>
                <button type="button" onClick={() => setMode("candidate")} className={`flex items-center justify-center gap-2 rounded-md py-2 text-sm font-medium transition ${mode === "candidate" ? "bg-white text-blue shadow-sm" : "text-muted"}`}>
                  <UserRoundCheck size={16} /> Candidate
                </button>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-ink">Email</label>
                  <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder={mode === "staff" ? "you@organization.com" : "candidate@organization.com"} autoComplete="username" />
                </div>
                <div>
                  <div className="mb-1.5 flex items-center justify-between">
                    <label className="block text-sm font-medium text-ink">Password</label>
                    <button type="button" onClick={() => setView("forgot")} className="text-xs font-medium text-blue hover:underline">Forgot password?</button>
                  </div>
                  <Input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" autoComplete="current-password" />
                </div>

                {error && <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}

                <Button type="submit" disabled={loading} className="w-full">{loading ? "Signing in…" : "Sign in"}</Button>
              </form>

              {GOOGLE_CLIENT_ID && (
                <div className="mt-4 flex justify-center">
                  <div id="google-signin-button" />
                </div>
              )}

              <div className="mt-5 border-t border-line pt-4">
                <p className="mb-2 text-center text-xs font-medium text-muted">Demo accounts — one click, no typing</p>
                <div className="grid grid-cols-3 gap-2">
                  {DEMO_ACCOUNTS.map((account) => (
                    <button
                      key={account.role}
                      type="button"
                      disabled={loading}
                      aria-label={`Log in as ${account.role}`}
                      onClick={() => quickLogin(account)}
                      className="rounded-md border border-line bg-white py-2 text-xs font-semibold text-ink hover:border-blue hover:text-blue disabled:opacity-50"
                    >
                      {account.role}
                    </button>
                  ))}
                </div>
                <p className="mt-2 text-center text-[11px] leading-relaxed text-muted">
                  All three demo accounts use the password <code className="rounded bg-surface px-1 py-0.5">Demo@12345</code>.
                  They only exist after <code className="rounded bg-surface px-1 py-0.5">POST /api/v1/skillforge/demo/bootstrap</code> has
                  been called once against this environment.
                </p>
              </div>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
