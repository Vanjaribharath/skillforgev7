"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { BookOpen, Folder, Plus } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { executionApi } from "@/lib/execution-api-client";

type Category = { id: string; name: string; icon: string | null; color: string | null };

export default function VaultPage() {
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");

  const { data: categories = [], isLoading, isError } = useQuery<Category[]>({
    queryKey: ["vault-categories"],
    queryFn: async () => {
      const res = await executionApi.get("/knowledge/categories", { params: { size: 50 } });
      const raw = res.data;
      return Array.isArray(raw) ? raw : Array.isArray(raw?.content) ? raw.content : [];
    },
  });

  async function handleCreate() {
    if (!name.trim()) return;
    try {
      await executionApi.post("/knowledge/categories", { name: name.trim() });
      setName("");
      setCreating(false);
      await queryClient.invalidateQueries({ queryKey: ["vault-categories"] });
    } catch {
      // Leave the input populated so the person can retry.
    }
  }

  return (
    <div className="space-y-5">
      <section className="flex items-center justify-between">
        <div><p className="text-sm font-medium text-blue">Vault</p><h1 className="text-3xl font-semibold">Knowledge library</h1></div>
        <Button onClick={() => setCreating((v) => !v)}><Plus size={18} /> New category</Button>
      </section>

      {creating && (
        <Card>
          <div className="flex gap-2">
            <Input placeholder="Category name" value={name} onChange={(e) => setName(e.target.value)} />
            <Button onClick={handleCreate}>Create</Button>
          </div>
        </Card>
      )}

      {isLoading && <p className="text-sm text-muted">Loading…</p>}
      {isError && <p className="text-sm text-red-600">Couldn't load categories.</p>}
      {!isLoading && !isError && categories.length === 0 && (
        <Card><p className="text-sm text-muted">No categories yet — create one above to start organizing notes, links, and snippets.</p></Card>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        {categories.map((category) => (
          <Link key={category.id} href={`/vault/${category.id}`}>
            <Card className="transition hover:border-blue">
              <Folder className="mb-3 text-amber" />
              <h2 className="font-semibold">{category.name}</h2>
              <p className="mt-1 text-sm text-muted">Pinned notes, links, snippets, and decisions.</p>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
