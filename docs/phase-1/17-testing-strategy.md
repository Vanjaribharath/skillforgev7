# Testing Strategy

## Test Pyramid

| Layer | Coverage |
| --- | --- |
| Unit | Services, validators, scoring rules, token rules |
| Integration | Repositories, controllers, security, Redis, RabbitMQ |
| Contract | OpenAPI schema and webhook payloads |
| E2E | Admin setup, trainer assessment creation, candidate attempt, report export |
| Security | Auth bypass, tenant isolation, rate limits, token replay |
| Performance | Concurrent candidates, autosave load, report generation |
| Accessibility | Keyboard navigation, labels, contrast, candidate flow |

## Critical Test Scenarios

- Candidate cannot reuse an expired or consumed link.
- Candidate cannot access another candidate attempt.
- Trainer cannot access another organization's questions.
- Approved question edits create a new version.
- Published assessment keeps the question versions selected at publish time.
- Autosave survives refresh and reconnect.
- Timeout submit finalizes the attempt exactly once.
- Manual evaluation updates final score and reports.
- Bulk import validates required fields and rejects bad rows with line-level errors.
- Webhook retries and records failure state.

## Quality Gates

- Backend unit and integration tests pass.
- Frontend typecheck, lint, and component tests pass.
- E2E smoke suite passes in Docker Compose.
- OpenAPI document generated successfully.
- Container image builds.
- No critical dependency vulnerabilities accepted without explicit waiver.

