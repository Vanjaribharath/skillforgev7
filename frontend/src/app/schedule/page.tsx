"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Plus } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { executionApi } from "@/lib/execution-api-client";

type Schedule = { id: string; name: string; cronExpression: string | null; active: boolean; color: string | null };

export default function SchedulePage() {
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");

  // The real backend concept here is a named, recurring schedule (cron
  // expression + active flag), not "today's timed calendar blocks with a
  // live status" the way the old mock data implied -- rendering what the
  // data actually is rather than forcing a UI shape it was never built for.
  const { data: schedules = [], isLoading, isError } = useQuery<Schedule[]>({
    queryKey: ["schedules"],
    queryFn: async () => {
      const res = await executionApi.get("/schedules");
      return Array.isArray(res.data) ? res.data : [];
    },
  });

  async function handleCreate() {
    if (!name.trim()) return;
    try {
      await executionApi.post("/schedules", { name: name.trim(), active: true });
      setName("");
      setCreating(false);
      await queryClient.invalidateQueries({ queryKey: ["schedules"] });
    } catch {
      // Leave input populated to retry.
    }
  }

  return (
    <div className="space-y-5">
      <section className="flex items-center justify-between">
        <div><h1 className="text-3xl font-semibold">Schedule</h1><p className="mt-1 text-sm text-muted">Recurring schedules, driven by cron expressions.</p></div>
        <Button onClick={() => setCreating((v) => !v)}><Plus size={18} /> New schedule</Button>
      </section>

      {creating && (
        <Card>
          <div className="flex gap-2">
            <Input placeholder="Schedule name" value={name} onChange={(e) => setName(e.target.value)} />
            <Button onClick={handleCreate}>Create</Button>
          </div>
        </Card>
      )}

      {isLoading && <p className="text-sm text-muted">Loading…</p>}
      {isError && <p className="text-sm text-red-600">Couldn't load schedules.</p>}
      {!isLoading && !isError && schedules.length === 0 && (
        <Card><p className="text-sm text-muted">No schedules yet — create one above.</p></Card>
      )}

      <Card>
        <div className="grid gap-3">
          {schedules.map((s) => (
            <div key={s.id} className="grid grid-cols-[auto_1fr_auto] items-center gap-3 rounded-md border border-line p-3">
              <CalendarDays size={18} style={{ color: s.color ?? undefined }} className={s.color ? undefined : "text-muted"} />
              <div>
                <div className="font-medium">{s.name}</div>
                <div className="mt-1 font-mono text-xs text-muted">{s.cronExpression ?? "no cron expression set"}</div>
              </div>
              <span className={`rounded-md px-2 py-1 text-xs font-medium ${s.active ? "bg-green-100 text-green-700" : "bg-surface text-muted"}`}>
                {s.active ? "Active" : "Inactive"}
              </span>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
