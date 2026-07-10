"use client";

import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Pause, Play, RotateCcw, TimerReset } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useFocusStore } from "@/store/use-focus-store";
import { executionApi } from "@/lib/execution-api-client";

type FocusSession = {
  id: string;
  startTime: string;
  endTime: string | null;
  durationSeconds: number;
  type: string;
  status: string;
};

export default function FocusPage() {
  const { secondsRemaining, running, start, pause, reset, tick } = useFocusStore();
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const minutes = Math.floor(secondsRemaining / 60).toString().padStart(2, "0");
  const seconds = (secondsRemaining % 60).toString().padStart(2, "0");

  // Fixed: tick() previously existed on the store but nothing ever called
  // it, so the timer never actually counted down -- Start/Pause/Reset just
  // changed the displayed number without a clock driving it.
  useEffect(() => {
    if (!running) return;
    const interval = setInterval(() => {
      tick();
      if (secondsRemaining <= 1 && sessionId) {
        void completeSession(sessionId);
      }
    }, 1000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running, secondsRemaining, sessionId]);

  const { data: sessions = [], isLoading, isError } = useQuery<FocusSession[]>({
    queryKey: ["focus-sessions"],
    queryFn: async () => {
      const res = await executionApi.get("/focus-sessions");
      const raw = res.data;
      return Array.isArray(raw) ? raw : Array.isArray(raw?.content) ? raw.content : [];
    },
  });

  async function handleStart() {
    setError(null);
    try {
      const res = await executionApi.post("/focus-sessions/start", { type: "DEEP_WORK" });
      setSessionId(res.data.id);
      start(secondsRemaining || 90 * 60);
    } catch (err: any) {
      setError(err?.response?.data?.error || "Couldn't start a session — is the backend reachable?");
    }
  }

  async function handlePause() {
    pause();
    if (sessionId) {
      try {
        await executionApi.patch(`/focus-sessions/${sessionId}/pause`);
      } catch {
        // Session state on the server is a nice-to-have here; the local
        // timer pausing is what the person actually asked for.
      }
    }
  }

  async function completeSession(id: string) {
    try {
      await executionApi.patch(`/focus-sessions/${id}/complete`);
      queryClient.invalidateQueries({ queryKey: ["focus-sessions"] });
    } catch {
      // Best-effort -- don't block the local reset on a network hiccup.
    }
    setSessionId(null);
    reset();
  }

  return (
    <div className="space-y-5">
      <section>
        <p className="text-sm font-medium text-blue">Deep work</p>
        <h1 className="text-3xl font-semibold">Protect the next block</h1>
      </section>
      <Card className="text-center">
        <TimerReset className="mx-auto text-blue" size={32} />
        <div className="mt-5 text-7xl font-semibold tracking-normal">{minutes}:{seconds}</div>
        {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
        <div className="mt-6 flex justify-center gap-3">
          <button className="touch-target rounded-full bg-blue px-5 text-sm font-semibold text-white" onClick={handleStart} disabled={running}>
            <Play size={18} className="inline" /> {running ? "Running" : "Start"}
          </button>
          <button className="touch-target rounded-full border border-line px-4 text-sm" onClick={handlePause}><Pause size={18} className="inline" /> Pause</button>
          <button className="touch-target rounded-full border border-line px-4 text-sm" onClick={() => { setSessionId(null); reset(); }}><RotateCcw size={18} className="inline" /> Reset</button>
        </div>
      </Card>

      <Card>
        <h2 className="mb-3 font-semibold">Recent sessions</h2>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isError && <p className="text-sm text-red-600">Couldn't load session history.</p>}
        {!isLoading && !isError && sessions.length === 0 && (
          <p className="text-sm text-muted">No focus sessions recorded yet — start one above.</p>
        )}
        <div className="space-y-2">
          {sessions.slice(0, 8).map((s) => (
            <div key={s.id} className="flex items-center justify-between rounded-md border border-line px-3 py-2 text-sm">
              <span>{new Date(s.startTime).toLocaleString()}</span>
              <span className="text-muted">{s.type.replace("_", " ")} · {s.status}</span>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
