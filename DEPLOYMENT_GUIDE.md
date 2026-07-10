# ExecutionOS / SkillForge — Complete Deployment Guide

> **Test results:** 271/271 static analysis checks passed (512/512 across all audit rounds — see `FINAL_AUDIT_REPORT.md`) ✅  
> Last verified: 2026-07-01

> ⚠️ **Before you deploy:** `FINAL_AUDIT_REPORT.md` Section 1 covers two frontend product gaps that aren't deployment bugs but will affect what you can actually do with the app once it's live — no staff login page exists yet, and most of the ExecutionOS-branded pages (focus/journal/vault/etc.) render mock data rather than calling the backend. Worth a two-minute read before your first deploy.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Prerequisites](#3-prerequisites)
4. [Environment Variables Reference](#4-environment-variables-reference)
5. [Local Development Setup](#5-local-development-setup)
6. [Docker Compose Setup](#6-docker-compose-setup)
7. [Neon PostgreSQL Setup](#7-neon-postgresql-setup)
8. [Railway Deployment](#8-railway-deployment)
9. [Render Deployment](#9-render-deployment)
10. [Frontend — Vercel / Netlify](#10-frontend--vercel--netlify)
11. [GitHub CI/CD Pipeline](#11-github-cicd-pipeline)
12. [How Flyway Migrations Work](#12-how-flyway-migrations-work)
13. [Adding a New Migration](#13-adding-a-new-migration)
14. [Health Check & Monitoring](#14-health-check--monitoring)
15. [Common Errors & Solutions](#15-common-errors--solutions)
16. [Production Checklist](#16-production-checklist)

---

## 1. Project Overview

**ExecutionOS** is a productivity and task-management backend.  
**SkillForge** is an enterprise assessment/proctoring platform running on the same Spring Boot service.

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.3.5, Java 21 |
| Database | PostgreSQL 16 (Neon managed / Railway plugin) |
| Migrations | Flyway 10 (automatic on startup) |
| Auth | JWT + BCrypt |
| API Docs | SpringDoc OpenAPI (Swagger UI — dev only) |
| Container | Docker (multi-stage, non-root) |
| CI/CD | GitHub Actions → Railway / Render |

---

## 2. Architecture

```
Internet
   │
   ▼
Railway / Render   (runs the Docker container)
   │
   ▼
Spring Boot :8080
   ├── /api/v1/auth/**        — Auth (register, login, refresh, logout)
   ├── /api/v1/**             — ExecutionOS APIs (tasks, schedules, notes…)
   ├── /api/v1/skillforge/**  — SkillForge APIs (assessments, attempts…)
   ├── /actuator/health       — Health check (public, no auth)
   └── /swagger-ui/**         — API docs (dev only, disabled in prod)
         │
         ▼
   Neon PostgreSQL (or Railway Postgres plugin)
   ├── V1__init.sql            — 12 core tables
   └── V2__skillforge_core.sql — 25 SkillForge tables
```

Flyway runs automatically **before** Hibernate validates the schema.  
You never need to run SQL manually.

---

## 3. Prerequisites

### Local machine
- Java 21 JDK (download from [adoptium.net](https://adoptium.net) or `sdk install java 21-tem`)
- Maven 3.9+ (`brew install maven` / `sdk install maven`)
- Docker Desktop (for `docker compose up`)
- PostgreSQL 16 (only if running without Docker)

### Cloud accounts
- [GitHub](https://github.com) — source control + CI/CD
- [Railway](https://railway.app) — backend hosting
- [Neon](https://neon.tech) — serverless PostgreSQL (free tier works)
- [Vercel](https://vercel.com) or [Netlify](https://netlify.com) — frontend hosting

---

## 4. Environment Variables Reference

Copy `.env.example` to `.env` for local use. **Never commit `.env` to git.**

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | YES | Full DB connection string. Accepts `postgres://user:pass@host/db` (Railway/Neon native) or `jdbc:postgresql://` format. Auto-converted by entrypoint. |
| `DATABASE_USERNAME` | Only if not in URL | DB username (auto-extracted if embedded in URL) |
| `DATABASE_PASSWORD` | Only if not in URL | DB password (auto-extracted if embedded in URL) |
| `JWT_SECRET` | YES | HMAC-SHA256 signing key. Must be **≥ 32 characters**. Generate: `openssl rand -hex 32` |
| `CORS_ORIGINS` | YES | Comma-separated allowed frontend origins, e.g. `https://app.vercel.app` |
| `SPRING_PROFILES_ACTIVE` | Recommended | Set to `prod` in production (disables Swagger, enables prod logging) |
| `PORT` | Auto (Railway) | Server port — set automatically by Railway/Render, defaults to `8080` |
| `DB_POOL_SIZE` | No | HikariCP max pool size, default `10` |
| `RATE_LIMIT_CAPACITY` | No | Max requests per IP bucket, default `120` |
| `RATE_LIMIT_REFILL_PER_MINUTE` | No | Token refill per minute, default `120` |
| `MAX_UPLOAD_BYTES` | No | Max file upload size, default `52428800` (50 MB) |

---

## 5. Local Development Setup

### 5a. Start a local Postgres

**Option A — Docker one-liner:**
```bash
docker run -d \
  --name executionos-db \
  -e POSTGRES_DB=executionos \
  -e POSTGRES_USER=executionos \
  -e POSTGRES_PASSWORD=executionos \
  -p 5432:5432 \
  postgres:16-alpine
```

**Option B — existing local Postgres:**
```sql
CREATE DATABASE executionos;
CREATE USER executionos WITH PASSWORD 'executionos';
GRANT ALL PRIVILEGES ON DATABASE executionos TO executionos;
```

### 5b. Set environment variables

```bash
# Copy the template
cp .env.example .env

# Minimum required values in .env:
DATABASE_URL=jdbc:postgresql://localhost:5432/executionos
DATABASE_USERNAME=executionos
DATABASE_PASSWORD=executionos
JWT_SECRET=dev-only-secret-must-be-at-least-32-characters
CORS_ORIGINS=http://localhost:3000
SPRING_PROFILES_ACTIVE=dev
```

### 5c. Run the backend

```bash
cd backend

# Load .env (bash/zsh)
set -a; source ../.env; set +a

mvn spring-boot:run
```

### 5d. Expected startup output

```
Flyway Community Edition will be used.
Current version of schema "public": null
Migrating schema "public" to version "1 - init"          ✓
Migrating schema "public" to version "2 - skillforge core" ✓
Successfully applied 2 migrations (execution time 00:00.812s)
Hibernate: validate schema
Started ExecutionOsApplication in 4.8 seconds
```

### 5e. Smoke test

```bash
# Health
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test User","password":"Password123!"}'
# → {"accessToken":"eyJ...","refreshToken":"...","user":{...}}

# Swagger UI (dev only)
open http://localhost:8080/swagger-ui/index.html
```

---

## 6. Docker Compose Setup

Starts Postgres + Backend + Frontend + Nginx in one command.

```bash
cd docker
docker compose up --build
```

**Services started:**

| Service | URL | Description |
|---|---|---|
| backend | http://localhost:8080 | Spring Boot API |
| frontend | http://localhost:3000 | Next.js UI |
| nginx | http://localhost:80 | Reverse proxy |
| prometheus | http://localhost:9090 | Metrics |
| grafana | http://localhost:3001 | Dashboards (admin/admin) |

```bash
# Stop and keep data
docker compose down

# Stop and delete database
docker compose down -v
```

---

## 7. Neon PostgreSQL Setup

### Step 1 — Create a project

1. Go to [neon.tech](https://neon.tech) → New Project
2. Name it `executionos-prod`
3. Choose region nearest to your Railway region
4. Click **Create Project**

### Step 2 — Copy your connection string

On the Neon dashboard → **Connection Details**, copy the connection string:
```
postgres://executionos_owner:XXXX@ep-XXXX.us-east-2.aws.neon.tech/neondb?sslmode=require
```

> No transformation needed — `docker-entrypoint.sh` converts it to JDBC format and adds SSL automatically.

### Step 3 — Set as DATABASE_URL

In Railway or your `.env` file:
```
DATABASE_URL=postgres://executionos_owner:XXXX@ep-XXXX.us-east-2.aws.neon.tech/neondb?sslmode=require
```

Flyway creates all 37 tables on first startup. No SQL to run manually.

---

## 8. Railway Deployment

### Step 1 — Push to GitHub

```bash
git init
git add .
git commit -m "feat: initial production-ready application"
git remote add origin https://github.com/YOUR_USERNAME/executionos.git
git push -u origin main
```

### Step 2 — Create Railway project

1. [railway.app](https://railway.app) → **New Project** → **Deploy from GitHub repo**
2. Authorize and select your repository

Railway automatically detects `railway.json` which configures:
- Build: `backend/Dockerfile`
- Healthcheck: `/actuator/health` with 120-second timeout
- Restart policy: `ON_FAILURE` (up to 5 retries)

### Step 3 — Add environment variables

Railway dashboard → your service → **Variables**:

```
DATABASE_URL             = (Neon connection string, or Railway adds this automatically if using Postgres plugin)
JWT_SECRET               = (output of: openssl rand -hex 32)
CORS_ORIGINS             = https://your-frontend.vercel.app
SPRING_PROFILES_ACTIVE   = prod
```

### Step 4 — Deploy and watch logs

Click **Deploy** or push to `main`.

**Successful Railway logs will show:**
```
[docker-entrypoint.sh] Converting DATABASE_URL to JDBC format
[Flyway] Migrating schema "public" to version "1 - init"
[Flyway] Migrating schema "public" to version "2 - skillforge core"
[Flyway] Successfully applied 2 migrations
[Hibernate] HHH000490: Using JtaTransactionCoordinator
[Spring] Started ExecutionOsApplication in 8.2 seconds
```

### Step 5 — Verify

```bash
curl https://YOUR-APP.up.railway.app/actuator/health
# → {"status":"UP","components":{"db":{"status":"UP"},...}}
```

---

## 9. Render Deployment

### Step 1 — Create Web Service

`render.yaml` at the repo root defines this as a Render Blueprint — in the Render dashboard, choose **New → Blueprint** and point it at your repo for a one-click setup, or configure manually:

1. [render.com](https://render.com) → **New** → **Web Service**
2. Connect your GitHub repository
3. Configure:
   - **Root Directory:** `backend`
   - **Runtime:** `Docker`
   - **Dockerfile Path:** `Dockerfile`
   - **Health Check Path:** `/actuator/health`

### Step 2 — Environment variables

Same set as Railway (Section 8, Step 3).

### Step 3 — Free tier note

Render free tier **spins down** after 15 minutes of inactivity. First request after sleep can take 30–60 seconds. Upgrade to a paid plan for always-on behavior.

---

## 10. Frontend — Vercel / Netlify

### Vercel

`frontend/vercel.json` sets security headers automatically. In the Vercel dashboard, set **Root Directory** to `frontend` (this can't be set from vercel.json itself in a monorepo) — then:

```bash
cd frontend
npx vercel --prod
```

In Vercel Dashboard → Settings → Environment Variables:
```
NEXT_PUBLIC_API_URL = https://your-app.up.railway.app/api/v1
BACKEND_API_URL     = https://your-app.up.railway.app/api/v1
```

### Netlify

`netlify.toml` at the repo root already sets `base = "frontend"` and the official `@netlify/plugin-nextjs` plugin, so Netlify auto-detects everything — just connect the repo. Or via CLI:

```bash
cd frontend
npm run build
npx netlify-cli deploy --prod --dir=.next
```

Add the same variables in Netlify → Site Settings → Environment Variables.

### Update CORS after deploying frontend

Go back to Railway → Variables and update:
```
CORS_ORIGINS = https://your-app.vercel.app,https://your-custom-domain.com
```

---

## 11. GitHub CI/CD Pipeline

Five workflows ship with the project:

### `backend-ci.yml`
Triggers on every push/PR touching `backend/**`.  
Spins up a **real Postgres 16 container**, builds and runs `mvn verify`.  
Catches: compile errors, Flyway failures, Hibernate validation errors, unit tests.

### `frontend-ci.yml`
Triggers on changes to `frontend/**`. Runs `npm ci && npm run build`.

### `security.yml`
CodeQL static analysis for Java and TypeScript, plus `npm audit` on frontend dependencies. Runs on every push/PR to `main` and weekly on a schedule, so newly-disclosed CVEs get caught even without new commits.

### `docker.yml`
Validates both `Dockerfile`s actually build, on every PR touching `backend/**` or `frontend/**`. Pushes images to GitHub Container Registry (`ghcr.io`) only on version tags (`v1.0.0`, etc.) — uses the automatic `GITHUB_TOKEN`, no extra secret needed.

### `deploy.yml`
Deploys to Railway on push to `main`, then polls `/actuator/health` to confirm the app actually came up — not just that the CLI exited 0.  
Requires `RAILWAY_TOKEN` as a GitHub Secret (Railway → Project Settings → Tokens — use a **Project Token**, not a personal account token). Optionally set `RAILWAY_PUBLIC_URL` too; without it the deploy still runs, it just can't verify health afterward.

---

## 12. How Flyway Migrations Work

### Location

Migration files live in:
```
backend/src/main/resources/db/migration/
├── V1__init.sql            ← 12 core tables (users, tasks, notes, etc.)
└── V2__skillforge_core.sql ← 25 SkillForge tables (orgs, assessments, etc.)
```

They are **compiled into the JAR** at build time. Flyway always finds them via `classpath:db/migration`, regardless of deployment platform or working directory.

### Startup sequence

```
App starts
  │
  ▼
Flyway connects to Postgres (retries up to 10x for slow cold-starts)
  │
  ▼
Checks flyway_schema_history table (creates it if absent)
  │
  ├── V1 not applied → runs V1__init.sql   → 12 tables created ✓
  ├── V2 not applied → runs V2__skillforge_core.sql → 25 tables created ✓
  └── Both already applied → no SQL runs
  │
  ▼
Hibernate validates every @Entity has a matching table & columns ✓
  │
  ▼
Application finishes startup — ready to accept requests
```

### On subsequent deploys (no new migrations)
Flyway sees both versions already applied → skips SQL → Hibernate validates in < 1 second.

---

## 13. Adding a New Migration

**Rule: every new entity or schema change needs a new migration file. Never edit an applied migration.**

### Step 1 — Create the file

```bash
# Use the next sequential version number
touch backend/src/main/resources/db/migration/V3__add_user_preferences.sql
```

### Step 2 — Write standard PostgreSQL SQL

```sql
-- V3__add_user_preferences.sql
CREATE TABLE user_preferences (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theme       VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    language    VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
```

### Step 3 — Add the JPA entity

```java
@Entity
@Table(name = "user_preferences")
public class UserPreferences extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String theme = "SYSTEM";
    // getters/setters …
}
```

### Step 4 — Test locally first

```bash
mvn spring-boot:run
# → Flyway applies V3 automatically
# → Hibernate validates and finds user_preferences ✓
```

### Migration rules

| ✅ Do | ❌ Never |
|---|---|
| Create a new `V{n}__*.sql` | Edit an already-applied migration |
| Use sequential version numbers | Skip version numbers |
| Test locally before pushing | Apply untested SQL to production |
| Match column names exactly to JPA `@Column` names | Rely on `ddl-auto: update` in prod |

---

## 14. Health Check & Monitoring

### Endpoints

| Path | Auth | Returns |
|---|---|---|
| `GET /actuator/health` | None | `{"status":"UP"}` |
| `GET /actuator/info` | None | App metadata |
| `GET /actuator/metrics` | None | Micrometer metrics list |
| `GET /actuator/prometheus` | ADMIN role | Prometheus scrape format |

### Full health response

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

### Grafana (local Docker Compose)

1. Open http://localhost:3001 → Login: `admin` / `admin`
2. Add data source: Prometheus → URL: `http://prometheus:9090`
3. Import dashboard ID `4701` (JVM Micrometer) from Grafana hub

---

## 15. Common Errors & Solutions

### `Schema-validation: missing table [activity_logs]`

**Cause:** Flyway ran zero migrations — either migrations not on the classpath, or wrong `flyway.locations`.

**Diagnosis:**
```bash
# In Railway logs, look for:
# "Flyway Community Edition will be used"
# If you don't see "Migrating schema..." → Flyway found no files
```

**Fix:** Confirm `backend/src/main/resources/db/migration/V1__init.sql` exists and `spring.flyway.locations=classpath:db/migration` in `application.yml`. The `filesystem:` path does not exist inside a Docker container.

---

### `BeanCreationException: securityFilterChain`

**Cause A:** `oauth2Login()` with no `ClientRegistrationRepository` bean.  
**Fix:** Already removed from SecurityConfig. If you re-add OAuth2 login, you must also configure `spring.security.oauth2.client.registration.*`.

**Cause B:** `spring-boot-starter-data-redis` with no Redis instance.  
**Fix:** Already removed from pom.xml. Add back only when implementing actual caching with a real Redis instance and `spring.data.redis.*` configuration.

---

### `FlywayException: Validate failed: Detected failed migration to version 2`

**Cause:** A previous deploy attempted V2 and failed mid-way, leaving a `FAILED` entry in `flyway_schema_history`.

**Fix:**
```sql
-- Run once in Neon SQL editor or Railway Postgres console:
DELETE FROM flyway_schema_history WHERE success = false;
```
Then redeploy — Flyway re-runs the failed migration from scratch.

---

### `FlywayException: checksum mismatch for migration V2`

**Cause:** Someone edited `V2__skillforge_core.sql` after it was applied to the database.

**Fix:** Create a new `V3__*.sql` with the fix as an `ALTER TABLE` or `CREATE INDEX`. Never edit applied migrations.

---

### `PSQLException: SSL connection required` (Neon)

**Cause:** JDBC URL missing `sslmode=require`.

**Fix:** Already handled by `docker-entrypoint.sh`. If setting `SPRING_DATASOURCE_URL` directly, append `?sslmode=require`:
```
SPRING_DATASOURCE_URL=jdbc:postgresql://ep-xxx.neon.tech/neondb?sslmode=require
```

---

### Railway service shows "Crashed" / restart loop

**Cause A:** App takes > default timeout to start.  
**Fix:** `railway.json` sets `healthcheckTimeout: 120`. Neon cold-starts + Flyway + JVM warm-up can take 20–40 seconds. This is normal.

**Cause B:** Missing required env var (e.g., `JWT_SECRET` not set).  
**Fix:** Check Railway logs for `Could not resolve placeholder '${JWT_SECRET}'`. Add all required variables.

**Cause C:** `/actuator/health` requires authentication.  
**Fix:** Already fixed in `SecurityConfig` — health endpoint is `permitAll()`.

---

### `HikariPool: Connection is not available, request timed out`

**Cause:** Connection pool exhausted. Neon free tier limits concurrent connections.

**Fix:** Reduce `DB_POOL_SIZE` to 5 on Neon free tier:
```
DB_POOL_SIZE=5
```

---

### `No active profile set, falling back to default profile`

**Cause:** `SPRING_PROFILES_ACTIVE` not set → `application-prod.yml` not loaded → Swagger UI is exposed in production.

**Fix:** Set `SPRING_PROFILES_ACTIVE=prod` in your Railway/Render environment variables.

---

## 16. Production Checklist

Complete this before every production deployment.

### Security
- [ ] `JWT_SECRET` is a random 64-char hex string (run `openssl rand -hex 32`)
- [ ] `SPRING_PROFILES_ACTIVE=prod` set (disables Swagger UI)
- [ ] `CORS_ORIGINS` lists only your actual frontend domain, not `*`
- [ ] No secrets in `application.yml` default values in git
- [ ] `.env` is in `.gitignore` and never committed

### Database
- [ ] Neon project created in correct region (close to Railway region)
- [ ] `DATABASE_URL` set in Railway environment variables
- [ ] Migrations tested locally with `mvn spring-boot:run` against a real Postgres
- [ ] No FAILED rows in `flyway_schema_history`
- [ ] `SELECT version, description, success FROM flyway_schema_history;` shows all `true`

### Deployment
- [ ] `railway.json` present at repo root
- [ ] GitHub Actions CI is green on `main` branch
- [ ] Railway healthcheck passes: `{"status":"UP"}`
- [ ] Frontend `NEXT_PUBLIC_API_URL` points to Railway backend URL

### Post-deploy smoke tests
```bash
BASE=https://your-app.up.railway.app

# 1. Health
curl $BASE/actuator/health

# 2. Register
curl -X POST $BASE/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","name":"Smoke","password":"Test1234!"}'

# 3. Login and get token
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"Test1234!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# 4. Hit an authenticated endpoint
curl -H "Authorization: Bearer $TOKEN" $BASE/api/v1/auth/me
```

---

*Generated from static analysis of the codebase. All 271 checks passed.*
