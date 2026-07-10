# Project Charter

## Project Name

SkillForge Enterprise

## Purpose

Build a production-ready internal assessment platform for organizations that need secure, branded, reusable, and analytically rich assessments.

## In Scope

- Authentication and RBAC.
- Multi-organization support.
- Admin, trainer, and candidate roles.
- Question bank with versioning and approval.
- Assessment builder, scheduling, invitations, and secure attempts.
- Autosave, timeout submit, resume support, and refresh protection.
- Scoring, reports, exports, analytics, certificates.
- Notifications, email templates, REST APIs, webhooks.
- Security, audit, anti-cheating, health dashboard.
- Docker-based deployment and CI/CD.

## Out Of Scope For V1

- Fully automated webcam proctoring decisions.
- Native mobile apps.
- Marketplace question purchases.
- Payments and billing.
- SCORM/xAPI authoring beyond integration hooks.

## Constraints

- Backend: Java 21 and Spring Boot 3.
- Frontend: React, Vite, TypeScript, TailwindCSS, Material UI, charts.
- Database: PostgreSQL with Flyway.
- Redis and RabbitMQ for state and asynchronous workflows.
- Must be deployable via Docker Compose and cloud-ready.

## Approval Gate

Phase 2 begins only after this documentation pack is reviewed and accepted.

