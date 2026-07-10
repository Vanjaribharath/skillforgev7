# Deployment Guide

## Deployment Targets

- Local Docker Compose.
- AWS-ready deployment.
- Azure-ready deployment.
- Railway-ready deployment.
- Render-ready deployment.
- Private VM or on-premise Docker host.

## Services

- Nginx reverse proxy with SSL termination.
- Frontend React app.
- Spring Boot backend.
- PostgreSQL.
- Redis.
- RabbitMQ.
- Prometheus and Grafana optional monitoring.

## Environment Variables

| Variable | Purpose |
| --- | --- |
| `APP_ENV` | `dev`, `test`, `prod` |
| `DATABASE_URL` | PostgreSQL connection |
| `REDIS_URL` | Redis connection |
| `RABBITMQ_URL` | RabbitMQ connection |
| `JWT_SECRET` | JWT signing secret |
| `TOKEN_ENCRYPTION_KEY` | Sensitive token encryption |
| `SMTP_HOST` | Email server |
| `SMTP_PORT` | Email port |
| `SMTP_USERNAME` | Email username |
| `SMTP_PASSWORD` | Email password |
| `PUBLIC_APP_URL` | Candidate and email link base URL |
| `WEBHOOK_SIGNING_SECRET` | Webhook HMAC signing |

## Production Checklist

- Configure SSL.
- Rotate secrets from defaults.
- Enable database backups.
- Configure SMTP and test delivery.
- Configure object storage for exports and certificates.
- Enable monitoring and log retention.
- Run migrations.
- Create platform admin.
- Verify candidate link, autosave, submit, report, and certificate flow.

