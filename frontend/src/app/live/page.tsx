"use client";

import { useState } from "react";
import { BellRing, MonitorDot, RefreshCcw, ShieldAlert } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api-client";
import { useOrganizationStore } from "@/store/use-organization-store";
import { statusClass } from "@/lib/skillforge-data";

export default function LivePage() {
  const { organizationId } = useOrganizationStore();
  const [selectedAssessmentId, setSelectedAssessmentId] = useState<string | null>(null);

  const { data: assessments = [] } = useQuery({
    queryKey: ["assessments", organizationId],
    queryFn: async () => {
      const res = await api.get(`/assessments`, { params: { organizationId } });
      return res.data?.content || [];
    },
    enabled: !!organizationId,
  });

  // Automatically select the first assessment if none is selected
  const activeAssessmentId = selectedAssessmentId || (assessments.length > 0 ? assessments[0].id : null);

  const { data: liveData, refetch, isFetching } = useQuery({
    queryKey: ["live", activeAssessmentId],
    queryFn: async () => {
      if (!activeAssessmentId) return null;
      const res = await api.get(`/assessments/${activeAssessmentId}/live`);
      return res.data;
    },
    enabled: !!activeAssessmentId,
    refetchInterval: 5000, // Refresh every 5s
  });

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm font-medium text-blue">Trainer live dashboard</p>
          <h1 className="text-3xl font-semibold tracking-normal">Monitor active attempts</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">Track started, in-progress, completed, disconnected, and suspicious activity states in one auto-refreshing view.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {assessments.length > 0 && (
            <select 
              className="h-11 rounded-md border border-line bg-white px-3 text-sm"
              value={activeAssessmentId || ""}
              onChange={(e) => setSelectedAssessmentId(e.target.value)}
            >
              {assessments.map((a: any) => <option key={a.id} value={a.id}>{a.title}</option>)}
            </select>
          )}
          <Button variant="outline" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCcw size={18} className={isFetching ? "animate-spin" : ""} /> Refresh
          </Button>
          <Button asChild><a href="mailto:trainer@apex.example?subject=SkillForge%20live%20assessment%20alert"><BellRing size={18} /> Notify trainer</a></Button>
        </div>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {[
          ["Started", liveData?.notStarted ?? 0],
          ["In progress", liveData?.inProgress ?? 0],
          ["Completed", (liveData?.submitted ?? 0) + (liveData?.evaluated ?? 0)],
          ["Evaluated", liveData?.evaluated ?? 0],
        ].map(([label, value]) => (
          <Card key={label as string}>
            <div className="text-sm text-muted">{label as string}</div>
            <div className="mt-2 text-3xl font-semibold">{value as number}</div>
          </Card>
        ))}
      </section>

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Candidate state</h2>
          <MonitorDot className="text-blue" size={20} />
        </div>
        <div className="space-y-3">
          {(Array.isArray(liveData?.attempts) ? liveData.attempts : []).map((attempt: any) => (
            <div key={attempt.id} className="rounded-md border border-line p-3">
              <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                <div>
                  <div className="font-semibold">Candidate: {attempt.candidateUserId}</div>
                  <div className="text-sm text-muted">{attempt.deviceInfo || "Unknown Device"}</div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`rounded-md px-2 py-1 text-xs font-semibold ${statusClass(attempt.status)}`}>{attempt.status}</span>
                  <span className="rounded-md bg-surface px-2 py-1 text-xs text-muted">{new Date(attempt.startedAt).toLocaleTimeString()}</span>
                  <span className="inline-flex items-center gap-1 rounded-md bg-[#FFFBEB] px-2 py-1 text-xs font-medium text-amber"><ShieldAlert size={14} /> {attempt.suspiciousScore}</span>
                </div>
              </div>
              <div className="mt-3 h-2 overflow-hidden rounded-full bg-surface">
                <div className="h-full rounded-full bg-blue" style={{ width: `${Math.min(100, attempt.score || 0)}%` }} />
              </div>
            </div>
          ))}
          {!liveData?.attempts?.length && (
            <div className="text-sm text-muted text-center py-4">No active attempts for this assessment.</div>
          )}
        </div>
      </Card>
    </div>
  );
}
