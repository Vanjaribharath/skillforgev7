"use client";

import { useQuery } from "@tanstack/react-query";
import { ArchiveRestore, Shield } from "lucide-react";
import { Card } from "@/components/ui/card";
import { executionApi } from "@/lib/execution-api-client";

type ActivityLog = { id: string; action: string; entityType: string; createdAt: string };

export default function AdminPage() {
  // The health/queue/APM stat cards this page used to show were hardcoded
  // numbers with no backend behind them at all (no queue system, no
  // latency tracking exists in this app) -- removed rather than replaced
  // with a different set of fake numbers. Only "Recent audit events" below
  // is wired to something real (ActivityLogRepository).
  const { data: logs = [], isLoading, isError } = useQuery<ActivityLog[]>({
    queryKey: ["admin-logs"],
    queryFn: async () => {
      const res = await executionApi.get("/admin/logs", { params: { size: 20 } });
      const raw = res.data;
      return Array.isArray(raw) ? raw : Array.isArray(raw?.content) ? raw.content : [];
    },
  });

  return (
    <div className="space-y-5">
      <section className="flex items-center justify-between">
        <div><p className="text-sm font-medium text-blue">Platform admin</p><h1 className="text-3xl font-semibold">Audit log</h1></div>
        <Shield className="text-blue" />
      </section>

      <Card className="border-amber/40 bg-[#FFFBEB] text-sm text-amber-800">
        No queue system, health-latency tracking, or backup automation exists in this app yet — removed the stat cards that used to show fake numbers for these rather than replace them with different fake numbers. What's below (audit events) is real.
      </Card>

      <Card>
        <div className="mb-4 flex items-center gap-2"><ArchiveRestore className="text-amber" size={20} /><h2 className="font-semibold">Recent audit events</h2></div>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isError && <p className="text-sm text-red-600">Couldn't load audit events.</p>}
        {!isLoading && !isError && logs.length === 0 && <p className="text-sm text-muted">No activity recorded yet.</p>}
        <div className="space-y-2 text-sm">
          {logs.map((log) => (
            <div key={log.id} className="rounded-md border border-line px-3 py-2">
              <span className="font-medium">{log.action}</span>
              <span className="text-muted"> · {log.entityType} · {new Date(log.createdAt).toLocaleString()}</span>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
