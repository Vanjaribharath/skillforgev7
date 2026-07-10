"use client";

import { Activity, ArrowUpRight, CalendarClock, CheckCircle2, FileText, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api-client";
import { useOrganizationStore } from "@/store/use-organization-store";
import { operationCards, roleCards, timeline, toneClass, statusClass } from "@/lib/skillforge-data";

export default function DashboardPage() {
  const { organizationId } = useOrganizationStore();

  const { data: orgData } = useQuery({
    queryKey: ["organizations", organizationId],
    queryFn: async () => {
      const res = await api.get(`/organizations/${organizationId}`);
      return res.data;
    },
    enabled: !!organizationId,
  });

  const { data: healthData } = useQuery({
    queryKey: ["health-dashboard", organizationId],
    queryFn: async () => {
      const res = await api.get(`/health-dashboard`, { params: { organizationId } });
      return res.data;
    },
    enabled: !!organizationId,
  });

  const { data: assessmentsData } = useQuery({
    queryKey: ["assessments", organizationId],
    queryFn: async () => {
      const res = await api.get(`/assessments`, { params: { organizationId } });
      // Assumes paginated response format
      return res.data?.content || [];
    },
    enabled: !!organizationId,
  });

  const orgName = orgData?.name || "Apex Learning Cloud";
  const passRate = 78; // Could be computed from backend reports later

  return (
    <div className="space-y-5">
      <section className="grid gap-4 lg:grid-cols-[1.4fr_0.8fr]">
        <div className="rounded-md border border-line bg-white p-5 shadow-soft">
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <span className="rounded-md bg-[#EFF6FF] px-2 py-1 text-xs font-semibold text-blue">SkillForge Enterprise</span>
            <span className="rounded-md bg-[#ECFDF5] px-2 py-1 text-xs font-semibold text-green">Multi-organization ready</span>
          </div>
          <h1 className="max-w-3xl text-3xl font-semibold tracking-normal text-ink sm:text-4xl">Internal assessment command center</h1>
          <p className="mt-3 max-w-3xl text-sm leading-6 text-muted">
            Manage question governance, reusable assessments, secure candidate delivery, live trainer monitoring, reports, and certificates for {orgName}.
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            <Button asChild><Link href="/assessments"><CheckCircle2 size={18} /> Publish assessment</Link></Button>
            <Button asChild variant="outline"><Link href="/question-bank"><FileText size={18} /> Import questions</Link></Button>
            <Button asChild variant="ghost"><Link href="/live"><ShieldCheck size={18} /> Review alerts</Link></Button>
          </div>
        </div>
        <div className="rounded-md border border-line bg-ink p-5 text-white shadow-soft">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-sm text-slate-300">Organization score</div>
              <div className="mt-2 text-4xl font-semibold">{passRate}%</div>
            </div>
            <Activity className="text-amber" size={32} />
          </div>
          <div className="mt-6 grid grid-cols-2 gap-3 text-sm">
            <div className="rounded-md bg-white/10 p-3">
              <div className="text-slate-300">Candidates</div>
              <div className="mt-1 text-xl font-semibold">{healthData?.candidates ?? 0}</div>
            </div>
            <div className="rounded-md bg-white/10 p-3">
              <div className="text-slate-300">Trainers</div>
              <div className="mt-1 text-xl font-semibold">{healthData?.trainers ?? 0}</div>
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-sm text-muted">Assessments this month</div>
              <div className="mt-2 text-3xl font-semibold tracking-normal">{healthData?.publishedAssessments ?? 0}</div>
              <div className="mt-1 text-sm text-muted">Active assessments</div>
            </div>
            <span className="grid h-10 w-10 place-items-center rounded-md bg-[#EFF6FF] text-blue"><CheckCircle2 size={20} /></span>
          </div>
        </Card>
        <Card>
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-sm text-muted">Active candidates</div>
              <div className="mt-2 text-3xl font-semibold tracking-normal">{healthData?.candidates ?? 0}</div>
              <div className="mt-1 text-sm text-muted">Registered learners</div>
            </div>
            <span className="grid h-10 w-10 place-items-center rounded-md bg-[#EFF6FF] text-blue"><Activity size={20} /></span>
          </div>
        </Card>
        <Card>
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-sm text-muted">Approved questions</div>
              <div className="mt-2 text-3xl font-semibold tracking-normal">{healthData?.approvedQuestions ?? 0}</div>
              <div className="mt-1 text-sm text-muted">{healthData?.draftQuestions ?? 0} waiting review</div>
            </div>
            <span className="grid h-10 w-10 place-items-center rounded-md bg-[#EFF6FF] text-blue"><FileText size={20} /></span>
          </div>
        </Card>
        <Card>
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-sm text-muted">Organizations</div>
              <div className="mt-2 text-3xl font-semibold tracking-normal">{healthData?.organizations ?? 1}</div>
              <div className="mt-1 text-sm text-muted">Managed tenants</div>
            </div>
            <span className="grid h-10 w-10 place-items-center rounded-md bg-[#EFF6FF] text-blue"><ShieldCheck size={20} /></span>
          </div>
        </Card>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.3fr_0.7fr]">
        <Card>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Priority assessments</h2>
            <CalendarClock className="text-blue" size={20} />
          </div>
          <div className="overflow-hidden rounded-md border border-line">
            <table className="w-full min-w-[680px] text-left text-sm">
              <thead className="bg-surface text-xs uppercase text-muted">
                <tr>
                  <th className="px-3 py-3">Assessment</th>
                  <th className="px-3 py-3">Status</th>
                  <th className="px-3 py-3">Duration</th>
                  <th className="px-3 py-3">Window</th>
                  <th className="px-3 py-3">Pass %</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {assessmentsData?.map((item: any) => (
                  <tr key={item.id} className="bg-white">
                    <td className="px-3 py-3">
                      <div className="font-medium">{item.title}</div>
                      <div className="mt-1 text-xs text-muted">Assessment</div>
                    </td>
                    <td className="px-3 py-3"><span className={`rounded-md px-2 py-1 text-xs font-medium ${statusClass(item.status)}`}>{item.status}</span></td>
                    <td className="px-3 py-3">{item.durationMinutes} min</td>
                    <td className="px-3 py-3 text-muted">{new Date(item.startAt || Date.now()).toLocaleDateString()}</td>
                    <td className="px-3 py-3">{item.passingPercentage}%</td>
                  </tr>
                ))}
                {assessmentsData?.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-3 py-4 text-center text-muted">No priority assessments found.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>

        <Card>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Live timeline</h2>
            <ArrowUpRight className="text-green" size={20} />
          </div>
          <div className="space-y-3">
            {timeline.map((item) => {
              const Icon = item.icon;
              return (
                <div key={`${item.time}-${item.title}`} className="grid grid-cols-[48px_36px_1fr] items-center gap-2">
                  <span className="text-xs font-medium text-muted">{item.time}</span>
                  <span className="grid h-9 w-9 place-items-center rounded-md bg-surface text-blue"><Icon size={18} /></span>
                  <span className="text-sm">{item.title}</span>
                </div>
              );
            })}
          </div>
        </Card>
      </section>

      <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        {operationCards.map((item) => {
          const Icon = item.icon;
          return (
            <Card key={item.title}>
              <span className={`mb-3 grid h-10 w-10 place-items-center rounded-md ${toneClass(item.tone)}`}><Icon size={20} /></span>
              <div className="font-semibold">{item.title}</div>
              <div className="mt-2 text-2xl font-semibold">{item.value}</div>
              <div className="mt-1 text-sm text-muted">{item.detail}</div>
            </Card>
          );
        })}
      </section>

      <section className="grid gap-3 lg:grid-cols-3">
        {roleCards.map((item) => {
          const Icon = item.icon;
          return (
            <Card key={item.title}>
              <div className="mb-3 flex items-center gap-2">
                <Icon className="text-blue" size={19} />
                <h2 className="font-semibold">{item.title}</h2>
              </div>
              <p className="text-sm leading-6 text-muted">{item.text}</p>
            </Card>
          );
        })}
      </section>
    </div>
  );
}
