"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, UsersRound, FileText, ClipboardCheck } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";
import { useOrganizationStore } from "@/store/use-organization-store";

type SearchResponse = {
  query: string;
  candidates: { id: string; fullName: string; email: string }[];
  questions: { id: string; code: string; topic: string | null; difficulty: string }[];
  assessments: { id: string; title: string; status: string }[];
};

export default function SearchPage() {
  const { organizationId } = useOrganizationStore();
  const [query, setQuery] = useState("");
  const [submitted, setSubmitted] = useState("");

  const { data, isFetching } = useQuery({
    queryKey: ["search", organizationId, submitted],
    queryFn: async () => {
      const res = await api.get<SearchResponse>("/search", { params: { organizationId, q: submitted } });
      return res.data;
    },
    enabled: submitted.length > 0 && !!organizationId,
  });

  const totalResults = (data?.candidates.length ?? 0) + (data?.questions.length ?? 0) + (data?.assessments.length ?? 0);

  return (
    <div className="space-y-5">
      <section className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-blue">Global search</p>
          <h1 className="text-3xl font-semibold">Find candidates, questions, assessments</h1>
        </div>
        <Search className="text-blue" />
      </section>

      <Card>
        <form onSubmit={(e) => { e.preventDefault(); setSubmitted(query); }} className="flex gap-2">
          <Input placeholder="Search by name, email, question code, topic, or assessment title" value={query} onChange={(e) => setQuery(e.target.value)} />
        </form>
      </Card>

      {submitted && (
        <>
          {isFetching ? (
            <Card><p className="text-sm text-muted">Searching…</p></Card>
          ) : totalResults === 0 ? (
            <Card><p className="text-sm text-muted">No results for "{submitted}".</p></Card>
          ) : (
            <>
              {data && data.candidates.length > 0 && (
                <Card>
                  <div className="mb-2 flex items-center gap-2 text-sm font-semibold"><UsersRound size={16} /> Candidates ({data.candidates.length})</div>
                  <div className="space-y-1">
                    {data.candidates.map((c) => (
                      <div key={c.id} className="rounded border border-line p-2 text-sm">
                        <span className="font-medium">{c.fullName}</span> <span className="text-muted">{c.email}</span>
                      </div>
                    ))}
                  </div>
                </Card>
              )}
              {data && data.questions.length > 0 && (
                <Card>
                  <div className="mb-2 flex items-center gap-2 text-sm font-semibold"><FileText size={16} /> Questions ({data.questions.length})</div>
                  <div className="space-y-1">
                    {data.questions.map((q) => (
                      <div key={q.id} className="rounded border border-line p-2 text-sm">
                        <span className="font-mono text-xs">{q.code}</span> <span className="text-muted">{q.topic ?? "—"}</span> <span className="text-muted">({q.difficulty})</span>
                      </div>
                    ))}
                  </div>
                </Card>
              )}
              {data && data.assessments.length > 0 && (
                <Card>
                  <div className="mb-2 flex items-center gap-2 text-sm font-semibold"><ClipboardCheck size={16} /> Assessments ({data.assessments.length})</div>
                  <div className="space-y-1">
                    {data.assessments.map((a) => (
                      <div key={a.id} className="rounded border border-line p-2 text-sm">
                        <span className="font-medium">{a.title}</span> <span className="text-muted">({a.status})</span>
                      </div>
                    ))}
                  </div>
                </Card>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}
