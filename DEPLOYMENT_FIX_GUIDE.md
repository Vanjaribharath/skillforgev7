# SkillForge / ExecutionOS — Railway Deployment Fix Guide

## Root cause of the deployment failure

Railway logs showed Hibernate failing with `Schema-validation: missing table [activity_logs]`.
The actual root causes (confirmed by inspecting the repo, not guessed):

1. **Flyway migrations were never packaged into the application.**
   `database/migrations/*.sql` lived at the **repo root**, outside `backend/`. The
   backend Dockerfile only does `COPY src ./src`, so those SQL files never made it
   into the Docker build context or the jar. `application.yml` tried to compensate
   with `flyway.locations: classpath:db/migration,filesystem:../database/migrations`,
   but `classpath:db/migration` didn't exist (no files there) and the
   `filesystem:../database/migrations` relative path doesn't exist inside the
   container at all. **Result: Flyway ran 0 migrations, the schema was never
   created, and Hibernate's `ddl-auto: validate` then failed on every table.**

2. **A reserved SQL keyword was used as a column name.** `sf_question_versions`
   had a column named `references`, which is a reserved word in PostgreSQL. Even
   after fixing the Flyway location, `V2__skillforge_core.sql` would have failed
   to apply with a syntax error at `references JSONB...`.

3. **No normalization for the `DATABASE_URL` format Railway/Neon provide.**
   Both platforms inject `postgres://user:pass@host:port/db` (or `postgresql://...`),
   but Spring's `spring.datasource.url` requires a JDBC URL
   (`jdbc:postgresql://host:port/db`). Without conversion this throws a
   `BeanCreationException` for the `DataSource` / `EntityManagerFactory` before
   Flyway ever runs.

## What was fixed

| Area | Fix |
|---|---|
| Flyway migrations | Copied `V1__init.sql` and `V2__skillforge_core.sql` into `backend/src/main/resources/db/migration/` (the real Spring Boot classpath default), so they're compiled into the jar. `backend/src/main/resources/db/migration` is now the **canonical** source; `database/migrations/` is kept only as a human-readable copy/reference. |
| `application.yml` | `spring.flyway.locations` now points only at `classpath:db/migration`. Added `baseline-on-migrate`, `validate-on-migrate`, `out-of-order: false`, and `connect-retries: 10` (so Flyway waits out a slow Neon/Railway cold-start instead of failing immediately). |
| `sf_question_versions.references` | Renamed to `reference_links` in both the migration SQL and the `SkillForgeEntities.java` JPA mapping (`@Column(name = "reference_links")`), avoiding the reserved-keyword syntax error. |
| `DATABASE_URL` handling | Added `backend/docker-entrypoint.sh`, which detects `postgres://`/`postgresql://` style URLs, converts them to `jdbc:postgresql://...`, extracts `DATABASE_USERNAME`/`DATABASE_PASSWORD` from the URL if not already set, and appends `sslmode=require` if missing (required by Neon). The Dockerfile now runs this script as its entrypoint instead of calling `java -jar` directly. |
| HikariCP | Added explicit pool settings (`connection-timeout`, `maximum-pool-size`, `minimum-idle`, `initialization-fail-timeout: -1`) so a slow/cold database doesn't cause a hard startup failure. |
| Railway config | Added `railway.json` pointing at `backend/Dockerfile`, with `healthcheckPath: /actuator/health` and an `ON_FAILURE` restart policy so Railway gives the app time to come up instead of marking it crashed on the first probe. |

`/actuator/health` was already permitted without authentication in `SecurityConfig`, so no change was needed there.

## Verification performed

- Diffed every `@Entity`/`@Table(name=...)` in `backend/src/main/java/com/executionos` against every `CREATE TABLE` in the migrations — all 28 entity tables have a matching migration-created table with matching columns/types (the extra `sf_*` tables without entities, e.g. lookup/audit tables, are intentional and harmless).
- Reviewed both migration files end-to-end for PostgreSQL syntax issues (the `references` keyword was the only problem found).
- Confirmed `pom.xml` (`java.version=21`) matches both Dockerfile stages (`temurin-21`).
- Confirmed `/actuator/health` and `/actuator/info` are `permitAll()` in `SecurityConfig`.
- Validated the edited `application.yml` parses as valid YAML.

**Note on environment limits:** this sandbox has no network access and no Maven/PostgreSQL installed, so I could not run `mvn package`, start the app, or run a live Flyway migration against a real Postgres instance to get a 100% black-box confirmation. Everything above was verified statically (entity↔schema diff, config audit, SQL review). Before deploying, run `cd backend && mvn -q test` and `mvn -q spring-boot:run` locally against a real Postgres once to confirm a clean boot — see below.

