-- V9: Question Bank Upgrade (renumbered during merge -- this codebase
-- already has its own real V7 and V8; see MERGE_AND_FIX_REPORT.md for the
-- full reconciliation of the two source packages).
-- Adds topic tagging, deterministic sort order, and shuffle-pool grouping
-- so the frontend can render questions "sorted beginner -> advanced" while
-- still shuffling within a difficulty pool per candidate attempt.

ALTER TABLE sf_questions
    ADD COLUMN IF NOT EXISTS topic TEXT,
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shuffle_group TEXT NOT NULL DEFAULT 'STANDARD';
    -- shuffle_group values: 'BEGINNER_POOL', 'ADVANCED_POOL', 'STANDARD'

CREATE INDEX IF NOT EXISTS idx_sf_questions_subject_difficulty
    ON sf_questions (subject_id, difficulty, sort_order);

CREATE INDEX IF NOT EXISTS idx_sf_questions_topic
    ON sf_questions (topic);

-- Composite index supporting the real (non-fabricated) catalog coverage
-- counts and the subject+status paginated question listing used by both
-- the public catalog and the question bank importer's idempotent re-run check.
CREATE INDEX IF NOT EXISTS idx_sf_questions_org_code
    ON sf_questions (organization_id, code);

-- Source-of-truth import ledger so bulk-generated batches can be
-- re-run safely without creating duplicate questions.
CREATE TABLE IF NOT EXISTS sf_question_import_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES sf_organizations(id) ON DELETE CASCADE,
    subject_slug TEXT NOT NULL,
    source_file TEXT NOT NULL,
    question_count INTEGER NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    checksum TEXT NOT NULL,
    UNIQUE (organization_id, subject_slug, checksum)
);
