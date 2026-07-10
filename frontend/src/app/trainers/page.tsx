"use client";

import { useState } from "react";
import { Plus, UserRoundCog, X } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";
import { useOrganizationStore } from "@/store/use-organization-store";

export default function TrainersPage() {
  const { organizationId } = useOrganizationStore();
  const queryClient = useQueryClient();

  const [showAddModal, setShowAddModal] = useState(false);
  const [newTrainer, setNewTrainer] = useState({ fullName: "", email: "" });
  const [message, setMessage] = useState("");

  const { data: trainers = [] } = useQuery({
    queryKey: ["trainers", organizationId],
    queryFn: async () => {
      const res = await api.get(`/trainers`, { params: { organizationId } });
      return res.data?.content || [];
    },
    enabled: !!organizationId,
  });

  const addTrainerMutation = useMutation({
    mutationFn: async (trainer: { fullName: string; email: string }) => {
      const res = await api.post(`/trainers`, {
        organizationId,
        fullName: trainer.fullName,
        email: trainer.email,
        role: "TRAINER",
      });
      return res.data;
    },
    onSuccess: (created: any) => {
      queryClient.invalidateQueries({ queryKey: ["trainers", organizationId] });
      setShowAddModal(false);
      setNewTrainer({ fullName: "", email: "" });
      setMessage(
        created?.temporaryPassword
          ? `Trainer added. Temporary password (also emailed): ${created.temporaryPassword}`
          : "Trainer added — credentials emailed to them.",
      );
    },
    onError: (error: any) => {
      setMessage(`Failed to add trainer: ${error.response?.data?.error || error.message}`);
    },
  });

  const handleAddTrainer = () => {
    if (!newTrainer.fullName || !newTrainer.email) return;
    addTrainerMutation.mutate(newTrainer);
  };

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm font-medium text-blue">Trainer management</p>
          <h1 className="text-3xl font-semibold tracking-normal">Trainers &amp; evaluators</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
            Add trainers who can author questions, build and publish assessments, and review candidates.
          </p>
        </div>
        <Button onClick={() => setShowAddModal(true)}><Plus size={18} /> Add trainer</Button>
      </section>

      {message && <div className="rounded-md bg-[#EFF6FF] px-3 py-2 text-sm text-blue">{message}</div>}

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Trainers</h2>
          <UserRoundCog className="text-blue" size={20} />
        </div>
        <div className="grid gap-3">
          {trainers.map((trainer: any) => (
            <div key={trainer.id} className="grid gap-3 rounded-md border border-line p-3 md:grid-cols-[1.2fr_1fr_1fr] md:items-center">
              <div>
                <div className="font-semibold">{trainer.fullName}</div>
                <div className="text-sm text-muted">{trainer.email}</div>
              </div>
              <div className="text-sm"><span className="text-muted">Status</span><div className="font-medium">{trainer.status}</div></div>
              <div className="text-sm"><span className="text-muted">Role</span><div className="font-medium">{trainer.role}</div></div>
            </div>
          ))}
          {trainers.length === 0 && (
            <div className="text-sm text-muted text-center py-4">No trainers found.</div>
          )}
        </div>
      </Card>

      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-xl font-semibold">Add New Trainer</h2>
              <button onClick={() => setShowAddModal(false)} className="text-muted hover:text-ink"><X size={20} /></button>
            </div>
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium">Full Name</label>
                <Input className="mt-1" value={newTrainer.fullName} onChange={(e) => setNewTrainer({ ...newTrainer, fullName: e.target.value })} placeholder="Jane Doe" />
              </div>
              <div>
                <label className="text-sm font-medium">Email Address</label>
                <Input className="mt-1" type="email" value={newTrainer.email} onChange={(e) => setNewTrainer({ ...newTrainer, email: e.target.value })} placeholder="jane@example.com" />
              </div>
              <div className="mt-6 flex justify-end gap-2">
                <Button variant="outline" onClick={() => setShowAddModal(false)}>Cancel</Button>
                <Button onClick={handleAddTrainer} disabled={addTrainerMutation.isPending}>
                  {addTrainerMutation.isPending ? "Adding..." : "Add Trainer"}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
