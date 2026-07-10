# FINAL_AUDIT_REPORT.md — ExecutionOS / SkillForge

**Audit date:** 2026-07-01
**Scope:** Full deployment + production-readiness audit across three iterative passes.
**Method:** Static analysis (regex/AST-level entity↔schema diffing, YAML/JSON/XML parsing, manual code review). See [Section 8](#8-what-was-not-verified--sandbox-limitations) for what this does *not* cover.

---

## 1. Headline findings (read this part first)

Two things matter more than everything else in this report:

1. **The staff/admin dashboard has no login page.** `useAuthStore.setSession()` exists but nothing in the frontend ever calls it — there is no `/login` route anywhere. Every protected SkillForge API call was silently going out with no Authorization header (see #2), and even after fixing that, there's no UI for a staff user to actually obtain a token. **The dashboard cannot be used by a real user until this is built.**
2. **12 of the ~18 ExecutionOS frontend routes are not wired to the backend at all.** `focus`, `journal`, `vault`, `admin`, `search`, `settings`, `analytics`, `schedule` (and their dynamic sub-routes) make **zero** API calls — they render static/mock data (`lib/mock-data.ts`). The backend has real, working, fully-migrated tables and endpoints for tasks/notes/schedules/journal-entries/etc.; the frontend simply never calls them. Only the SkillForge-branded routes (`/`, `/candidates`, `/assessments`, `/reports`, `/live`, `/candidate`) call real APIs.

Both are **pre-existing product/feature gaps, not deployment bugs**, and building them out is a real frontend engineering effort (new pages, forms, data fetching, loading/error states) — not something I built speculatively without TypeScript compilation available to verify it (see [Section 8](#8-what-was-not-verified--sandbox-limitations)). I fixed everything that was a genuine deployment, security, or infrastructure defect; I did **not** build new product features, per your explicit "do not redesign" instruction. Both gaps are listed again in Section 6 with a recommended next step.

Everything below this point — Flyway/Hibernate/Railway/Docker/CI/CD/security-headers/etc. — is now in good shape and independently re-verified.

---

## 2. Every issue found and fixed, by round

### Round 1 — Railway deployment failure (root cause)

| # | Issue | Root cause | Fix |
|---|---|---|---|
| 1 | `Schema-validation: missing table [activity_logs]` | Flyway migrations lived at repo-root `database/migrations/`, outside the Docker build context (`COPY src ./src` only). `application.yml` pointed at a `filesystem:../database/migrations` path that doesn't exist in the container. Flyway ran **zero** migrations; Hibernate then failed validating every table. | Copied both migrations into `backend/src/main/resources/db/migration/` (real classpath location); `spring.flyway.locations` now points only there. |
| 2 | Latent SQL syntax error waiting to happen | `sf_question_versions.references` used `references` — a reserved PostgreSQL keyword — as a column name. | Renamed to `reference_links` in the SQL and the JPA `@Column(name=...)` mapping. |
| 3 | `DATABASE_URL` format mismatch | Railway/Neon inject `postgres://user:pass@host/db`; Spring needs `jdbc:postgresql://...`. | Added `docker-entrypoint.sh` to convert the URL format and auto-append `sslmode=require` for Neon. |
| 4 | No resilience to slow DB cold-starts | No Flyway retry, no Hikari tuning. | Added `flyway.connect-retries: 10`, Hikari pool settings, `initialization-fail-timeout: -1`. |
| 5 | Railway had no real healthcheck config | No `railway.json`. | Added it, pointing at `/actuator/health` with a 120s timeout and `ON_FAILURE` restart policy. |

### Round 2 — Production-grade audit

| # | Issue | Root cause | Fix |
|---|---|---|---|
| 6 | **Guaranteed `BeanCreationException` on every boot** | `SecurityConfig` called `.oauth2Login(oauth -> {})` with **no** `ClientRegistrationRepository` configured anywhere in the app (no properties, no registration). Spring Security throws building the filter chain. | Removed the call. OAuth2 login can be re-added later with real `spring.security.oauth2.client.registration.*` config. |
| 7 | Unused Redis dependency risking startup failure | `spring-boot-starter-data-redis` was in `pom.xml` with **zero** Redis code anywhere (`grep` for `RedisTemplate`/`@Cacheable`/etc. returned nothing). Auto-configuration would try to connect to `localhost:6379`, which doesn't exist on Railway. | Removed the dependency. |
| 8 | Silent 500s instead of 401/403 | `GlobalExceptionHandler` had no handler for `AuthenticationException` or `AccessDeniedException`, and no catch-all — unhandled exceptions leaked default Spring error pages/stack traces. | Added explicit 401/403/500 handlers; catch-all no longer leaks exception details. |
| 9 | JWT filter silently swallowed all token errors | A malformed/expired token failed silently with no logging and inconsistent context state. | Now catches, clears `SecurityContextHolder`, logs at DEBUG, lets Spring Security produce a proper 401. |
| 10 | Swagger UI exposed in production | No profile-specific override. | `application-prod.yml` now sets `springdoc.swagger-ui.enabled: false` / `api-docs.enabled: false`. |
| 11 | Container ran as root, no image-level healthcheck | Original `Dockerfile` had neither. | Added non-root `app` user and `HEALTHCHECK` hitting `/actuator/health`. |
| 12 | `docker-compose.yml` had a broken Flyway filesystem override and an incorrect `DATABASE_URL` | Left over from the original repo layout. | Rewrote with the correct JDBC URL, dependency healthchecks, no Flyway override. |
| 13 | CI never actually tested against a database | `backend-ci.yml` ran `mvn test` with no Postgres — Flyway/Hibernate errors would never be caught before merge. | Added a real Postgres 16 service container and switched to `mvn verify`. |
| 14 | No `.env.example` | — | Created, documenting every variable the app reads. |

### Round 3 — Final enterprise audit (this pass)

| # | Issue | Root cause | Fix |
|---|---|---|---|
| 15 | **`deploy.yml` referenced a file that didn't exist** | The workflow ran `docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml build` — `docker-compose.prod.yml` existed but only overrode `restart:`/`SPRING_PROFILES_ACTIVE`, not credentials, meaning "production" mode would still boot the bundled Postgres with the hardcoded dev password `executionos`. | Rewrote `docker-compose.prod.yml` so every credential (`POSTGRES_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`) is **required from the environment** with no insecure fallback — the compose file now hard-fails at `docker compose config` time if they're unset, instead of silently deploying with dev defaults. Also stopped publishing the Postgres port to the host. |
| 16 | `deploy.yml` didn't actually deploy anywhere | It only ran `docker compose build` behind a manual trigger — no push to any host, no verification. | Replaced with a real Railway CLI deploy (`railway up`, blocking on real terminal status, not `--detach`) followed by a genuine post-deploy `/actuator/health` polling loop — this only passes if the live app is actually serving `{"status":"UP"}`. |
| 17 | **Frontend auth token key mismatch** | `use-auth-store.ts` writes the JWT to `localStorage["executionos.accessToken"]`; `api-client.ts`'s axios interceptor read `localStorage["skillforge.accessToken"]` — a key nothing ever wrote to. Every authenticated SkillForge API call (candidates, assessments, organizations, health-dashboard…) went out with **no Authorization header**, and would 401 against the real backend. | Fixed `api-client.ts` to read the same key the store writes. (See Section 1 — a login page is still needed for a token to exist in the first place.) |
| 18 | **Candidate exam flow would 401 mid-exam** | `POST /candidate/attempts/start/{id}` was `permitAll()`, but the very next calls a candidate makes — `PUT .../answers/{id}`, `POST .../events`, `POST .../submit` — fell under `anyRequest().authenticated()`. Candidates never receive a staff JWT, so every answer-save and the final submit would 401. | Extended `permitAll()` to the full `/api/v1/skillforge/candidate/attempts/**` namespace, completing the pattern the original code had already started for `start`. This is a **security-relevant decision** — attempt/question IDs are unguessable UUIDs gated by a prior invitation-token validation, but flagging it explicitly rather than burying it. |
| 19 | `/actuator/metrics` exposed to any authenticated user | Only `/actuator/prometheus` was `hasRole("ADMIN")`; `/actuator/metrics` fell through to `anyRequest().authenticated()`, meaning any logged-in user (not just admins) could read app metrics. | Locked `/actuator/metrics/**` to `ADMIN`, consistent with `/actuator/prometheus`. |
| 20 | No HTTP compression, no graceful shutdown | Neither configured. | Added `server.compression` (gzip for JSON/HTML/CSS/JS ≥1KB) and `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 20s` so in-flight requests finish before Railway kills the container on redeploy. |
| 21 | Frontend Docker image ships full `node_modules` | `next.config.ts` had no `output: "standalone"`; the Dockerfile copied the entire `node_modules` tree into the runtime image. | Enabled `output: "standalone"`; rewrote the frontend Dockerfile to the standard Next.js standalone pattern (smaller image, faster cold start on Render's free tier), added a non-root user and `HEALTHCHECK`, switched `npm install` → `npm ci` for deterministic installs. |
| 22 | No Render/Vercel/Netlify config | Not present. | Added `render.yaml` (Docker blueprint, healthcheck, env var placeholders), `frontend/vercel.json` (security headers), `netlify.toml` (monorepo `base = "frontend"`, official `@netlify/plugin-nextjs`). |
| 23 | No security-scanning or Docker-build CI | Not present. | Added `.github/workflows/security.yml` (CodeQL for Java + TypeScript, weekly scheduled scan, `npm audit`) and `.github/workflows/docker.yml` (validates both Dockerfiles build on every PR; pushes to GHCR only on version tags, using the automatic `GITHUB_TOKEN` — no extra secret needed). |

### Self-caught issues (introduced and fixed within this same pass, before shipping)

I'm listing these separately, deliberately, rather than quietly folding them into the table above — you asked me to verify every improvement against the codebase, and part of that is being honest when *my own* edit needed a second pass:

- While adding `spring.lifecycle.timeout-per-shutdown-phase`, my first edit created a **second top-level `spring:` key** in `application.yml`. YAML mappings can't have duplicate sibling keys — most parsers (including Spring Boot's) silently keep only the last one, which would have discarded the entire `datasource`/`jpa`/`flyway` block. Caught by re-parsing the file with PyYAML immediately after the edit; fixed by merging into the single existing `spring:` block.
- My first draft of `docker-compose.prod.yml` used unquoted `${VAR:?message with a colon in it}` values, which broke YAML parsing (a literal `:` inside an unquoted scalar is ambiguous with a new mapping key). Caught the same way — parse-checked before considering it done, quoted every interpolated value.

Both were caught by the same static verification pass described in Section 7, before being included in this ZIP.

---

## 3. Files created

```
railway.json
render.yaml
netlify.toml
frontend/vercel.json
backend/docker-entrypoint.sh
backend/src/main/resources/db/migration/V1__init.sql
backend/src/main/resources/db/migration/V2__skillforge_core.sql
.env.example
.github/workflows/security.yml
.github/workflows/docker.yml
DEPLOYMENT_FIX_GUIDE.md
DEPLOYMENT_GUIDE.md
FINAL_AUDIT_REPORT.md   (this file)
```

## 4. Files modified

```
backend/pom.xml                                                    — removed redis + oauth2-client deps
backend/Dockerfile                                                 — non-root user, HEALTHCHECK, entrypoint script
backend/src/main/resources/application.yml                         — flyway location, hikari, compression, graceful shutdown
backend/src/main/resources/application-prod.yml                    — swagger disabled
backend/src/main/resources/db/migration/V2__skillforge_core.sql    — reserved-keyword column rename
backend/src/main/java/com/executionos/config/SecurityConfig.java   — removed oauth2Login, candidate permitAll, actuator lockdown
backend/src/main/java/com/executionos/exception/GlobalExceptionHandler.java — 401/403/500 handlers
backend/src/main/java/com/executionos/security/JwtAuthenticationFilter.java — exception handling
backend/src/main/java/com/executionos/skillforge/model/SkillForgeEntities.java — reference_links column mapping
frontend/src/lib/api-client.ts                                     — localStorage key fix
frontend/next.config.ts                                            — standalone output
frontend/Dockerfile                                                — standalone build pattern, non-root, healthcheck
docker/docker-compose.yml                                          — correct JDBC URL, healthchecks
docker/docker-compose.prod.yml                                     — required secrets, no insecure defaults
.github/workflows/backend-ci.yml                                   — real Postgres service container
.github/workflows/deploy.yml                                       — real Railway deploy + health verification
.env.example                                                       — self-hosted compose vars documented
```

## 5. Files deliberately *not* created

You asked for zero placeholder files, so two items from the request were intentionally skipped rather than added as empty box-checking:

- **`Procfile`** — the app deploys via Dockerfile everywhere (Railway, Render, Docker Compose). A Procfile is a Heroku/buildpack convention that nothing in this stack reads; adding one would itself be a placeholder file.
- **`docker-compose.override.yml`** — Docker Compose auto-loads this file silently on top of `docker-compose.yml` with no `-f` flag needed, which is a common source of "why is my prod config different locally" confusion. `docker-compose.prod.yml` (explicit, requires `-f`) covers the same need without the silent-merge surprise.

---

## 6. Remaining limitations

Ordered by how much they affect whether someone can actually *use* the deployed app:

1. **No staff/admin login page** (Section 1). Recommended next step: a `/login` page calling `POST /api/v1/auth/login`, storing the result via the existing `useAuthStore.setSession()`. The store and API client are already correctly wired to each other now — this is the one missing piece.
2. **12 ExecutionOS frontend routes render mock data, not live data** (Section 1). Each needs real `@tanstack/react-query` calls to the already-working backend endpoints, replacing `lib/mock-data.ts` imports.
3. **Candidate exam UI has hardcoded answer options.** `candidate-player.tsx` has a code comment admitting the four multiple-choice options shown are hardcoded (`ps aux | grep java`, etc.) rather than pulled from the question's actual structured options — the question catalog API is called, but the component doesn't yet render its real option data.
4. **No automated end-to-end/integration test suite** beyond the single trivial `ExecutionOsApplicationTests` (asserts the application class loads). `backend-ci.yml` now runs against a real Postgres, which will catch schema/migration regressions, but there's no test coverage of business logic (auth flows, CRUD, scoring, etc.).
5. **No live deployment has been performed or observed by me** — see Section 8.

---

## 7. Verification performed

Everything in this ZIP was checked with three static test passes, re-run after every edit:

- **Round 1** (37 checks): pom.xml/YAML/entity↔migration parity for the original Flyway fix.
- **Round 2** (271 checks): build config, Flyway, Spring config, security, deployment files, GitHub Actions — expanded after the production-grade audit request.
- **Round 3** (47 checks): every file touched or added in this final pass.
- **Full regression re-run** (157 checks) immediately before packaging, re-confirming every fix from rounds 1–2 was still intact after round 3's edits — this is what caught that round 3 hadn't broken anything else.

**512 total static checks, 512 passed**, across: XML validity (`pom.xml`), YAML validity and semantic correctness (all `application*.yml`, `docker-compose*.yml`, all 5 GitHub Actions workflows), JSON validity (`vercel.json`), column-for-column diffing of all 37 database tables against their JPA entities, and targeted string/regex checks confirming specific bugs were actually fixed (not just "file was touched").

---

## 8. What was *not* verified — sandbox limitations

Being direct about this because claiming otherwise would be dishonest: **this environment has no network access and no Maven, npm packages, or Postgres installed.** Concretely, I could not:

- Run `mvn package` / `mvn verify` — no Maven binary is available, so the backend has never actually been compiled in this session.
- Run `npm install` / `npm run build` — Node and npm ARE installed, but there's no `node_modules` and no network to fetch one, so the frontend TypeScript has never been type-checked or compiled either.
- Start the Spring Boot application against a real PostgreSQL instance, or watch Flyway actually apply `V1`/`V2` against a live database.
- Build either Docker image, or run a container.
- Deploy to Railway, Render, Vercel, or Netlify, or observe a real `/actuator/health` response.
- Run the GitHub Actions workflows (`security.yml`, `docker.yml`, `deploy.yml`, `backend-ci.yml`, `frontend-ci.yml`) — their YAML is validated as syntactically correct and their logic reviewed, but none has ever actually executed.

What I *did* do to compensate: line-by-line entity↔schema diffing (every column, not just table names), YAML/JSON/XML parsing after every edit, and full regression re-runs — which is how the two self-introduced bugs in Section 2 were caught before you ever saw them. This is a meaningfully high bar of static confidence, but it is not the same as a green CI run or a real deployment. **Before your first real deploy, run `mvn clean verify` and `npm run build` locally, and watch the Railway deploy logs for the "Migrating schema... Successfully applied 2 migrations" line described in `DEPLOYMENT_GUIDE.md` Section 5.**

---

## 9. Scores

**These are my own structured judgment based on the static audit above — not output from a security scanner, load-testing tool, code-coverage tool, or a real deployment.** I'm giving numbers because you asked for them, but treat them as a organized opinion with visible reasoning, not a measured benchmark. Where I'm not confident, I've said so instead of rounding to a reassuring number.

| Category | Score | Why |
|---|---|---|
| **Deployment readiness** | 80/100 | Every known Railway-blocking defect (Flyway location, OAuth2 bean crash, Redis dependency, DB URL format, missing healthcheck config) is fixed and statically verified. Docked because *no* real build or deploy has happened yet in this session (Section 8) — I'm confident in the analysis, not in an unwitnessed outcome. |
| **Production readiness (whole product)** | 55/100 | The backend is genuinely solid. The score is dragged down hard by Section 1: most of the ExecutionOS frontend doesn't talk to the backend, and there's no way for a staff user to log in at all. A backend that's ready to deploy isn't the same as a product that's ready for real users — both are true here, and conflating them would be the misleading part. |
| **Security** | 75/100 | JWT + BCrypt + rate limiting + CSP/Permissions-Policy headers + CORS allowlist + actuator lockdown are all in place and now consistent. Docked for: no automated dependency-vulnerability gate before this pass (partially addressed by `security.yml`, but it's never run yet), and the candidate-attempt `permitAll()` widening in #18 is a real tradeoff worth your explicit sign-off, not just something to accept because a report said so. |
| **Performance** | 60/100 | Hikari pool tuned, pagination already used consistently across both controller sets (a "found good," not something I added), compression and graceful shutdown added this pass. No caching layer exists (Redis was removed for being unused dead weight, not replaced with anything) and there's been no load testing of any kind — this number is about configuration hygiene, not measured throughput. |
| **Documentation** | 85/100 | `DEPLOYMENT_GUIDE.md` covers all requested targets with copy-pasteable steps; this file exists. Docked slightly because neither has been validated by a person actually following the steps end-to-end. |

## 10. Deployment target checklist

| Target | Config prepared | Still needs real-world verification |
|---|---|---|
| **Localhost** | ✅ `.env.example`, documented `mvn spring-boot:run` steps | Run it once — no local Postgres/Maven/npm was available to test *in this sandbox* |
| **Docker** | ✅ Both Dockerfiles rewritten (non-root, healthcheck, standalone frontend build) | `docker build` has not been run — no Docker daemon in this sandbox |
| **Docker Compose** | ✅ Dev (`docker-compose.yml`) and prod overlay (`docker-compose.prod.yml`) with real healthchecks and no insecure defaults | `docker compose up` has not been run |
| **Railway** | ✅ `railway.json`, entrypoint URL conversion, healthcheck path | Actual deploy + watching logs for "Successfully applied 2 migrations" |
| **Render** | ✅ `render.yaml` blueprint | Never deployed to Render; blueprint schema reviewed but not exercised |
| **Neon PostgreSQL** | ✅ SSL handling, connection string format, pool sizing note for free-tier limits | Never connected to a real Neon instance |
| **Vercel** | ✅ `frontend/vercel.json`, env vars documented | Root Directory must be set to `frontend` in the Vercel dashboard (not settable from vercel.json itself) — never deployed |
| **Netlify** | ✅ `netlify.toml` with `base = "frontend"` and the official Next.js plugin | Never deployed |
| **GitHub Actions** | ✅ 5 workflows, all YAML-valid: `backend-ci`, `frontend-ci`, `security`, `docker`, `deploy` | None have ever executed — first real run will be the first real signal |

---

*If you want, the two headline gaps in Section 1 (login page, ExecutionOS API wiring) are well-scoped enough to tackle next — happy to start there.*
