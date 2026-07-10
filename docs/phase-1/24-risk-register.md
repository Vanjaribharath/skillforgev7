# Risk Register

| ID | Risk | Probability | Impact | Mitigation |
| --- | --- | --- | --- | --- |
| R-001 | V1 scope is too large | High | High | Phase gates, strict MVP acceptance, defer optional proctoring |
| R-002 | Question bank target of 1000+ per subject is unrealistic for initial build | High | High | Build import/generation workflow first, seed representative packs |
| R-003 | Anti-cheating false positives harm candidates | Medium | High | Advisory scoring, event evidence, manual review |
| R-004 | Tenant isolation bug exposes data | Low | Critical | Tenant tests, service guards, code review, security testing |
| R-005 | Bulk import complexity delays release | Medium | Medium | Start with CSV/Excel validation templates |
| R-006 | Email deliverability issues | Medium | Medium | SMTP test tools, delivery logs, retry queues |
| R-007 | Large reports are slow | Medium | Medium | Async export jobs and indexed analytics tables |
| R-008 | Candidate disconnects during assessment | Medium | High | Autosave, heartbeat, resume, grace period |
| R-009 | Integration webhooks fail silently | Medium | Medium | Retry, signatures, delivery logs, dead-letter status |
| R-010 | Compliance requirements vary by customer | Medium | High | Configurable retention, audit exports, deployment choice |

