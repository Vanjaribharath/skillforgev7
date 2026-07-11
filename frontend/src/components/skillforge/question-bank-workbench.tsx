"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { CheckCircle2, FileSpreadsheet, Filter, GitBranch, Loader2, Plus, RefreshCcw, Search, Send, Shuffle, X } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api-client";
import { statusClass } from "@/lib/skillforge-data";
import { useOrganizationStore } from "@/store/use-organization-store";
import { useAuthStore } from "@/store/use-auth-store";

type Difficulty = "EASY" | "MEDIUM" | "HARD";

type CatalogQuestion = {
  id: string;
  subject: string;
  topic: string;
  type: string;
  difficulty: Difficulty;
  prompt: string;
  options: string; // JSON-encoded array of option strings
  expectedTimeSeconds: number;
};

type SubjectCoverage = {
  subject: string;
  slug: string;
  totalQuestions: number;
  easy: number;
  medium: number;
  hard: number;
};

type ImportReport = {
  totalRecords: number;
  importedSuccessfully: number;
  duplicates: number;
  invalidQuestions: number;
  failedRows: number;
  warnings: string[];
};

const fallbackSubjects = ["Java", "Spring Boot", "Linux", "SQL", "Kafka", "Docker"];

const QUESTION_TYPES: { value: string; label: string; hasOptions: boolean }[] = [
  { value: "MULTIPLE_CHOICE", label: "Multiple choice (one correct answer)", hasOptions: true },
  { value: "MULTIPLE_SELECT", label: "Multiple select (several correct answers)", hasOptions: true },
  { value: "TRUE_FALSE", label: "True / False", hasOptions: true },
  { value: "FILL_BLANK", label: "Fill in the blank", hasOptions: false },
  { value: "CODE_OUTPUT", label: "Predict the code output", hasOptions: false },
  { value: "CODE_COMPLETION", label: "Code completion", hasOptions: false },
  { value: "CODING", label: "Coding exercise", hasOptions: false },
  { value: "SCENARIO", label: "Scenario / free response", hasOptions: false },
  { value: "ORDERING", label: "Ordering", hasOptions: false },
  { value: "DRAG_DROP", label: "Drag and drop", hasOptions: false },
];

