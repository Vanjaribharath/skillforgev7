# Technical Requirements Document

## Stack

- Java 21
- Spring Boot 3
- Spring Security
- JWT and refresh tokens
- OAuth2 client support
- PostgreSQL
- Redis
- RabbitMQ
- Spring Data JPA
- Flyway
- Docker and Docker Compose
- Swagger/OpenAPI
- JUnit, Mockito, Spring Security Test
- React, Vite, TypeScript, TailwindCSS, Material UI, charting library
- Nginx and SSL-ready reverse proxy

## Backend Module Structure

| Module | Responsibility |
| --- | --- |
| identity | Auth, users, roles, refresh tokens |
| organization | Tenants, departments, batches, branding |
| questionbank | Questions, versions, approval, imports |
| assessment | Templates, assessments, sections, scheduling |
| delivery | Invitations, tokens, attempts, autosave |
| scoring | Scoring, manual evaluation, ranking |
| reporting | Analytics, exports, certificates |
| notification | Email, in-app notifications, templates |
| integration | REST API keys, webhooks |
| audit | Audit events and compliance views |
| operations | Health dashboard, jobs, backups |

## Frontend Areas

- Admin workspace.
- Trainer workspace.
- Candidate assessment player.
- Reports and analytics.
- Settings and integrations.
- Health dashboard.

## Key Technical Decisions

- PostgreSQL is the source of truth.
- Redis stores short-lived tokens, rate-limit buckets, heartbeat state, and autosave cache before durable writes.
- RabbitMQ handles slow or retryable jobs.
- Flyway owns schema changes.
- OpenAPI is generated from backend annotations and checked into release artifacts.
- Candidate attempt state is persisted frequently to survive refresh and disconnect events.

