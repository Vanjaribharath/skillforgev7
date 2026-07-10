-- ============================================================
-- Fix ALL missing created_at/updated_at columns across every
-- SkillForge table that extends SkillForgeEntity.
--
-- V2__skillforge_core.sql created some tables without one or
-- both audit columns even though every @Entity in
-- SkillForgeEntities.java extends the SkillForgeEntity
-- @MappedSuperclass, which declares both createdAt and
-- updatedAt. Hibernate validates ALL entities on every boot,
-- so fixing one table at a time (as Railway's diagnosis
-- suggested) only ever reveals the NEXT missing table on the
-- next deploy. This migration fixes every affected table in
-- one pass, found via a zero-exclusion diff of every entity
-- field against every migration column (not just the tables
-- that happened to fail first).
--
-- Every statement uses IF NOT EXISTS, so this is safe to run
-- even if some of these columns were already added by earlier,
-- more narrowly-scoped migrations (V3-V6) on your live database
-- — it will simply no-op for anything that already exists.
-- ============================================================

-- sf_question_versions — missing updated_at
ALTER TABLE sf_question_versions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- sf_question_approvals — missing both
ALTER TABLE sf_question_approvals
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sf_question_approvals
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- sf_assessment_questions — missing updated_at
ALTER TABLE sf_assessment_questions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- sf_attempt_events — missing both
ALTER TABLE sf_attempt_events
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sf_attempt_events
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- sf_score_results — missing both
ALTER TABLE sf_score_results
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sf_score_results
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- sf_certificates — missing both
ALTER TABLE sf_certificates
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sf_certificates
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- sf_notifications — missing updated_at
ALTER TABLE sf_notifications
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- sf_audit_logs — missing updated_at
ALTER TABLE sf_audit_logs
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