/** Wraps a CSV cell in quotes and doubles any internal quotes, only when needed. */
function csvCell(value: string): string {
  if (value.includes(",") || value.includes("\"") || value.includes("\n")) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

type NewQuestionForm = {
  subject: string;
  type: string;
  difficulty: Difficulty;
  prompt: string;
  options: string[];
  correctAnswers: string[];
  freeTextAnswer: string;
  expectedTimeSeconds: string;
  marks: string;
  topic: string;
  explanation: string;
};

function blankQuestionForm(defaultSubject: string): NewQuestionForm {
  return {
    subject: defaultSubject,
    type: "MULTIPLE_CHOICE",
    difficulty: "EASY",
    prompt: "",
    options: ["", ""],
    correctAnswers: [],
    freeTextAnswer: "",
    expectedTimeSeconds: "60",
    marks: "1",
    topic: "",
    explanation: "",
  };
}

export function QuestionBankWorkbench() {
  const organizationId = useOrganizationStore((s) => s.organizationId);
  const currentUserId = useAuthStore((s) => s.user?.id);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [coverage, setCoverage] = useState<SubjectCoverage[]>([]);
  const [questions, setQuestions] = useState<CatalogQuestion[]>([]);
  const [subject, setSubject] = useState("Java");
  const [difficulty, setDifficulty] = useState<Difficulty>("EASY");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Record<string, CatalogQuestion>>({});
  const [message, setMessage] = useState("Load the enterprise question bank to begin.");
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importReport, setImportReport] = useState<ImportReport | null>(null);
  const [showNewQuestionModal, setShowNewQuestionModal] = useState(false);
  const [newQuestion, setNewQuestion] = useState<NewQuestionForm>(() => blankQuestionForm("Java"));
  const [submittingQuestion, setSubmittingQuestion] = useState(false);
  const [newQuestionError, setNewQuestionError] = useState<string | null>(null);
  // Shown right next to "Randomize" / "Use in assessment" -- the old code only
  // ever wrote to `message`, which renders far away in the left-hand card, so
  // clicking these buttons looked like nothing had happened.
  const [poolMessage, setPoolMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!organizationId) return;
    void loadCoverage();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [organizationId]);

  useEffect(() => {
    if (!organizationId) return;
    void loadQuestions(subject, difficulty);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subject, difficulty, organizationId]);

  const visibleQuestions = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return questions;
    return questions.filter((question) => `${question.id} ${question.subject} ${question.topic} ${question.prompt}`.toLowerCase().includes(normalized));
  }, [questions, query]);

  const selectedQuestions = Object.values(selected);

  async function loadCoverage() {
    try {
      const response = await api.get("/catalog/coverage", { params: { organizationId } });
      const data = Array.isArray(response.data) ? (response.data as SubjectCoverage[]) : [];
      setCoverage(data);
      if (data.length > 0) {
        const total = data.reduce((sum, item) => sum + item.totalQuestions, 0);
        setMessage(total > 0
          ? `Loaded ${data.length} subjects with ${total.toLocaleString()} approved questions in the bank.`
          : `Loaded ${data.length} subjects — no approved questions yet. Import a CSV to populate the bank.`);
      } else {
        setMessage("No subject coverage data returned from the backend.");
      }
    } catch (error: any) {
      setCoverage([]);
      setMessage(error?.response?.data?.error || error.message || "Unable to load backend coverage.");
    }
  }

  async function loadQuestions(nextSubject = subject, nextDifficulty = difficulty) {
    setLoading(true);
    try {
      const response = await api.get("/catalog/questions", {
        params: { organizationId, subject: nextSubject, difficulty: nextDifficulty, page: 0, size: 50 },
      });
      const raw = response.data;
      const list: CatalogQuestion[] = Array.isArray(raw) ? raw : Array.isArray(raw?.content) ? raw.content : [];
      setQuestions(list);
      if (list.length === 0) {
        setMessage(`No approved questions for ${nextSubject} (${nextDifficulty}) yet. Import a CSV to add some.`);
      }
    } catch (error: any) {
      setQuestions([]);
      setMessage(error?.response?.data?.error || error.message || "Unable to load questions.");
    } finally {
      setLoading(false);
    }
  }

  function parseOptions(json: string): string[] {
    try {
      const parsed = JSON.parse(json);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function toggle(question: CatalogQuestion) {
    setSelected((current) => {
      const copy = { ...current };
      if (copy[question.id]) {
        delete copy[question.id];
      } else {
        copy[question.id] = question;
      }
      return copy;
    });
  }

  function randomizePool() {
    const shuffled = [...questions].sort(() => Math.random() - 0.5).slice(0, 20);
    setSelected(Object.fromEntries(shuffled.map((question) => [question.id, question])));
    const text = `Randomized ${shuffled.length} ${subject} questions for trainer review.`;
    setMessage(text);
    setPoolMessage(text);
  }

  function publishPool() {
    if (selectedQuestions.length === 0) {
      const text = "Select questions before publishing an assessment pool.";
      setMessage(text);
      setPoolMessage(text);
      return;
    }
    const text = `Selected ${selectedQuestions.length} questions. Head to Assessments → your assessment → "Add questions" to attach this pool and publish it to candidates.`;
    setMessage(text);
    setPoolMessage(text);
  }

  function updateNewQuestionField<K extends keyof NewQuestionForm>(field: K, value: NewQuestionForm[K]) {
    setNewQuestion((current) => ({ ...current, [field]: value }));
  }

  function updateOption(index: number, value: string) {
    setNewQuestion((current) => {
      const options = [...current.options];
      options[index] = value;
      return { ...current, options };
    });
  }

  function addOption() {
    setNewQuestion((current) => ({ ...current, options: [...current.options, ""] }));
  }

  function removeOption(index: number) {
    setNewQuestion((current) => ({
      ...current,
      options: current.options.filter((_, i) => i !== index),
      correctAnswers: current.correctAnswers.filter((answer) => answer !== current.options[index]),
    }));
  }

  function toggleCorrectAnswer(option: string) {
    setNewQuestion((current) => {
      const isMulti = current.type === "MULTIPLE_SELECT";
      if (isMulti) {
        const exists = current.correctAnswers.includes(option);
        return { ...current, correctAnswers: exists ? current.correctAnswers.filter((a) => a !== option) : [...current.correctAnswers, option] };
      }
      return { ...current, correctAnswers: [option] };
    });
  }

  function openNewQuestionModal() {
    setNewQuestion(blankQuestionForm(subject));
    setNewQuestionError(null);
    setShowNewQuestionModal(true);
  }

  /**
   * There is no single-question "create" screen in the API that a form can
   * safely target end-to-end (POST /questions alone leaves the question in
   * DRAFT status, invisible in this catalog until a separate submit-review +
   * approve step runs). The CSV import endpoint already does all of that in
   * one trusted, tested step -- APPROVED immediately, correct-answer
   * validated, subject resolved -- for any ADMIN/TRAINER/EVALUATOR account.
   * So this form builds a valid one-row CSV in memory and reuses that same
   * endpoint, rather than duplicating (and re-testing) that logic.
   */
  async function submitNewQuestion() {
    if (!organizationId || !currentUserId) {
      setNewQuestionError("Sign in again — missing organization or user context.");
      return;
    }
    const typeMeta = QUESTION_TYPES.find((t) => t.value === newQuestion.type)!;
    const prompt = newQuestion.prompt.trim();
    if (!prompt) {
      setNewQuestionError("Prompt is required.");
      return;
    }
    if (!subjects.map((s) => s.toLowerCase()).includes(newQuestion.subject.trim().toLowerCase())) {
      setNewQuestionError(`"${newQuestion.subject}" isn't an existing subject. Pick one from the dropdown — new subjects can't be created from this form yet.`);
      return;
    }
    const cleanOptions = newQuestion.options.map((o) => o.trim()).filter(Boolean);
    if (typeMeta.hasOptions && cleanOptions.length < 2) {
      setNewQuestionError("Add at least two options for this question type.");
      return;
    }
    let correctAnswerCell = "";
    if (typeMeta.hasOptions) {
      if (newQuestion.correctAnswers.length === 0) {
        setNewQuestionError("Select the correct answer (or answers).");
        return;
      }
      correctAnswerCell = newQuestion.correctAnswers.join("|");
    } else {
      correctAnswerCell = newQuestion.freeTextAnswer.trim();
    }

    const header = "subject,prompt,type,difficulty,options,correct_answer,expected_time_seconds,marks,topic,explanation";
    const row = [
      newQuestion.subject.trim(),
      prompt,
      newQuestion.type,
      newQuestion.difficulty,
      typeMeta.hasOptions ? cleanOptions.join("|") : "",
      correctAnswerCell,
      newQuestion.expectedTimeSeconds.trim() || "60",
      newQuestion.marks.trim() || "1",
      newQuestion.topic.trim(),
      newQuestion.explanation.trim(),
    ].map(csvCell).join(",");
    const csv = `${header}\n${row}`;

    setSubmittingQuestion(true);
    setNewQuestionError(null);
    try {
      const response = await api.post<ImportReport>("/questions/import/csv", csv, {
        params: { organizationId, createdBy: currentUserId },
        headers: { "Content-Type": "text/plain" },
      });
      if (response.data.importedSuccessfully >= 1) {
        setMessage(`Question added to the ${newQuestion.subject} bank and approved — it's visible below immediately.`);
        setShowNewQuestionModal(false);
        await loadCoverage();
        await loadQuestions(newQuestion.subject, newQuestion.difficulty);
      } else {
        const reason = response.data.warnings[0] || "The question wasn't accepted — check the correct answer matches one of the options exactly.";
        setNewQuestionError(reason);
      }
    } catch (error: any) {
      setNewQuestionError(error?.response?.data?.error || error.message || "Couldn't save the question — check your connection and try again.");
    } finally {
      setSubmittingQuestion(false);
    }
  }

  async function handleCsvFile(file: File) {
    if (!organizationId || !currentUserId) {
      setMessage("Sign in again — missing organization or user context for import.");
      return;
    }
    setImporting(true);
    setImportReport(null);
    try {
      const text = await file.text();
      const response = await api.post<ImportReport>("/questions/import/csv", text, {
        params: { organizationId, createdBy: currentUserId },
        headers: { "Content-Type": "text/plain" },
      });
      setImportReport(response.data);
      setMessage(`Import complete: ${response.data.importedSuccessfully} added, ${response.data.duplicates} duplicates, ${response.data.invalidQuestions} invalid, ${response.data.failedRows} failed.`);
      await loadCoverage();
      await loadQuestions();
    } catch (error: any) {
      setMessage(error?.response?.data?.error || error.message || "CSV import failed.");
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  const subjects = coverage.length > 0 ? coverage.map((item) => item.subject) : fallbackSubjects;

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-sm font-medium text-blue">Question bank</p>
          <h1 className="text-3xl font-semibold tracking-normal">Trainer-accessible enterprise catalog</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">Browse approved questions by subject and difficulty, or import a CSV to grow the bank. Correct answers are never sent to this view — only trainers reviewing a specific question via the approval workflow can see them.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={openNewQuestionModal}><Plus size={18} /> New question</Button>
          <input ref={fileInputRef} type="file" accept=".csv,text/csv" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) void handleCsvFile(f); }} />
          <Button variant="outline" disabled={importing} onClick={() => fileInputRef.current?.click()}>
            <FileSpreadsheet size={18} /> {importing ? "Importing…" : "Import CSV"}
          </Button>
          <Button variant="outline" onClick={() => void loadCoverage()}><RefreshCcw size={18} /> Reload</Button>
        </div>
      </section>

      <Card className="border-line bg-surface text-sm">
        <div className="mb-2 font-semibold text-ink">CSV format for Import CSV</div>
        <p className="mb-2 text-muted">
          First row must be a header with exactly these column names (any order). One question per row:
        </p>
        <div className="mb-2 overflow-x-auto rounded-md border border-line bg-white p-2 font-mono text-xs">
          subject, prompt, type, difficulty, options, correct_answer, expected_time_seconds, marks, topic, explanation
        </div>
        <ul className="mb-2 list-inside list-disc space-y-1 text-muted">
          <li><span className="font-medium text-ink">subject</span> — must match an existing subject (e.g. Java, Linux, SQL) shown in the filter dropdown above.</li>
          <li><span className="font-medium text-ink">type</span> — one of MULTIPLE_CHOICE, MULTIPLE_SELECT, TRUE_FALSE, FILL_BLANK, CODE_OUTPUT, ORDERING, SCENARIO.</li>
          <li><span className="font-medium text-ink">difficulty</span> — EASY, MEDIUM, or HARD.</li>
          <li><span className="font-medium text-ink">options</span> — pipe-separated, e.g. <code>Yes|No|Maybe</code> (leave blank for free-text question types).</li>
          <li><span className="font-medium text-ink">correct_answer</span> — must exactly match one of the options for MULTIPLE_CHOICE/TRUE_FALSE; pipe-separated for MULTIPLE_SELECT.</li>
          <li><span className="font-medium text-ink">expected_time_seconds</span>, <span className="font-medium text-ink">marks</span>, <span className="font-medium text-ink">topic</span>, <span className="font-medium text-ink">explanation</span> — optional; sensible defaults are used if left blank.</li>
        </ul>
        <p className="mb-2 text-muted">
          Rows are never rejected as a batch — each row is validated independently, and the import summary below tells you exactly how many were imported, duplicated, invalid, or failed, with a reason per row.
        </p>
        <Button
          variant="outline"
          onClick={() => {
            const sample = [
              "subject,prompt,type,difficulty,options,correct_answer,expected_time_seconds,marks,topic,explanation",
              'Java,"What does the ""final"" keyword do on a variable?",MULTIPLE_CHOICE,EASY,"Prevents reassignment|Makes it static|Makes it private|Deletes it after use",Prevents reassignment,60,1,Keywords,"final variables can only be assigned once."',
              'Linux,"Which command lists running processes?",MULTIPLE_CHOICE,EASY,"ps|cat|ls|touch",ps,45,1,Process management,"ps aux is the common form."',
            ].join("\n");
            const blob = new Blob([sample], { type: "text/csv" });
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = "skillforge-sample-questions.csv";
            a.click();
            URL.revokeObjectURL(url);
          }}
        >
          <FileSpreadsheet size={16} /> Download sample CSV
        </Button>
      </Card>

      {importReport && (
        <Card className="border-blue/30 bg-[#EFF6FF]">
          <div className="flex flex-wrap gap-4 text-sm">
            <div><span className="font-semibold">{importReport.totalRecords}</span> rows</div>
            <div className="text-green-700"><span className="font-semibold">{importReport.importedSuccessfully}</span> imported</div>
            <div className="text-amber-700"><span className="font-semibold">{importReport.duplicates}</span> duplicates</div>
            <div className="text-red-700"><span className="font-semibold">{importReport.invalidQuestions}</span> invalid</div>
            <div className="text-red-700"><span className="font-semibold">{importReport.failedRows}</span> failed</div>
          </div>
          {importReport.warnings.length > 0 && (
            <ul className="mt-2 max-h-32 space-y-1 overflow-auto text-xs text-muted">
              {importReport.warnings.map((w, i) => <li key={i}>• {w}</li>)}
            </ul>
          )}
        </Card>
      )}

      <Card>
        <div className="grid gap-3 lg:grid-cols-[1fr_180px_180px_auto_auto]">
          <label className="relative block">
            <Search className="pointer-events-none absolute left-3 top-3 text-muted" size={18} />
            <Input className="pl-10" placeholder="Search loaded questions" value={query} onChange={(event) => setQuery(event.target.value)} />
          </label>
          <select className="h-11 rounded-md border border-line bg-white px-3 text-sm" value={subject} onChange={(event) => setSubject(event.target.value)}>
            {subjects.map((item) => <option key={item}>{item}</option>)}
          </select>
          <select className="h-11 rounded-md border border-line bg-white px-3 text-sm" value={difficulty} onChange={(event) => setDifficulty(event.target.value as Difficulty)}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
          <Button variant="outline" onClick={randomizePool}><Shuffle size={18} /> Randomize</Button>
          <Button onClick={publishPool}><Send size={18} /> Publish pool</Button>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {coverage.slice(0, 16).map((item) => (
            <button key={item.slug} onClick={() => setSubject(item.subject)} className="rounded-md border border-line bg-white px-3 py-1.5 text-xs font-medium text-muted hover:bg-surface">
              {item.subject}: {item.totalQuestions.toLocaleString()}
            </button>
          ))}
        </div>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[1fr_320px]">
        <Card>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Question lifecycle</h2>
            <GitBranch className="text-blue" size={20} />
          </div>
          <div className="mb-3 rounded-md bg-[#EFF6FF] px-3 py-2 text-sm text-blue">{message}</div>
          <div className="overflow-hidden rounded-md border border-line">
            <table className="w-full min-w-[860px] text-left text-sm">
              <thead className="bg-surface text-xs uppercase text-muted">
                <tr>
                  <th className="px-3 py-3">Select</th>
                  <th className="px-3 py-3">Code</th>
                  <th className="px-3 py-3">Subject</th>
                  <th className="px-3 py-3">Type</th>
                  <th className="px-3 py-3">Difficulty</th>
                  <th className="px-3 py-3">Prompt</th>
                  <th className="px-3 py-3">Options</th>
                  <th className="px-3 py-3">Time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {visibleQuestions.map((question) => (
                  <tr key={question.id} className="bg-white">
                    <td className="px-3 py-3"><input aria-label={`Select ${question.id}`} type="checkbox" checked={Boolean(selected[question.id])} onChange={() => toggle(question)} /></td>
                    <td className="px-3 py-3 font-mono text-xs font-semibold">{question.id.slice(0, 8)}</td>
                    <td className="px-3 py-3">{question.subject}</td>
                    <td className="px-3 py-3 text-muted">{question.type.replaceAll("_", " ")}</td>
                    <td className="px-3 py-3"><span className={`rounded-md px-2 py-1 text-xs font-medium ${statusClass(question.difficulty === "HARD" ? "Review" : "Approved")}`}>{question.difficulty}</span></td>
                    <td className="px-3 py-3 text-muted">{question.prompt}</td>
                    <td className="px-3 py-3 text-xs text-muted">{parseOptions(question.options).length} choices</td>
                    <td className="px-3 py-3">{question.expectedTimeSeconds}s</td>
                  </tr>
                ))}
                {!loading && visibleQuestions.length === 0 && (
                  <tr><td className="px-3 py-8 text-center text-muted" colSpan={8}>No approved questions loaded yet — import a CSV above to populate this subject.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>

        <Card>
          <div className="mb-4 flex items-center gap-2"><Filter className="text-blue" size={19} /><h2 className="font-semibold">Selected pool</h2></div>
          <div className="text-3xl font-semibold">{selectedQuestions.length}</div>
          <p className="mt-1 text-sm text-muted">questions selected for randomized delivery</p>
          <div className="mt-4 max-h-96 space-y-2 overflow-auto">
            {selectedQuestions.map((question) => (
              <div key={question.id} className="rounded-md border border-line p-2 text-sm">
                <div className="font-mono text-xs font-semibold">{question.id.slice(0, 8)}</div>
                <div className="mt-1 text-muted">{question.topic}</div>
              </div>
            ))}
          </div>
          <Button className="mt-4 w-full" onClick={publishPool}><CheckCircle2 size={17} /> Use in assessment</Button>
          {poolMessage && (
            <p className="mt-2 rounded-md bg-[#EFF6FF] px-3 py-2 text-xs text-blue">{poolMessage}</p>
          )}
        </Card>
      </div>

      {showNewQuestionModal && (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/40 p-4 pt-10 sm:pt-16" onClick={() => !submittingQuestion && setShowNewQuestionModal(false)}>
          <div className="w-full max-w-2xl rounded-lg bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold">New question</h2>
              <button aria-label="Close" onClick={() => !submittingQuestion && setShowNewQuestionModal(false)} className="text-muted hover:text-ink"><X size={20} /></button>
            </div>

            {newQuestionError && (
              <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{newQuestionError}</div>
            )}

            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-3">
                <label className="text-sm">
                  <span className="mb-1 block font-medium text-ink">Subject</span>
                  <select className="h-10 w-full rounded-md border border-line bg-white px-3 text-sm" value={newQuestion.subject} onChange={(e) => updateNewQuestionField("subject", e.target.value)}>
                    {subjects.map((s) => <option key={s}>{s}</option>)}
                  </select>
                </label>
                <label className="text-sm">
                  <span className="mb-1 block font-medium text-ink">Difficulty</span>
                  <select className="h-10 w-full rounded-md border border-line bg-white px-3 text-sm" value={newQuestion.difficulty} onChange={(e) => updateNewQuestionField("difficulty", e.target.value as Difficulty)}>
                    <option value="EASY">Easy</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HARD">Hard</option>
                  </select>
                </label>
                <label className="text-sm">
                  <span className="mb-1 block font-medium text-ink">Type</span>
                  <select
                    className="h-10 w-full rounded-md border border-line bg-white px-3 text-sm"
                    value={newQuestion.type}
                    onChange={(e) => {
                      const type = e.target.value;
                      const isTrueFalse = type === "TRUE_FALSE";
                      setNewQuestion((current) => ({
                        ...current,
                        type,
                        options: isTrueFalse ? ["True", "False"] : current.options,
                        correctAnswers: [],
                      }));
                    }}
                  >
                    {QUESTION_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                  </select>
                </label>
              </div>

              <label className="block text-sm">
                <span className="mb-1 block font-medium text-ink">Prompt</span>
                <textarea
                  className="min-h-[80px] w-full rounded-md border border-line bg-white px-3 py-2 text-sm"
                  placeholder="e.g. What does the final keyword do on a variable?"
                  value={newQuestion.prompt}
                  onChange={(e) => updateNewQuestionField("prompt", e.target.value)}
                />
              </label>

              {QUESTION_TYPES.find((t) => t.value === newQuestion.type)?.hasOptions ? (
                <div>
                  <span className="mb-1 block text-sm font-medium text-ink">Options — tick the correct one{newQuestion.type === "MULTIPLE_SELECT" ? "(s)" : ""}</span>
                  <div className="space-y-2">
                    {newQuestion.options.map((option, index) => (
                      <div key={index} className="flex items-center gap-2">
                        <input
                          type={newQuestion.type === "MULTIPLE_SELECT" ? "checkbox" : "radio"}
                          name="correct-answer"
                          checked={option.trim().length > 0 && newQuestion.correctAnswers.includes(option.trim())}
                          onChange={() => option.trim() && toggleCorrectAnswer(option.trim())}
                        />
                        <Input
                          value={option}
                          placeholder={`Option ${index + 1}`}
                          disabled={newQuestion.type === "TRUE_FALSE"}
                          onChange={(e) => updateOption(index, e.target.value)}
                        />
                        {newQuestion.type !== "TRUE_FALSE" && newQuestion.options.length > 2 && (
                          <button aria-label="Remove option" onClick={() => removeOption(index)} className="text-muted hover:text-red-600"><X size={16} /></button>
                        )}
                      </div>
                    ))}
                  </div>
                  {newQuestion.type !== "TRUE_FALSE" && (
                    <Button variant="outline" className="mt-2" onClick={addOption}><Plus size={14} /> Add option</Button>
                  )}
                </div>
              ) : (
                <label className="block text-sm">
                  <span className="mb-1 block font-medium text-ink">Expected answer</span>
                  <Input value={newQuestion.freeTextAnswer} onChange={(e) => updateNewQuestionField("freeTextAnswer", e.target.value)} placeholder="Model answer used for manual/trainer evaluation" />
                </label>
              )}

              <div className="grid gap-3 sm:grid-cols-3">
                <label className="text-sm">
                  <span className="mb-1 block font-medium text-ink">Time (seconds)</span>
                  <Input value={newQuestion.expectedTimeSeconds} onChange={(e) => updateNewQuestionField("expectedTimeSeconds", e.target.value)} />
                </label>
                <label className="text-sm">
                  <span className="mb-1 block font-medium text-ink">Marks</span>
                  <Input value={newQuestion.marks} onChange={(e) => updateNewQuestionField("marks", e.target.value)} />
                </label>
                <label className="text-sm">
                  <span className="mb-1 block font-medium text-ink">Topic (optional)</span>
                  <Input value={newQuestion.topic} onChange={(e) => updateNewQuestionField("topic", e.target.value)} />
                </label>
              </div>

              <label className="block text-sm">
                <span className="mb-1 block font-medium text-ink">Explanation (optional, shown to candidates after grading)</span>
                <textarea
                  className="min-h-[60px] w-full rounded-md border border-line bg-white px-3 py-2 text-sm"
                  value={newQuestion.explanation}
                  onChange={(e) => updateNewQuestionField("explanation", e.target.value)}
                />
              </label>
            </div>

            <div className="mt-6 flex justify-end gap-2">
              <Button variant="outline" onClick={() => setShowNewQuestionModal(false)} disabled={submittingQuestion}>Cancel</Button>
              <Button onClick={() => void submitNewQuestion()} disabled={submittingQuestion}>
                {submittingQuestion ? <><Loader2 size={16} className="animate-spin" /> Saving…</> : <>Save question</>}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
