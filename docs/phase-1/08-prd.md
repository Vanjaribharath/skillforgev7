# Product Requirements Document

## Product Goals

- Let organizations create, deliver, score, report, and certify internal assessments.
- Reduce manual trainer and admin effort.
- Protect assessment integrity without making the candidate flow hostile.
- Support enterprise governance, branding, audit, and integrations.

## Personas

- Admin: configures organization, roles, departments, batches, branding, SMTP.
- Trainer: manages questions, creates tests, schedules attempts, monitors live progress.
- Candidate: receives invitation, verifies identity, takes assessment, receives result or certificate.
- Organization Owner: reviews analytics, audit, integrations, and growth.

## V1 Functional Requirements

### Authentication And Organization

- Organization registration.
- Login with email/password and optional OAuth2.
- JWT access token and refresh token.
- Role-based access for platform admin, org admin, trainer, evaluator, candidate.
- Department and batch management.
- Trainer and candidate management.

### Question Bank

- Subjects: Linux, Unix, Shell, Bash, Splunk, Java, Spring Boot, Spring MVC, Spring Security, Hibernate, JPA, SQL, Oracle, PostgreSQL, Kafka, IBM MQ, REST API, Microservices, Docker, Kubernetes, Git, GitHub, Jenkins, Maven, Gradle, AWS, Azure, GCP, New Relic, Grafana, Prometheus, ServiceNow, ITIL, Apigee, IBM FileNet, PCF, RabbitMQ, Redis, JSON, XML, XPath, Swagger, Postman, REST Assured, JUnit, Mockito, DSA, OOP, Collections, Exception Handling, Streams, Multithreading, Design Patterns.
- Question types: multiple choice, multiple select, fill blanks, code output, code completion, coding, scenario, true/false, drag/drop, ordering.
- Difficulty: easy, medium, hard.
- Metadata: tags, expected time, explanation, correct answer, references.
- Question versioning and restore.
- Draft, review, approved, retired workflow.
- Import and export from Excel, CSV, JSON, and Markdown.

### Assessment Builder

- Create, clone, reuse, preview, publish, and schedule assessments.
- Templates such as Java Fresher and Linux Admin.
- Sections with independent instructions, timers, marks, and pass rules.
- Question pools, randomization, difficulty distribution, weights, marks, negative marking.
- Default duration 60 minutes and custom duration.
- Start time, end time, candidate limit, grace period.

### Candidate Delivery

- Email invitation with secure magic link.
- OTP verification.
- One-time access token with automatic expiry.
- System compatibility check.
- Instructions acknowledgement.
- Timer, navigator, flag for review, review page.
- Fullscreen and dark mode.
- Autosave and auto-submit on timeout.
- Resume after disconnect.
- Completion confirmation and certificate download when enabled.

### Scoring And Reports

- Automatic scoring for objective questions.
- Manual evaluation queue for coding and subjective items.
- Partial scoring.
- Ranking and leaderboard.
- Skill, topic, weak area, difficulty, average score, pass rate, and time analysis.
- Candidate, batch, organization, department, trainer, subject, question performance reports.
- CSV, Excel, and PDF exports.

### Notifications

- SMTP configuration.
- Custom templates for invitation, reminder, result, certificate, password reset.
- In-app notifications.
- Submission alerts and assessment lifecycle reminders.

### Security And Anti-Cheating

- Rate limiting, audit logs, encrypted tokens, question encryption.
- IP, session, and device tracking.
- Fullscreen, tab switching, copy/paste, right-click, devtools, and multiple-monitor detection events.
- Optional camera and microphone support.
- AI suspicious activity score as an assistive signal, not automatic punishment.

## Non-Functional Requirements

- Multi-tenant data isolation.
- Accessibility aligned to WCAG 2.2 AA.
- Responsive admin, trainer, and candidate UI.
- API p95 latency target under 500 ms for common dashboard calls.
- Autosave target under 2 seconds on stable network.
- Export jobs asynchronous for large reports.

