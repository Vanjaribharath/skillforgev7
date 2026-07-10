# Master Prompt — SkillForge Question Bank Generator

Use this prompt whenever you want more questions for a subject already in
this pack, or a brand-new subject. Paste it into a fresh Claude conversation
(with Code Execution / file creation on), fill in the bracketed values, and
Claude will hand you back an importable `.json` file in the same schema as
`linux.json`, `java.json`, etc. Run the import script afterward — it's
idempotent, so re-running never creates duplicates.

Keep batches to **15–30 questions per request**. Larger batches quietly drop
in quality and start repeating patterns — many focused batches beat one huge
one, and you can review each batch before it goes live for candidates.

---

## The Prompt

```
Generate [N] coding questions for the subject "[SUBJECT NAME]" for a
technical hiring/training assessment platform (HackerRank-style).

Difficulty split: [X] EASY, [Y] MEDIUM, [Z] HARD.
Level: mostly basic-to-intermediate — assume a candidate with 0-2 years of
hands-on experience with this subject. Avoid obscure edge cases or
rarely-used advanced features unless explicitly asked for HARD questions.

Topics to cover (spread across the batch, don't repeat the same topic twice
unless N is large): [list 8-15 topics, e.g. for Linux: file permissions,
process management, grep/sed/awk, cron, networking basics, disk usage,
archiving, shell piping]

For EACH question, output a JSON object with exactly these fields:
{
  "subjectSlug": "[slug-form of subject, e.g. 'linux']",
  "subjectName": "[Subject Name]",
  "code": "[SUBJECT]-COD-[DIFFICULTY]-[3-digit sequence]",
  "type": "CODING",
  "difficulty": "EASY" | "MEDIUM" | "HARD",
  "topic": "[specific topic within the subject]",
  "title": "[short 3-8 word title]",
  "prompt": "[the problem statement a candidate would read — be specific,
              include sample input/output or schema context where relevant]",
  "options": [],
  "correctAnswer": { "solution": "[the reference solution — real, runnable
                       code/command/query, not pseudocode]" },
  "explanation": "[2-4 sentences: WHY the solution works and what concept
                    it tests — this is what the trainer sees after grading,
                    not just a restatement of the answer]",
  "expectedTimeSeconds": [60-120 for EASY, 150-240 for MEDIUM, 240-400 for HARD],
  "defaultMarks": [1 for EASY, 2 for MEDIUM, 3 for HARD],
  "negativeMarks": 0,
  "shuffleGroup": "BEGINNER_POOL" for EASY, "STANDARD" for MEDIUM, "ADVANCED_POOL" for HARD,
  "sortOrder": [sequence number within this batch, starting at the next
                 available number after what's already imported]
}

Hard requirements:
- Every "correctAnswer.solution" must be real, correct, and something you
  would trust to run/execute as-is — verify it mentally line by line before
  including it. Do not invent syntax that doesn't exist in the target tool.
- No two questions in the batch may test the exact same concept with only
  the surface wording changed.
- Do not pad prompts or explanations with filler — every sentence should
  carry information a candidate or trainer actually needs.
- Output a JSON array of exactly [N] objects and nothing else — no markdown
  fences, no commentary before or after.
```

---

## Example filled-in request

```
Generate 20 coding questions for the subject "Kafka" for a technical
hiring/training assessment platform.

Difficulty split: 8 EASY, 8 MEDIUM, 4 HARD.
Level: mostly basic-to-intermediate — assume a candidate with 0-2 years of
hands-on experience.

Topics to cover: producer/consumer basics, topics & partitions, consumer
groups, offsets & commits, replication factor, CLI tools (kafka-topics,
kafka-console-producer), Spring Kafka basics, serialization, error handling
/ dead-letter topics, partition keys & ordering.

[... rest of the field spec and hard requirements from above ...]
```

## After Claude generates the batch

1. Save the output as `kafka.json` (or `kafka-batch-2.json` if you're adding
   more to a subject that already has a file — the importer merges by code,
   so duplicates across files are automatically skipped).
2. Run:
   ```
   python3 import_question_bank.py --organization-id <your-org-uuid>
   ```
3. Spot-check 3-5 questions per batch in the trainer UI before publishing
   any assessment that uses them — an LLM-authored batch should always get
   a human skim before candidates see it, the same way you'd review a
   junior engineer's PR.

## Scaling to 999-1999 per subject

Run the prompt above repeatedly for the same subject with a fresh topic
list each time (or ask Claude to avoid repeating topics/titles already in
your existing file, which you can paste in as context). At ~20-30 questions
per batch, reaching 999 per subject is ~35-50 batches — plan for this as an
ongoing content operation, not a one-time generation, the same way any real
question bank (including HackerRank's own) is built up over months, not
one prompt.
