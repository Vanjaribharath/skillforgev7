"use client";

import { useMemo, useState } from "react";
import { CalendarClock, Copy, Eye, Mail, Plus, Send, Shuffle, UsersRound, Trash } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";
import { useOrganizationStore } from "@/store/use-organization-store";
import { statusClass } from "@/lib/skillforge-data";

type AssessmentDraft = {
  title: string;
  subject: string;
  easy: number;
  medium: number;
  hard: number;
  duration: number;
  candidates: string[];
};

export function AssessmentWorkbench() {
  const { organizationId } = useOrganizationStore();
  const queryClient = useQueryClient();

  const [draft, setDraft] = useState<AssessmentDraft>({
    title: "Java Fresher Certification",
    subject: "Java",
    easy: 10,
    medium: 10,
    hard: 5,
    duration: 60,
    candidates: [], // We will fetch real candidates and prefill or let them select
  });
  
  const [published, setPublished] = useState(false);
  const [activeAssessmentId, setActiveAssessmentId] = useState<string | null>(null);
  const [invitations, setInvitations] = useState<string[]>([]);
  const [message, setMessage] = useState("Configure an assessment template, publish it, then send one-time links.");

  const { data: candidates = [] } = useQuery({
    queryKey: ["candidates", organizationId],
    queryFn: async () => {
      const res = await api.get(`/candidates`, { params: { organizationId } });
      return res.data?.content || [];
    },
    enabled: !!organizationId,
  });

  const { data: assessments = [] } = useQuery({
    queryKey: ["assessments", organizationId],
    queryFn: async () => {
      const res = await api.get(`/assessments`, { params: { organizationId } });
      return res.data?.content || [];
    },
    enabled: !!organizationId,
  });

  const publishMutation = useMutation({
    mutationFn: async () => {
      // 1. Create the assessment shell (draft).
      const res = await api.post(`/assessments`, {
        organizationId,
        title: draft.title,
        description: `${draft.subject} test with ${totalQuestions} questions`,
        durationMinutes: draft.duration,
        passingPercentage: 70,
        shuffleQuestions: true,
      });
      const newAssessment = res.data;

      // 2. Give it one section to hold the question pool.
      const section = await api.post(`/assessments/${newAssessment.id}/sections`, {
        name: `${draft.subject} — mixed difficulty`,
        sortOrder: 0,
      });

      // 3. Pull real APPROVED questions from the catalog for each requested
      // difficulty band and attach them. This step used to be entirely
      // missing -- the assessment was created and published with zero
      // questions, which the backend correctly rejects with a 400
      // ("Assessment requires at least one approved question before
      // publishing"). That 400 was the backend doing its job; the bug was
      // here.
      const bands: Array<["EASY" | "MEDIUM" | "HARD", number]> = [
        ["EASY", draft.easy],
        ["MEDIUM", draft.medium],
        ["HARD", draft.hard],
      ];
      let attached = 0;
      for (const [difficulty, count] of bands) {
        if (count <= 0) continue;
        const catalogRes = await api.get(`/catalog/questions`, {
          params: { organizationId, subject: draft.subject, difficulty, page: 0, size: count },
        });
        const catalogQuestions: Array<{ id: string }> = Array.isArray(catalogRes.data) ? catalogRes.data : [];
        for (const question of catalogQuestions) {
          await api.post(`/assessments/${newAssessment.id}/questions`, { questionId: question.id });
          attached++;
        }
      }

      if (attached === 0) {
        throw new Error(
          `No approved ${draft.subject} questions are available yet — import some from the Question Bank first, then publish.`,
        );
      }

      // 4. Only now is it safe to publish.
      await api.post(`/assessments/${newAssessment.id}/publish`);
      return { ...newAssessment, attachedQuestionCount: attached };
    },
    onSuccess: (data: any) => {
      queryClient.invalidateQueries({ queryKey: ["assessments", organizationId] });
      setPublished(true);
      setActiveAssessmentId(data.id);
      setMessage(`Published "${draft.title}" with ${data.attachedQuestionCount} real approved ${draft.subject} question(s).`);
    },
    onError: (error: any) => {
      setMessage(error?.response?.data?.error || error?.message || "Failed to publish assessment.");
    }
  });

  const inviteMutation = useMutation({
    mutationFn: async () => {
      if (!activeAssessmentId) throw new Error("No active assessment to invite to");
      const candidateIds = candidates.map((c: any) => c.id);
      if (candidateIds.length === 0) throw new Error("No candidates found in organization");
      
      const res = await api.post(`/assessments/${activeAssessmentId}/invite`, {
        candidateUserIds: candidateIds,
        expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      });
      return res.data;
    },
    onSuccess: (data: any) => {
      const list = Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : [];
      const tokens = list.map((invite: any) => invite.tokenPreview || `Invite sent to ${invite.candidateUserId}`);
      setInvitations(tokens);
      setMessage(tokens.length > 0 ? `Sent ${tokens.length} invitation(s) by email. If SMTP isn't configured in this environment, check the backend server logs for the full token/link (search for "SMTP not configured").` : "Invite request succeeded but returned no invitations.");
    },
    onError: (error: any) => {
      setMessage(`Failed to invite: ${error.response?.data?.error || error.message}`);
    }
  });

  const totalQuestions = draft.easy + draft.medium + draft.hard;
  const randomizedPlan = useMemo(() => {
    return [
      `${draft.easy} easy ${draft.subject} questions`,
      `${draft.medium} medium ${draft.subject} questions`,
      `${draft.hard} hard ${draft.subject} questions`,
      "Unique order per candidate",
      "One attempt per invitation",
    ];
  }, [draft]);

  function cloneAssessment(title: string) {
    setDraft((current) => ({ ...current, title: `${title} Clone` }));
    setPublished(false);
    setActiveAssessmentId(null);
    setMessage(`Cloned "${title}" into the builder.`);
  }

  function publishAssessment() {
    if (totalQuestions <= 0) {
      setMessage("Choose at least one question before publishing.");
      return;
    }
    publishMutation.mutate();
  }

  function sendInvitations() {
    if (!published || !activeAssessmentId) {
      setMessage("Publish the assessment before sending candidate invitations.");
      return;
    }
    inviteMutation.mutate();
  }

  function updateNumber(key: "easy" | "medium" | "hard" | "duration", value: string) {
    setDraft((current) => ({ ...current, [key]: Math.max(0, Number(value) || 0) }));
  }

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm font-medium text-blue">Assessment builder</p>
          <h1 className="text-3xl font-semibold tracking-normal">Templates, sections, scheduling</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">Create a trainer-owned test, choose difficulty distribution, publish it, and deliver random question sets to students.</p>
        </div>
        <Button onClick={() => {
            setDraft({ title: "New Assessment", subject: "Java", easy: 10, medium: 10, hard: 5, duration: 60, candidates: [] });
            setPublished(false);
            setActiveAssessmentId(null);
            setMessage("New assessment builder is ready below. Change the title and publish.");
        }}><Plus size={18} /> Create assessment</Button>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1fr_380px]">
        <Card>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Builder</h2>
            <Shuffle className="text-blue" size={20} />
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <label className="text-sm font-medium">Title<Input className="mt-2" value={draft.title} onChange={(event) => setDraft((current) => ({ ...current, title: event.target.value }))} /></label>
            <label className="text-sm font-medium">Subject
              <select className="mt-2 h-11 w-full rounded-md border border-line px-3 text-sm" value={draft.subject} onChange={(event) => setDraft((current) => ({ ...current, subject: event.target.value }))}>
                {["Java", "Spring Boot", "Linux", "SQL", "Kafka", "Docker", "Kubernetes", "AWS"].map((item) => <option key={item}>{item}</option>)}
              </select>
            </label>
            <label className="text-sm font-medium">Easy questions<Input className="mt-2" type="number" value={draft.easy} onChange={(event) => updateNumber("easy", event.target.value)} /></label>
            <label className="text-sm font-medium">Medium questions<Input className="mt-2" type="number" value={draft.medium} onChange={(event) => updateNumber("medium", event.target.value)} /></label>
            <label className="text-sm font-medium">Hard questions<Input className="mt-2" type="number" value={draft.hard} onChange={(event) => updateNumber("hard", event.target.value)} /></label>
            <label className="text-sm font-medium">Duration minutes<Input className="mt-2" type="number" value={draft.duration} onChange={(event) => updateNumber("duration", event.target.value)} /></label>
          </div>
          <div className="mt-4 rounded-md bg-[#EFF6FF] px-3 py-2 text-sm text-blue">{message}</div>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setMessage(`Preview ready: ${draft.title}, ${totalQuestions} questions, ${draft.duration} minutes.`)}><Eye size={17} /> Preview</Button>
            <Button onClick={publishAssessment} disabled={publishMutation.isPending}>
                <CalendarClock size={17} /> {publishMutation.isPending ? "Publishing..." : "Publish"}
            </Button>
            <Button onClick={sendInvitations} disabled={inviteMutation.isPending || !published}>
                <Send size={17} /> {inviteMutation.isPending ? "Sending..." : "Send invitations"}
            </Button>
          </div>
        </Card>

        <Card>
          <div className="mb-4 flex items-center gap-2"><UsersRound className="text-green" size={20} /><h2 className="font-semibold">Random delivery plan</h2></div>
          <div className="text-4xl font-semibold">{totalQuestions}</div>
          <p className="mt-1 text-sm text-muted">questions per candidate</p>
          <div className="mt-4 space-y-2">
            {randomizedPlan.map((item) => <div key={item} className="rounded-md border border-line px-3 py-2 text-sm">{item}</div>)}
          </div>
        </Card>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        {assessments.map((item: any) => (
          <Card key={item.id} className="flex flex-col">
            <div className="flex items-start justify-between gap-3">
              <div>
                <span className={`rounded-md px-2 py-1 text-xs font-semibold ${statusClass(item.status)}`}>{item.status}</span>
                <h2 className="mt-3 text-lg font-semibold">{item.title}</h2>
              </div>
              <CalendarClock className="text-blue" size={21} />
            </div>
            <div className="mt-4 text-sm text-muted">{new Date(item.startAt || Date.now()).toLocaleDateString()} - {item.durationMinutes} min</div>
            <div className="mt-4 flex flex-wrap gap-2">
              <span className="rounded-md border border-line px-2 py-1 text-xs text-muted">Randomized Pool</span>
            </div>
            <div className="mt-5 flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setMessage(`Previewing ${item.title}.`)}><Eye size={17} /> Preview</Button>
              <Button variant="outline" onClick={() => cloneAssessment(item.title)}><Copy size={17} /> Clone</Button>
              <Button variant="outline" onClick={() => setMessage(`${item.title} pool randomized for each candidate.`)}><Shuffle size={17} /> Pools</Button>
            </div>
          </Card>
        ))}
      </section>

      <Card>
        <div className="mb-4 flex items-center gap-2"><Mail className="text-blue" size={20} /><h2 className="font-semibold">Queued invitations</h2></div>
        <div className="grid gap-2">
          {(invitations.length ? invitations : ["No invitations sent yet. Publish an assessment and click Send invitations."]).map((item, idx) => (
            <div key={idx} className="rounded-md border border-line px-3 py-2 text-sm text-muted">{item}</div>
          ))}
        </div>
      </Card>
    </div>
  );
}
