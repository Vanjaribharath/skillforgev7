# API Documentation

Base path: `/api/v1`

## Authentication

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register-organization` | Register organization and first admin |
| POST | `/auth/login` | Login |
| POST | `/auth/refresh` | Refresh JWT |
| POST | `/auth/logout` | Revoke refresh token |
| POST | `/auth/password-reset/request` | Send reset email |
| POST | `/auth/password-reset/confirm` | Reset password |

## Organization

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/organizations/current` | Current tenant profile |
| PATCH | `/organizations/current` | Update organization |
| PUT | `/organizations/current/branding` | Update logo, colors, certificate branding |
| GET | `/departments` | List departments |
| POST | `/departments` | Create department |
| GET | `/batches` | List batches |
| POST | `/batches` | Create batch |

## Users

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/trainers` | List trainers |
| POST | `/trainers` | Create trainer |
| GET | `/candidates` | List candidates |
| POST | `/candidates` | Create candidate |
| POST | `/candidates/import` | Bulk import candidates |

## Question Bank

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/questions` | Search questions |
| POST | `/questions` | Create draft question |
| GET | `/questions/{id}` | Get question |
| PUT | `/questions/{id}` | Create new version |
| POST | `/questions/{id}/submit-review` | Send for approval |
| POST | `/questions/{id}/approve` | Approve question |
| POST | `/questions/{id}/restore/{versionId}` | Restore version |
| POST | `/questions/import` | Import questions |
| GET | `/questions/export` | Export questions |

## Assessments

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/assessments` | List assessments |
| POST | `/assessments` | Create assessment |
| POST | `/assessments/{id}/clone` | Clone assessment |
| POST | `/assessments/{id}/publish` | Publish assessment |
| POST | `/assessments/{id}/schedule` | Schedule assessment |
| POST | `/assessments/{id}/invite` | Invite candidates |
| GET | `/assessments/{id}/live` | Live dashboard state |

## Candidate Attempt

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/candidate/link/validate` | Validate magic link |
| POST | `/candidate/otp/verify` | Verify OTP |
| GET | `/candidate/attempts/{id}` | Load attempt |
| PUT | `/candidate/attempts/{id}/answers/{questionId}` | Autosave answer |
| POST | `/candidate/attempts/{id}/events` | Record event |
| POST | `/candidate/attempts/{id}/submit` | Submit attempt |

## Reports And Integrations

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/reports/candidates/{id}` | Candidate report |
| GET | `/reports/batches/{id}` | Batch report |
| POST | `/exports` | Create export job |
| GET | `/exports/{id}` | Download export |
| GET | `/certificates/{code}` | Verify certificate |
| GET | `/webhooks` | List webhooks |
| POST | `/webhooks` | Create webhook |
| GET | `/audit-logs` | Search audit logs |
| GET | `/health-dashboard` | Operational health |

