"use client";

import { useState } from "react";
import { CheckCircle2, Clock3, Flag, Moon, Send, ShieldCheck } from "lucide-react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";
import { statusClass } from "@/lib/skillforge-data";

type CatalogQuestion = { id: string; subject: string; prompt: string; options: string };

function parseOptions(json: string): string[] {
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function CandidatePlayer() {
  const [invitationId, setInvitationId] = useState("");
  const [attemptId, setAttemptId] = useState<string | null>(null);
  const [current, setCurrent] = useState(0);
  const [flagged, setFlagged] = useState<Record<number, boolean>>({});
  const [answers, setAnswers] = useState<Record<string, string[]>>({});
  const [message, setMessage] = useState("Enter your secure invitation token to begin.");
  const [submitted, setSubmitted] = useState(false);

  // Real, attempt-specific questions -- resolved server-side from the
  // assessment's actual blueprint, not a generic subject browse. Doesn't
  // need organizationId at all (resolved from attemptId), so this also
  // works for anonymous invitation-link candidates, not just logged-in ones.
  const { data: questions = [], isFetching } = useQuery<CatalogQuestion[]>({
    queryKey: ["candidate-questions", attemptId],
    queryFn: async () => {
      const res = await api.get(`/candidate/attempts/${attemptId}/questions`);
      const raw = res.data;
      const list = Array.isArray(raw) ? raw : Array.isArray(raw?.content) ? raw.content : [];
      return list.map((q: any) => ({ id: q.questionId, subject: q.topic ?? "", prompt: q.prompt, options: q.optionsJson }));
    },
    enabled: !!attemptId && !submitted,
  });

  const startMutation = useMutation({
    mutationFn: async () => {
      // Step 1: exchange the real token (from the invitation email, or from
      // the backend logs if SMTP isn't configured in this environment) for
      // the invitation's internal id. This also confirms the link hasn't
      // expired or been used already.
      const validation = await api.post(`/candidate/link/validate`, { token: invitationId.trim() });
      if (!validation.data?.valid) {
        throw new Error("This invitation link is invalid, expired, or has already been used.");
      }
      // Step 2: only now start the actual attempt.
      const res = await api.post(`/candidate/attempts/start/${validation.data.invitationId}`);
      return res.data;
    },
    onSuccess: (data) => {
      setAttemptId(data.id);
      setMessage("Autosave is active. Choose answers and submit when ready.");
    },
    onError: (error: any) => {
      setMessage(`Failed to start: ${error.response?.data?.error || error.message}`);
    }
  });

  const saveAnswerMutation = useMutation({
    mutationFn: async ({ questionId, answerChoices }: { questionId: string, answerChoices: string[] }) => {
      if (!attemptId) return;
      await api.put(`/candidate/attempts/${attemptId}/answers/${questionId}`, {
        answerJson: JSON.stringify(answerChoices),
        flaggedForReview: !!flagged[current]
      });
    }
  });

  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!attemptId) return;
      const res = await api.post(`/candidate/attempts/${attemptId}/submit`);
      return res.data;
    },
    onSuccess: () => {
      setSubmitted(true);
      setMessage("Assessment submitted. Confirmation and certificate eligibility are ready.");
    },
    onError: (error: any) => {
      setMessage(`Failed to submit: ${error.response?.data?.error || error.message}`);
    }
  });

  const currentQuestion = questions[current];
  const currentAnswer = currentQuestion ? (answers[currentQuestion.id] || []) : [];

  function toggleFlag() {
    setFlagged((state) => ({ ...state, [current]: !state[current] }));
    setMessage(flagged[current] ? "Question flag removed." : "Question flagged for review.");
  }

  function toggleAnswer(choice: string) {
    if (!currentQuestion) return;
    const newAnswers = currentAnswer.includes(choice) 
      ? currentAnswer.filter((item) => item !== choice) 
      : [...currentAnswer, choice];
      
    setAnswers((state) => ({ ...state, [currentQuestion.id]: newAnswers }));
    saveAnswerMutation.mutate({ questionId: currentQuestion.id, answerChoices: newAnswers });
    setMessage("Answer autosaved just now.");
  }

  if (!attemptId && !submitted) {
    return (
      <div className="space-y-5 flex flex-col items-center justify-center py-20">
        <h1 className="text-2xl font-semibold">Candidate Secure Portal</h1>
        <Card className="w-full max-w-md space-y-4">
          <div>
            <label className="text-sm font-medium">Invitation Token</label>
            <Input
              className="mt-1"
              placeholder="e.g. 3f9c1a02-....-....-....  .  8b7d4e10-....-....-...."
              value={invitationId}
              onChange={(e) => setInvitationId(e.target.value)}
            />
            <p className="mt-2 text-xs leading-5 text-muted">
              A trainer or admin invites you from the <span className="font-medium text-ink">Candidates</span> or{" "}
              <span className="font-medium text-ink">Assessments</span> page. You'll get this token by email. If
              email delivery (SMTP) isn't configured in this environment yet, ask your admin to check the backend
              server logs for a line starting with "SMTP not configured" right after they send the invite — the
              full token is printed there.
            </p>
          </div>
          <Button 
            className="w-full" 
            onClick={() => startMutation.mutate()}
            disabled={!invitationId || startMutation.isPending}
          >
            {startMutation.isPending ? "Validating..." : "Start Assessment"}
          </Button>
          {message && <div className="text-sm text-center text-muted">{message}</div>}
        </Card>
      </div>
    );
  }

  if (submitted) {
    return (
      <div className="space-y-5 flex flex-col items-center justify-center py-20">
        <CheckCircle2 className="text-green h-16 w-16 mb-4" />
        <h1 className="text-2xl font-semibold">Assessment Complete</h1>
        <p className="text-muted">{message}</p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <section className="rounded-md border border-line bg-white p-5 shadow-soft">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-medium text-blue">Candidate secure player</p>
            <h1 className="text-3xl font-semibold tracking-normal">Java Certification Assessment</h1>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">Magic link verified. Autosave is active. Tab, fullscreen, paste, and device events are recorded for trainer review.</p>
          </div>
          <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
            <span className="rounded-md bg-[#EFF6FF] px-3 py-2 font-semibold text-blue"><Clock3 className="mr-1 inline" size={16} /> 42:18</span>
            <span className="rounded-md bg-[#ECFDF5] px-3 py-2 font-semibold text-green"><ShieldCheck className="mr-1 inline" size={16} /> Secure</span>
            <span className="rounded-md bg-surface px-3 py-2 font-semibold text-muted"><Moon className="mr-1 inline" size={16} /> Dark</span>
            <span className="rounded-md bg-[#FFFBEB] px-3 py-2 font-semibold text-amber"><Flag className="mr-1 inline" size={16} /> {Object.values(flagged).filter(Boolean).length} flag</span>
          </div>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-[280px_1fr]">
        <Card>
          <h2 className="mb-4 text-lg font-semibold">Navigator</h2>
          <div className="grid grid-cols-4 gap-2 lg:grid-cols-3">
            {questions.map((q: any, idx: number) => {
              const state = answers[q.id]?.length > 0 ? "Completed" : "Current";
              return (
                <button key={q.id} onClick={() => setCurrent(idx)} className={`h-12 rounded-md border border-line text-sm font-semibold ${current === idx ? "bg-blue text-white" : statusClass(flagged[idx] ? "Flagged" : state)}`}>
                  {idx + 1}
                </button>
              );
            })}
          </div>
          <div className="mt-5 space-y-2 text-sm text-muted">
            <div className="flex items-center gap-2"><CheckCircle2 className="text-green" size={17} /> {message}</div>
            <div className="flex items-center gap-2"><ShieldCheck className="text-blue" size={17} /> Fullscreen active</div>
          </div>
        </Card>

        <Card>
          {isFetching ? (
            <div className="text-center py-10">Loading questions...</div>
          ) : currentQuestion ? (
            <>
              <div className="mb-4 flex items-start justify-between gap-3">
                <div>
                  <span className="rounded-md bg-[#EFF6FF] px-2 py-1 text-xs font-semibold text-blue">Question {current + 1} / {currentQuestion.subject}</span>
                  <h2 className="mt-3 text-xl font-semibold">{currentQuestion.prompt}</h2>
                </div>
                <Button variant="outline" onClick={toggleFlag}><Flag size={17} /> {flagged[current] ? "Unflag" : "Flag"}</Button>
              </div>
              <div className="grid gap-3">
                {parseOptions(currentQuestion.options).map((choice, index) => (
                  <button key={choice} onClick={() => toggleAnswer(choice)} className={`flex min-h-12 items-center justify-between rounded-md border px-3 text-left text-sm hover:bg-surface ${currentAnswer.includes(choice) ? "border-blue bg-[#EFF6FF]" : "border-line bg-white"}`}>
                    <span>{choice}</span>
                    <span className="rounded-md bg-white px-2 py-1 text-xs text-muted">{index + 1}</span>
                  </button>
                ))}
                {parseOptions(currentQuestion.options).length === 0 && (
                  <div className="rounded-md border border-line bg-surface px-3 py-4 text-center text-sm text-muted">This question has no answer options recorded yet.</div>
                )}
              </div>
              <div className="mt-5 flex flex-wrap justify-end gap-2">
                <Button variant="outline" onClick={() => setCurrent(Math.max(0, current - 1))}>Previous</Button>
                <Button variant="outline" onClick={() => setCurrent(Math.min(questions.length - 1, current + 1))}>Next</Button>
                <Button onClick={() => submitMutation.mutate()} disabled={submitMutation.isPending}>
                  <Send size={17} /> {submitMutation.isPending ? "Submitting..." : "Submit"}
                </Button>
              </div>
            </>
          ) : (
            <div className="py-10 text-center text-muted">
              {isFetching ? "Loading your assessment questions…" : "No questions are attached to this assessment yet — contact your trainer."}
            </div>
          )}
        </Card>
      </section>
    </div>
  );
}
