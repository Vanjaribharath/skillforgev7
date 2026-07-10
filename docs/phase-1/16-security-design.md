# Security Design

## Security Principles

- Tenant isolation is mandatory at every layer.
- Tokens are short-lived, scoped, encrypted or hashed where applicable.
- Assessment integrity events support human review and auditability.
- Candidate security must be clear and proportional.
- Default configuration should be secure for production.

## Controls

| Area | Control |
| --- | --- |
| Authentication | JWT access tokens, refresh token rotation, adaptive password hashing |
| Authorization | Role and permission checks at service and controller layers |
| Tenant Isolation | Mandatory organization scope on all tenant data |
| Rate Limiting | Redis-backed limits for login, OTP, candidate links, imports, and webhooks |
| Token Security | Hashed invitation tokens, expiry, one-time use, device binding options |
| Data Protection | TLS, database encryption options, secrets via environment variables |
| Question Protection | Restrict approved question access, encrypt sensitive content at rest where required |
| Audit | Append-only logs for admin, trainer, candidate, security, and system actions |
| Anti-Cheating | Fullscreen, tab switching, copy/paste, right click, devtools, monitor events |
| Webhooks | HMAC signatures, retries, dead-letter state |
| Backups | Scheduled backups, restore validation, encrypted storage |

## Suspicious Activity Score

The score is an advisory signal. It must not automatically fail candidates in V1. Trainers see event evidence and decide whether to review, invalidate, or accept an attempt.

## Standards Alignment

- OWASP ASVS for application security controls.
- OWASP API Security Top 10 for API abuse prevention.
- NIST digital identity guidance for authentication and token posture.
- WCAG 2.2 AA for accessible secure flows.

