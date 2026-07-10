# ER Diagram

```mermaid
erDiagram
  ORGANIZATION ||--o{ USER : owns
  ORGANIZATION ||--o{ DEPARTMENT : has
  DEPARTMENT ||--o{ BATCH : contains
  BATCH ||--o{ CANDIDATE_PROFILE : enrolls
  USER ||--o| TRAINER_PROFILE : may_have
  USER ||--o| CANDIDATE_PROFILE : may_have
  ORGANIZATION ||--o{ QUESTION : owns
  QUESTION ||--o{ QUESTION_VERSION : versions
  QUESTION ||--o{ QUESTION_APPROVAL : approvals
  QUESTION ||--o{ QUESTION_OPTION : options
  QUESTION ||--o{ QUESTION_TAG : tags
  ORGANIZATION ||--o{ ASSESSMENT_TEMPLATE : defines
  ORGANIZATION ||--o{ ASSESSMENT : owns
  ASSESSMENT ||--o{ ASSESSMENT_SECTION : has
  ASSESSMENT_SECTION ||--o{ ASSESSMENT_QUESTION_RULE : selects
  ASSESSMENT ||--o{ ASSESSMENT_INVITATION : invites
  ASSESSMENT_INVITATION ||--o| ATTEMPT : creates
  ATTEMPT ||--o{ ATTEMPT_ANSWER : contains
  ATTEMPT ||--o{ ATTEMPT_EVENT : records
  ATTEMPT ||--o{ SCORE_RESULT : produces
  SCORE_RESULT ||--o| CERTIFICATE : grants
  ORGANIZATION ||--o{ EMAIL_TEMPLATE : customizes
  ORGANIZATION ||--o{ WEBHOOK_ENDPOINT : notifies
  ORGANIZATION ||--o{ AUDIT_LOG : records
```

## Notes

- `organization_id` is mandatory for tenant-scoped tables.
- Audit logs are append-only.
- Question versions preserve historical assessment integrity.
- Attempt answers must reference the exact question version presented to the candidate.

