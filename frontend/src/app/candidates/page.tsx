"use client";

import { useRef, useState } from "react";
import { FileSpreadsheet, MailPlus, Plus, UsersRound, X } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";
import { useOrganizationStore } from "@/store/use-organization-store";

export default function CandidatesPage() {
  const { organizationId } = useOrganizationStore();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  const [showAddModal, setShowAddModal] = useState(false);
  const [newCandidate, setNewCandidate] = useState({ fullName: "", email: "" });
  const [message, setMessage] = useState("");

  const { data: candidates = [] } = useQuery({
    queryKey: ["candidates", organizationId],
    queryFn: async () => {
      const res = await api.get(`/candidates`, { params: { organizationId } });
      return res.data?.content || [];
    },
    enabled: !!organizationId,
  });

  const addCandidateMutation = useMutation({
    mutationFn: async (candidate: { fullName: string; email: string }) => {
      const res = await api.post(`/candidates`, {
        organizationId,
        fullName: candidate.fullName,
        email: candidate.email,
        role: "CANDIDATE",
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["candidates", organizationId] });
      setShowAddModal(false);
      setNewCandidate({ fullName: "", email: "" });
      setMessage("Candidate added successfully.");
    },
    onError: (error: any) => {
      setMessage(`Failed to add candidate: ${error.response?.data?.error || error.message}`);
    }
  });

  const handleAddCandidate = () => {
    if (!newCandidate.fullName || !newCandidate.email) return;
    addCandidateMutation.mutate(newCandidate);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (event) => {
      const csv = event.target?.result as string;
      const lines = csv.split('\n');
      let successCount = 0;
      
      setMessage("Processing CSV...");
      
      // Simple CSV parsing: assume Name,Email
      for (let i = 1; i < lines.length; i++) {
        const line = lines[i].trim();
        if (!line) continue;
        
        const [fullName, email] = line.split(',');
        if (fullName && email) {
          try {
            await api.post(`/candidates`, {
              organizationId,
              fullName: fullName.trim(),
              email: email.trim(),
              role: "CANDIDATE",
            });
            successCount++;
          } catch (err) {
            console.error("Failed to add from CSV", err);
          }
        }
      }
      
      queryClient.invalidateQueries({ queryKey: ["candidates", organizationId] });
      setMessage(`CSV processed: added ${successCount} candidates.`);
      
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm font-medium text-blue">Candidate management</p>
          <h1 className="text-3xl font-semibold tracking-normal">Batches, history, invitations</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">Manage learners across departments, upload CSV cohorts, track attempts, and send reminders.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setShowAddModal(true)}><Plus size={18} /> Add candidate</Button>
          <Button variant="outline" onClick={() => fileInputRef.current?.click()}><FileSpreadsheet size={18} /> CSV upload</Button>
          <input type="file" ref={fileInputRef} className="hidden" accept=".csv" onChange={handleFileUpload} />
          <Button variant="outline" onClick={() => setMessage("Reminder emails queued for pending assessments.")}><MailPlus size={18} /> Remind</Button>
        </div>
      </section>

      {message && <div className="rounded-md bg-[#EFF6FF] px-3 py-2 text-sm text-blue">{message}</div>}

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Candidate history</h2>
          <UsersRound className="text-blue" size={20} />
        </div>
        <div className="grid gap-3">
          {candidates.map((candidate: any) => (
            <div key={candidate.id} className="grid gap-3 rounded-md border border-line p-3 md:grid-cols-[1.2fr_1fr_1fr] md:items-center">
              <div>
                <div className="font-semibold">{candidate.fullName}</div>
                <div className="text-sm text-muted">{candidate.email}</div>
              </div>
              <div className="text-sm"><span className="text-muted">Status</span><div className="font-medium">{candidate.status}</div></div>
              <div className="text-sm"><span className="text-muted">Role</span><div className="font-medium">{candidate.role}</div></div>
            </div>
          ))}
          {candidates.length === 0 && (
            <div className="text-sm text-muted text-center py-4">No candidates found.</div>
          )}
        </div>
      </Card>

      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-xl font-semibold">Add New Candidate</h2>
              <button onClick={() => setShowAddModal(false)} className="text-muted hover:text-ink"><X size={20} /></button>
            </div>
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium">Full Name</label>
                <Input className="mt-1" value={newCandidate.fullName} onChange={(e) => setNewCandidate({ ...newCandidate, fullName: e.target.value })} placeholder="John Doe" />
              </div>
              <div>
                <label className="text-sm font-medium">Email Address</label>
                <Input className="mt-1" type="email" value={newCandidate.email} onChange={(e) => setNewCandidate({ ...newCandidate, email: e.target.value })} placeholder="john@example.com" />
              </div>
              <div className="mt-6 flex justify-end gap-2">
                <Button variant="outline" onClick={() => setShowAddModal(false)}>Cancel</Button>
                <Button onClick={handleAddCandidate} disabled={addCandidateMutation.isPending}>
                  {addCandidateMutation.isPending ? "Adding..." : "Add Candidate"}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
