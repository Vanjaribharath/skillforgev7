"use client";

import { Mail, Palette, Settings } from "lucide-react";
import { useEffect, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api-client";
import { useOrganizationStore } from "@/store/use-organization-store";
import { useAuthStore } from "@/store/use-auth-store";

type OrganizationResponse = {
  id: string;
  name: string;
  slug: string;
  status: string;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  certificatePrefix: string;
  locale: string;
};

export default function SettingsPage() {
  const { organizationId } = useOrganizationStore();
  const { user } = useAuthStore();
  const queryClient = useQueryClient();
  const [message, setMessage] = useState<string | null>(null);
  const [testEmailTo, setTestEmailTo] = useState("");
  const [testEmailResult, setTestEmailResult] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [primaryColor, setPrimaryColor] = useState("");
  const [certificatePrefix, setCertificatePrefix] = useState("");
  const [locale, setLocale] = useState("en");

  const { data } = useQuery({
    queryKey: ["organization", organizationId],
    queryFn: async () => {
      const res = await api.get<OrganizationResponse>(`/organizations/${organizationId}`);
      return res.data;
    },
    enabled: !!organizationId,
  });

  useEffect(() => {
    if (user?.email) setTestEmailTo(user.email);
  }, [user?.email]);

  useEffect(() => {
    if (data) {
      setName(data.name);
      setPrimaryColor(data.primaryColor);
      setCertificatePrefix(data.certificatePrefix);
      setLocale(data.locale);
    }
  }, [data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const res = await api.patch<OrganizationResponse>(`/organizations/${organizationId}`, {
        name, primaryColor, certificatePrefix, locale,
      });
      return res.data;
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(["organization", organizationId], updated);
      setMessage("Saved.");
    },
    onError: (error: any) => {
      setMessage(error?.response?.data?.error || "Failed to save settings.");
    },
  });

  const testEmailMutation = useMutation({
    mutationFn: async (email: string) => {
      const res = await api.post("/notifications/test-email", { email });
      return res.data as { sent: boolean; smtpConfigured: boolean; detail: string };
    },
    onSuccess: (result) => setTestEmailResult(result.detail),
    onError: (error: any) => setTestEmailResult(error?.response?.data?.error || error.message || "Test email request failed."),
  });

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm font-medium text-blue">Enterprise settings</p>
          <h1 className="text-3xl font-semibold">Organization branding</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
            These fields are real and persist to the database — this is the whole scope of what
            this page currently does. Email delivery is configured via environment variables
            (see <code className="rounded bg-surface px-1 py-0.5">GMAIL_SMTP_SETUP.md</code>), not
            per-organization here yet, and exam security toggles (fullscreen, copy-paste
            detection, etc.) aren't implemented in the candidate exam player yet — showing them
            here without them actually doing anything would be misleading, so they've been
            removed until they're real.
          </p>
        </div>
        <Button disabled={saveMutation.isPending || !organizationId} onClick={() => saveMutation.mutate()}>
          <Settings size={18} /> {saveMutation.isPending ? "Saving…" : "Save changes"}
        </Button>
      </section>

      {message && <Card className="border-amber/40 bg-[#FFFBEB] text-sm text-amber-800">{message}</Card>}

      <Card>
        <div className="mb-4 flex items-center gap-2"><Palette className="text-blue" size={20} /><h2 className="font-semibold">Organization branding</h2></div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm font-medium">Organization name<Input className="mt-2" value={name} onChange={(e) => setName(e.target.value)} /></label>
          <label className="text-sm font-medium">Primary color<Input className="mt-2" value={primaryColor} onChange={(e) => setPrimaryColor(e.target.value)} /></label>
          <label className="text-sm font-medium">Certificate prefix<Input className="mt-2" value={certificatePrefix} onChange={(e) => setCertificatePrefix(e.target.value)} /></label>
          <label className="text-sm font-medium">Locale
            <select className="mt-2 h-11 w-full rounded-md border border-line px-3 text-sm" value={locale} onChange={(e) => setLocale(e.target.value)}>
              <option value="en">English</option>
              <option value="hi">Hindi</option>
              <option value="es">Spanish</option>
            </select>
          </label>
        </div>
      </Card>

      <Card>
        <div className="mb-4 flex items-center gap-2"><Mail className="text-blue" size={20} /><h2 className="font-semibold">Email delivery test</h2></div>
        <p className="mb-3 text-sm text-muted">
          Sends one real email through whatever SMTP is currently configured on the backend — the
          fastest way to confirm SMTP_HOST/PORT/USERNAME/PASSWORD actually work before relying on
          candidate invitations to prove it.
        </p>
        <div className="flex flex-col gap-2 sm:flex-row">
          <Input value={testEmailTo} onChange={(e) => setTestEmailTo(e.target.value)} placeholder="you@example.com" className="sm:max-w-xs" />
          <Button
            variant="outline"
            disabled={testEmailMutation.isPending || !testEmailTo}
            onClick={() => testEmailMutation.mutate(testEmailTo)}
          >
            {testEmailMutation.isPending ? "Sending…" : "Send test email"}
          </Button>
        </div>
        {testEmailResult && (
          <p className="mt-3 rounded-md bg-[#EFF6FF] px-3 py-2 text-sm text-blue">{testEmailResult}</p>
        )}
      </Card>
    </div>
  );
}
