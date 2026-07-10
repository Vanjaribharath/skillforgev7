"use client";

import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { NotebookPen } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { executionApi } from "@/lib/execution-api-client";

type JournalEntry = {
  id: string;
  entryDate: string;
  learnings: string | null;
  blockers: string | null;
};

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export default function JournalPage() {
  const today = todayIso();
  const queryClient = useQueryClient();
  const [learnings, setLearnings] = useState("");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  // A 404 here just means no entry exists for today yet, which is the
  // normal starting state -- not an error to show the person.
  const { data: entry, isLoading } = useQuery<JournalEntry | null>({
    queryKey: ["journal-entry", today],
    queryFn: async () => {
      try {
        const res = await executionApi.get("/journal", { params: { date: today } });
        return res.data;
      } catch (err: any) {
        if (err?.response?.status === 404) return null;
        throw err;
      }
    },
  });

  useEffect(() => {
    if (entry?.learnings) setLearnings(entry.learnings);
  }, [entry]);

  async function handleSave() {
    setSaving(true);
    setSaved(false);
    try {
      if (entry?.id) {
        await executionApi.put(`/journal/${entry.id}`, { ...entry, learnings });
      } else {
        await executionApi.post("/journal", { entryDate: today, learnings });
      }
      await queryClient.invalidateQueries({ queryKey: ["journal-entry", today] });
      setSaved(true);
    } catch {
      setSaved(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-5">
      <section className="flex items-center justify-between">
        <div><p className="text-sm font-medium text-blue">Journal</p><h1 className="text-3xl font-semibold">Daily review</h1></div>
        <NotebookPen className="text-blue" />
      </section>
      <Card>
        <label className="text-sm font-medium" htmlFor="learnings">Learnings — {today}</label>
        {isLoading ? (
          <p className="mt-2 text-sm text-muted">Loading…</p>
        ) : (
          <textarea
            id="learnings"
            className="mt-2 min-h-36 w-full rounded-md border border-line p-3 outline-none focus:border-blue"
            placeholder="What did you learn today?"
            value={learnings}
            onChange={(e) => { setLearnings(e.target.value); setSaved(false); }}
          />
        )}
        <div className="mt-3 flex items-center gap-3">
          <Button onClick={handleSave} disabled={saving || isLoading}>{saving ? "Saving…" : "Save"}</Button>
          {saved && <span className="text-sm text-green-700">Saved.</span>}
        </div>
      </Card>
    </div>
  );
}
