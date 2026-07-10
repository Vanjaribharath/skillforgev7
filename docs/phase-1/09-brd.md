# Business Requirements Document

## Business Objectives

- Enable organizations to run internal assessments without depending on hiring-first tools.
- Provide complete operational control over users, questions, assessments, invitations, scoring, reporting, and certificates.
- Support private deployment for organizations with data residency needs.
- Improve trainer productivity and assessment repeatability.

## Business Requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| BR-001 | Organizations can manage their own departments, batches, trainers, and candidates | Must |
| BR-002 | Trainers can reuse templates and clone assessments | Must |
| BR-003 | Questions require approval before production use | Must |
| BR-004 | Candidates can attempt only once unless reassigned by authorized user | Must |
| BR-005 | Reports can be exported for compliance and offline review | Must |
| BR-006 | Organization branding appears in candidate portal, emails, and certificates | Must |
| BR-007 | External systems can receive assessment completion events | Should |
| BR-008 | Admins can monitor email delivery, jobs, and platform health | Should |
| BR-009 | Training leaders can view trends by topic, batch, and subject | Should |

## Acceptance Criteria

- A trainer can build and publish a Java assessment using approved questions in less than 15 minutes after initial setup.
- An admin can invite 500 candidates by CSV without manual email sending.
- A candidate can complete an assessment and receive confirmation without admin intervention.
- A manager can export a batch report and certificate list.
- Every sensitive action appears in audit logs.

