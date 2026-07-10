# Software Requirements Specification

## System Overview

SkillForge Enterprise is a multi-tenant web application with a Java Spring Boot backend, React/Vite frontend, PostgreSQL database, Redis cache/state layer, RabbitMQ job queue, SMTP email integration, and Docker-based deployment.

## Actors

- Platform Admin
- Organization Admin
- Trainer
- Evaluator
- Candidate
- External System

## Functional Requirements

| ID | Requirement |
| --- | --- |
| FR-001 | Register and configure organizations |
| FR-002 | Authenticate users with JWT and refresh tokens |
| FR-003 | Assign role-based permissions |
| FR-004 | Manage departments and batches |
| FR-005 | Manage trainers and candidates |
| FR-006 | Create, import, version, approve, retire, and search questions |
| FR-007 | Build assessments using sections, pools, weights, and randomization |
| FR-008 | Schedule assessments and send invitations |
| FR-009 | Validate secure candidate links and OTP |
| FR-010 | Autosave answers and submit attempts |
| FR-011 | Track suspicious activity events |
| FR-012 | Auto-score objective questions |
| FR-013 | Queue manual evaluations |
| FR-014 | Generate reports and exports |
| FR-015 | Generate certificates |
| FR-016 | Send emails and in-app notifications |
| FR-017 | Expose REST APIs and webhooks |
| FR-018 | Record audit logs |
| FR-019 | Monitor system health and background jobs |
| FR-020 | Backup and restore data |

## Non-Functional Requirements

| ID | Requirement |
| --- | --- |
| NFR-001 | Enforce tenant isolation at query and service layers |
| NFR-002 | Store passwords using adaptive hashing |
| NFR-003 | Encrypt sensitive tokens at rest |
| NFR-004 | Keep assessment autosave durable |
| NFR-005 | Support 1,000 concurrent candidates in target production sizing |
| NFR-006 | Provide audit-ready exports |
| NFR-007 | Maintain accessible keyboard navigation |
| NFR-008 | Support localization-ready UI strings |

