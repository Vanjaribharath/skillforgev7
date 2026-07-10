# SkillForge Enterprise Deployment

## Required Environment

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `CORS_ORIGINS`
- `NEXT_PUBLIC_API_URL`
- `BACKEND_API_URL`
- `SMTP_HOST`
- `SMTP_PORT`
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `PUBLIC_APP_URL`
- `WEBHOOK_SIGNING_SECRET`

## Production Compose

```bash
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up --build -d
```

Open:

- Frontend via Nginx: http://localhost
- Frontend direct: http://localhost:3000
- Backend OpenAPI: http://localhost:8080/swagger-ui/index.html
- Backend health: http://localhost:8080/actuator/health
- Grafana: http://localhost:3001

## Demo Bootstrap

```bash
curl -X POST http://localhost:8080/api/v1/skillforge/demo/bootstrap
curl http://localhost:8080/api/v1/skillforge/catalog/coverage
curl "http://localhost:8080/api/v1/skillforge/catalog/questions?subject=Java&difficulty=EASY&page=0&size=25"
```

The catalog endpoint exposes 1,200 generated question templates for each requested subject, split across easy, medium, and hard levels. Trainers can select a subject, filter questions, randomize a pool, publish an assessment, and send one-time invitations from the UI.

## Security Checklist

- Replace the development JWT secret with a long random value.
- Restrict CORS to production origins.
- Configure SMTP with a verified sender domain.
- Put Nginx behind TLS or terminate SSL at a cloud load balancer.
- Use managed PostgreSQL, Redis, and RabbitMQ in cloud production where available.
- Keep Prometheus and Grafana behind a private network or admin authentication.
- Rotate webhook and API signing secrets before go-live.

## Operational Checks

- Backend health: `/actuator/health`
- Backend metrics: `/actuator/prometheus`
- SkillForge catalog coverage: `/api/v1/skillforge/catalog/coverage`
- SkillForge demo bootstrap: `/api/v1/skillforge/demo/bootstrap`
- Frontend PWA manifest: `/manifest.json`

## Release Verification

```bash
cd backend
mvn -q test

cd ../frontend
npm run build
```
