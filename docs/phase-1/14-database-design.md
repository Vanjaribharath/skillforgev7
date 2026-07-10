# Database Design

## Core Tables

| Table | Purpose |
| --- | --- |
| organizations | Tenant root, plan, status, branding |
| users | Login identity and shared profile |
| roles | Role catalog |
| user_roles | Role assignments scoped by organization |
| departments | Organization departments |
| batches | Candidate groups |
| trainer_profiles | Trainer-specific metadata |
| candidate_profiles | Candidate-specific metadata and history |
| questions | Current question identity and metadata |
| question_versions | Immutable version content |
| question_options | Options for objective questions |
| question_approvals | Draft/review/approved workflow |
| question_import_jobs | Bulk import tracking |
| assessment_templates | Reusable assessment setups |
| assessments | Published or draft assessments |
| assessment_sections | Custom sections and timers |
| assessment_question_rules | Pools, weights, difficulty distribution |
| assessment_invitations | Candidate access control and email state |
| attempts | Candidate attempt lifecycle |
| attempt_answers | Answers and autosave history |
| attempt_events | Anti-cheating, lifecycle, and device events |
| score_results | Scores, ranks, topic breakdowns |
| manual_evaluations | Evaluator workflow |
| certificates | Generated certificates and verification codes |
| email_templates | Custom organization email templates |
| notifications | In-app notifications |
| webhook_endpoints | External integration endpoints |
| webhook_deliveries | Delivery history and retry state |
| audit_logs | Append-only user and system action history |
| backup_jobs | Backup and restore job tracking |

## Data Rules

- Use UUID primary keys for externally referenced records.
- Add `created_at`, `updated_at`, `created_by`, and `updated_by` where applicable.
- Add optimistic locking to mutable authoring entities.
- Store question content by version to keep submitted attempts stable.
- Use JSONB for flexible per-question scoring configuration and report breakdowns where relational modeling would overfit.
- Use partial indexes for active invitations, open attempts, and approved questions.

## Seed Data

- Platform admin user.
- Demo organization.
- Demo departments and batches.
- Minimal subject taxonomy.
- Email template defaults.
- Assessment template examples.

