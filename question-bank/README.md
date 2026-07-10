# Question Bank — 270 real questions across 9 subjects

30 questions each: Linux, Shell, SQL, Java, Spring Boot, Splunk, New Relic,
ServiceNow, AI Prompt Engineering. Reviewed for content safety before
inclusion (see MERGE_AND_FIX_REPORT.md) — nothing suspicious found.

## Prerequisites

1. The V9 migration (`backend/src/main/resources/db/migration/V9__question_bank_upgrade.sql`)
   must have already run against your database — it adds the `topic`,
   `sort_order`, and `shuffle_group` columns this import writes to. It runs
   automatically the next time your backend deploys/starts (Flyway picks up
   any new migration file on boot).
2. `pip install psycopg2-binary` (not bundled — this repo has no Python
   dependency manifest since this is the only Python file in it).
3. An organization ID to import into — use the demo org's ID (visible after
   calling `POST /api/v1/skillforge/demo/bootstrap`), or your own org's ID.

## Usage

```bash
cd question-bank

# Preview first — no database writes, just shows what would happen
python3 import_question_bank.py --organization-id <your-org-uuid> --dry-run

# Then actually import
export DATABASE_URL="jdbc:postgresql://..."   # same connection your backend uses
python3 import_question_bank.py --organization-id <your-org-uuid> --created-by <your-user-uuid>
```

Safe to re-run — it checks `(organization_id, code)` before inserting each
question, so running it twice never creates duplicates.

## What I verified vs. what I couldn't

I read every line of `import_question_bank.py` and confirmed: it uses
parameterized queries throughout (no SQL injection risk from the script's
own construction), wraps everything in a transaction with rollback on
error, and its assumed column names (`sf_subjects.active`, `sf_questions`
columns, the new V9 columns) all match the real schema. I scanned all 270
questions for suspicious content (script tags, prompt-injection patterns,
destructive SQL) — one question about defending against prompt injection
legitimately discusses "ignore previous instructions" as its subject
matter, which is exactly what you'd expect from an "AI Prompt Engineering"
exam question, not a real issue.

**I have not run this script.** This sandbox has no network access and no
live Postgres instance, so I could not execute an actual import and confirm
it completes successfully end-to-end. Run the `--dry-run` first, then a
real import against a non-production database before trusting it against
real data.