## 1. Localhost setup

```bash
# Start a local Postgres (or use the provided docker-compose)
docker compose -f docker/docker-compose.yml up -d postgres

cd backend
export DATABASE_URL=jdbc:postgresql://localhost:5432/executionos
export DATABASE_USERNAME=executionos
export DATABASE_PASSWORD=executionos
export JWT_SECRET=dev-only-change-this-secret-to-at-least-32-characters
mvn spring-boot:run
```

Flyway will create every table automatically on first run. Check `http://localhost:8080/actuator/health` → `{"status":"UP"}` and `http://localhost:8080/swagger-ui/index.html`.

## 2. Neon PostgreSQL setup

1. Create a Neon project and database.
2. Copy the connection string Neon gives you (it looks like
   `postgres://user:password@ep-xxxx.neon.tech/dbname?sslmode=require`).
3. Set it as `DATABASE_URL` in your environment (Railway, Docker, or locally) **exactly as Neon gives it to you** — the new `docker-entrypoint.sh` converts it to the JDBC form and ensures SSL automatically.

## 3. Railway deployment

1. Push this repo to GitHub.
2. In Railway, create a new project → "Deploy from GitHub repo".
3. Set the service's Root Directory to the repo root (so `railway.json` and `backend/Dockerfile` are found).
4. Add a PostgreSQL plugin, or paste your Neon `DATABASE_URL` as an environment variable.
5. Set the required environment variables (below).
6. Deploy. Railway will build `backend/Dockerfile`, run `docker-entrypoint.sh`, Flyway will run all migrations from the jar's classpath, Hibernate validation will pass, and `/actuator/health` will report `UP` for the Railway healthcheck.

## 4. Required environment variables

| Variable | Required | Example |
|---|---|---|
| `DATABASE_URL` | Yes | `postgres://user:pass@host:5432/db?sslmode=require` (Neon/Railway native format — auto-converted) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Only if not embedded in `DATABASE_URL` | |
| `JWT_SECRET` | Yes | random string ≥ 32 chars |
| `CORS_ORIGINS` | Yes | `https://your-frontend.vercel.app` |
| `SPRING_PROFILES_ACTIVE` | Recommended | `prod` |
| `PORT` | Set automatically by Railway | |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | If email features are used | |

## 5. GitHub deployment steps

Standard flow: push to `main`, connect the repo in Railway/Render, enable auto-deploy on push. The existing `.github/workflows/backend-ci.yml` already runs `mvn test`/build on PRs — keep that as your gate before merging to `main`.

## 6. How Flyway migrations work here

- Canonical migrations live in `backend/src/main/resources/db/migration/V*.sql` and are baked into the jar at build time — Flyway always finds them, regardless of deploy platform or working directory.
- `V1__init.sql` creates the core ExecutionOS tables (users, tasks, schedules, focus sessions, notes, attachments, categories, knowledge items, journal entries, activity logs, refresh tokens).
- `V2__skillforge_core.sql` creates the SkillForge assessment-platform tables (organizations, users, questions, assessments, attempts, scoring, certificates, notifications, webhooks, audit logs) plus seed subject data.
- To add a new migration, add `V3__description.sql` (next sequential version) to `backend/src/main/resources/db/migration/`. Never edit an already-applied migration file — Flyway checksums them.

## 7. Common deployment errors and solutions

| Error | Cause | Fix |
|---|---|---|
| `Schema-validation: missing table [x]` | Flyway didn't run / found 0 migrations | Confirm migrations are under `backend/src/main/resources/db/migration` and `spring.flyway.locations=classpath:db/migration` |
| `BeanCreationException` for `dataSource` | `DATABASE_URL` isn't a JDBC URL | Use `docker-entrypoint.sh` (already wired in) or set `SPRING_DATASOURCE_URL=jdbc:postgresql://...` directly |
| `FlywayException: checksum mismatch` | An already-applied migration file was edited | Never edit applied migrations — add a new `V{n}__*.sql` instead |
| `SSL connection required` (Neon) | Missing `sslmode=require` | Handled automatically by `docker-entrypoint.sh`, or add `?sslmode=require` to your URL manually |
| Railway healthcheck fails / restart loop | App takes longer than the default timeout to boot, or healthcheck path wrong | `railway.json` sets `healthcheckPath: /actuator/health` and a 120s timeout |
| `relation "..." does not exist` after deploy | A new entity was added without a matching migration | Add a new `V{n}__*.sql` migration creating that table before deploying the code that uses it |
