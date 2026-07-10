"use client";

import { useState } from "react";
import { BarChart3, Download, FileText, PieChart, TrendingDown } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api-client";
import { useOrganizationStore } from "@/store/use-organization-store";

export default function ReportsPage() {
  const { organizationId } = useOrganizationStore();

  const { data: candidates = [] } = useQuery({
    queryKey: ["candidates", organizationId],
    queryFn: async () => {
      const res = await api.get(`/candidates`, { params: { organizationId } });
      return res.data?.content || [];
    },
    enabled: !!organizationId,
  });

  // Calculate dynamic report rows based on candidates (Mocking batches since they aren't fully resolved yet)
  const reportRows = candidates.length > 0 ? [
    { label: "Organization Total", candidates: candidates.length, average: 74, pass: 82, weak: "Streams" }
  ] : [
    { label: "No data", candidates: 0, average: 0, pass: 0, weak: "None" }
  ];

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm font-medium text-blue">Reports and analytics</p>
          <h1 className="text-3xl font-semibold tracking-normal">Skill intelligence by cohort</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">Export candidate, batch, department, trainer, subject, difficulty, and question performance reports.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild><a download="skillforge-batch-report.csv" href="data:text/csv;charset=utf-8,Batch,Candidates,Average,Pass%20Rate%0AOrganization%20Total,126,74,82"><Download size={18} /> Excel</a></Button>
          <Button asChild variant="outline"><a download="skillforge-report.txt" href="data:text/plain;charset=utf-8,SkillForge%20report%20export"><FileText size={18} /> PDF</a></Button>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-[1fr_1fr]">
        <Card>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Weekly test volume</h2>
            <BarChart3 className="text-blue" size={20} />
          </div>
          <div className="flex h-56 items-end gap-2">
            {[46, 82, 61, 94, 73, 88, 52, 67, 91, 78, 84, 69].map((height, index) => (
              <div key={index} className="flex flex-1 flex-col items-center gap-2">
                <div className="w-full rounded-t-md bg-blue" style={{ height: `${height}%` }} />
                <span className="text-[10px] text-muted">{index + 1}</span>
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Weak area analysis</h2>
            <PieChart className="text-green" size={20} />
          </div>
          <div className="space-y-3">
            {["Streams", "Kubernetes", "OAuth2", "Linux permissions", "Kafka offsets"].map((topic, index) => (
              <div key={topic}>
                <div className="mb-1 flex justify-between text-sm"><span>{topic}</span><span className="text-muted">{68 - index * 7}% failed</span></div>
                <div className="h-2 rounded-full bg-surface"><div className="h-full rounded-full bg-amber" style={{ width: `${68 - index * 7}%` }} /></div>
              </div>
            ))}
          </div>
        </Card>
      </section>

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Batch report</h2>
          <TrendingDown className="text-amber" size={20} />
        </div>
        <div className="overflow-hidden rounded-md border border-line">
          <table className="w-full min-w-[680px] text-left text-sm">
            <thead className="bg-surface text-xs uppercase text-muted">
              <tr><th className="px-3 py-3">Batch</th><th className="px-3 py-3">Candidates</th><th className="px-3 py-3">Average</th><th className="px-3 py-3">Pass rate</th><th className="px-3 py-3">Weak topic</th></tr>
            </thead>
            <tbody className="divide-y divide-line">
              {reportRows.map((row) => (
                <tr key={row.label} className="bg-white">
                  <td className="px-3 py-3 font-medium">{row.label}</td>
                  <td className="px-3 py-3">{row.candidates}</td>
                  <td className="px-3 py-3">{row.average}%</td>
                  <td className="px-3 py-3">{row.pass}%</td>
                  <td className="px-3 py-3 text-muted">{row.weak}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
