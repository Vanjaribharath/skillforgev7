# Architecture Document

## Logical Architecture

```mermaid
flowchart LR
  Candidate["Candidate UI"] --> Gateway["Nginx / API Gateway"]
  Trainer["Trainer UI"] --> Gateway
  Admin["Admin UI"] --> Gateway
  Gateway --> Backend["Spring Boot API"]
  Backend --> Postgres["PostgreSQL"]
  Backend --> Redis["Redis"]
  Backend --> Rabbit["RabbitMQ"]
  Rabbit --> Workers["Async Workers"]
  Workers --> Postgres
  Workers --> SMTP["SMTP"]
  Backend --> Webhooks["External Webhooks"]
  Backend --> Metrics["Prometheus Metrics"]
```

## Bounded Contexts

- Identity and access.
- Organization management.
- Question bank governance.
- Assessment authoring.
- Candidate delivery.
- Scoring and evaluation.
- Reporting and analytics.
- Notification and communication.
- Audit and security.
- Integrations and operations.

## Deployment Architecture

```mermaid
flowchart TB
  Internet["Internet"] --> Nginx["Nginx SSL Reverse Proxy"]
  Nginx --> Frontend["React Static App"]
  Nginx --> Api["Spring Boot API"]
  Api --> Db["PostgreSQL Volume"]
  Api --> Cache["Redis"]
  Api --> Queue["RabbitMQ"]
  Api --> ObjectStore["Local/S3-compatible File Store"]
  Api --> Monitoring["Actuator, Prometheus, Grafana"]
```

## Data Flow: Candidate Attempt

1. Candidate opens secure magic link.
2. Backend validates token, expiry, assessment window, device policy, and attempt eligibility.
3. Candidate completes OTP verification when enabled.
4. Candidate answers are autosaved.
5. Anti-cheating events are recorded as attempt events.
6. Timeout or manual submit finalizes the attempt.
7. Scoring jobs run.
8. Reports, notifications, certificates, and webhooks are generated.

