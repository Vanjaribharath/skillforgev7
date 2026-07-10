# ExecutionOS

ExecutionOS is a minimal, mobile-friendly execution and scheduling system for developers, learners, and creators. It combines a daily scheduler, deep work mode, knowledge vault, journal, analytics, search, notifications, and admin visibility.

## Stack

- Backend: Spring Boot 3, Java 21, Spring Security, JWT, OAuth2 hooks, JPA, Flyway, PostgreSQL, WebSocket, Actuator.
- Frontend: Next.js 15, TypeScript, Tailwind CSS, React Query, Zustand, PWA manifest, lucide icons.
- Infrastructure: Docker Compose, Nginx, PostgreSQL, Redis, Prometheus, Grafana, Loki, GitHub Actions.

## Run Locally

```bash
cd docker
docker compose up --build
```

Then open:

- Frontend: http://localhost:3000
- Nginx: http://localhost
- Backend health: http://localhost:8080/actuator/health
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001

## API Shape

All backend routes are under `/api/v1`. The scaffold includes auth, tasks, schedules, focus sessions, notes, attachments, knowledge vault, journal, dashboard, analytics, search, admin, and `/ws/notifications`.

## Development Notes

The repository is intentionally schema-first. Flyway owns the core database layout in `database/migrations/V1__init.sql`; JPA validates against it at boot. Frontend pages currently ship with local mock data where backend aggregation endpoints are still placeholders, which keeps the UI usable while real service logic is expanded.
