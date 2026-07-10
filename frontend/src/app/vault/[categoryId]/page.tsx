"use client";

import { use, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Pin, Plus } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { executionApi } from "@/lib/execution-api-client";

type KnowledgeItem = {
  id: string;
  title: string;
  content: string | null;
  pinned: boolean;
  category: { id: string } | null;
};

export default function VaultCategoryPage({ params }: { params: Promise<{ categoryId: string }> }) {
  const { categoryId } = use(params);
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [title, setTitle] = useState("");

  const { data: items = [], isLoading, isError } = useQuery<KnowledgeItem[]>({
    queryKey: ["vault-items", categoryId],
    queryFn: async () => {
      const res = await executionApi.get("/knowledge/items", { params: { size: 100 } });
      const raw = res.data;
      const all: KnowledgeItem[] = Array.isArray(raw) ? raw : Array.isArray(raw?.content) ? raw.content : [];
      return all.filter((item) => item.category?.id === categoryId);
    },
  });

  async function handleCreate() {
    if (!title.trim()) return;
    try {
      await executionApi.post("/knowledge/items", { title: title.trim(), category: { id: categoryId } });
      setTitle("");
      setCreating(false);
      await queryClient.invalidateQueries({ queryKey: ["vault-items", categoryId] });
    } catch {
      // Leave input populated to retry.
    }
  }

  return (
    <div className="space-y-5">
      <section className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Category items</h1>
        <Button onClick={() => setCreating((v) => !v)}><Plus size={18} /> New item</Button>
      </section>

      {creating && (
        <Card>
          <div className="flex gap-2">
            <Input placeholder="Item title" value={title} onChange={(e) => setTitle(e.target.value)} />
            <Button onClick={handleCreate}>Create</Button>
          </div>
        </Card>
      )}

      {isLoading && <p className="text-sm text-muted">Loading…</p>}
      {isError && <p className="text-sm text-red-600">Couldn't load items for this category.</p>}
      {!isLoading && !isError && items.length === 0 && (
        <Card><p className="text-sm text-muted">No items in this category yet.</p></Card>
      )}

      <div className="space-y-2">
        {items.map((item) => (
          <Card key={item.id} className="flex items-start justify-between">
            <div>
              <h2 className="font-medium">{item.title}</h2>
              {item.content && <p className="mt-1 text-sm text-muted line-clamp-2">{item.content}</p>}
            </div>
            {item.pinned && <Pin size={16} className="mt-1 shrink-0 text-amber" />}
          </Card>
        ))}
      </div>
    </div>
  );
}
