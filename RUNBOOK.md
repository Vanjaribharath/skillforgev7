# SkillForge / ExecutionOS — Run & Deploy Runbook

This covers three things, in order:
1. Run everything locally (fastest path first, manual path second)
2. Run the test suites
3. Deploy online (Docker Compose on a VM, and split platform deploy)

Every command block is copy-paste runnable as-is from the repo root
(`skillforge-application/`) unless a `cd` is shown.

---

## 0. One-time prerequisites

| Tool | Version | Check |
|---|---|---|
| Docker + Docker Compose | 24+ | `docker --version` |
| Node.js | 22.x | `node --version` |
| Java | 21 | `java --version` |
| Maven | 3.9+ (or use the Docker build, no local install needed) | `mvn --version` |

If you only have Docker, skip straight to **Section 1 (fastest path)** — you
don't need Node/Java/Maven installed on your machine at all.

---

## 1. Fastest path — full stack with Docker Compose (recommended)

```bash
cd skillforge-application/docker
docker compose up --build
```

This starts, in order: Postgres → backend (Spring Boot, runs Flyway
migrations automatically) → frontend (Next.js) → nginx. Wait for
`start_period` health checks (~60s on first boot while Maven layers cache).

**Services once up:**
- App (via nginx): http://localhost
- Frontend directly: http://localhost:3000
- Backend directly: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html (login: `admin` / value of `SWAGGER_PASSWORD`, default `change-this-swagger-password`)
- Prometheus: http://localhost:9090, Grafana: http://localhost:3001 (if you kept the observability services in `docker-compose.yml`)

**Seed demo data + log in (do this once per fresh database):**

```bash
curl -X POST http://localhost:8080/api/v1/skillforge/demo/bootstrap
```

Then open http://localhost:3000/login (or http://localhost/login) and sign in with:
- Admin: `admin@apex.example` / `Demo@12345`
- Trainer: `trainer@apex.example` / `Demo@12345`
- Candidate: `candidate@apex.example` / `Demo@12345`

**Stop everything:**
```bash
docker compose down          # stop, keep data
docker compose down -v       # stop and wipe the Postgres volume (fresh start)
```

---

## 2. Manual path — run backend and frontend without Docker

Useful for active development (hot reload) or debugging.

### 2a. Start Postgres only

```bash
docker run --name skillforge-pg -e POSTGRES_DB=executionos \
  -e POSTGRES_USER=executionos -e POSTGRES_PASSWORD=executionos \
  -p 5432:5432 -d postgres:16-alpine
```

### 2b. Run the backend

```bash
cd skillforge-application/backend
export DATABASE_URL=jdbc:postgresql://localhost:5432/executionos
export DATABASE_USERNAME=executionos
export DATABASE_PASSWORD=executionos
export JWT_SECRET=dev-only-change-this-secret-to-at-least-32-characters
export CORS_ORIGINS=http://localhost:3000
export SWAGGER_PASSWORD=dev-swagger-password
mvn spring-boot:run
```

Backend is up when you see `Started ExecutionOsApplication` — check:
```bash
curl http://localhost:8080/actuator/health
```

### 2c. Run the frontend (separate terminal)

```bash
cd skillforge-application/frontend
npm install
export BACKEND_API_URL=http://localhost:8080/api/v1
npm run dev
```

Open http://localhost:3000/login. Bootstrap demo data + log in exactly as
in Section 1.

> **Why `BACKEND_API_URL` matters:** the frontend's `/api/[...path]` route
> proxies every `/api/...` call to `${BACKEND_API_URL}/...`. Leave it unset
> and it defaults to `http://localhost:8080/api/v1`, which is correct for
> this exact setup anyway — but set it explicitly if your backend runs
> somewhere else.

---

## 3. Running the tests

### 3a. Frontend tests (Vitest) — installed and passing as of this delivery

```bash
cd skillforge-application/frontend
npm install
npm test          # runs vitest, all tests should pass
npm run typecheck # tsc --noEmit, no errors expected
npm run build     # production build, should complete with no type errors
```

Current test coverage added in this pass:
- `src/lib/proxy-headers.test.ts` — proves the API proxy strips
  `Content-Encoding`/`Content-Length`/etc. from upstream responses and
  `Accept-Encoding`/`Host`/etc. from outgoing requests (regression test for
  the login bug that was fixed).
- `src/app/login/page.test.tsx` — proves the login page shows the real
  backend error message when one is available, falls back to a generic
  message only on a true network/decoding failure, and redirects correctly
  on success.

### 3b. Backend tests (JUnit via Maven)

```bash
cd skillforge-application/backend
mvn test
```

> This sandbox environment has no access to Maven Central, so `mvn test`
> could not actually be executed here — the fix to `SkillForgeController.java`
> (missing `import java.util.List;`, which is a hard compile error) was found
> and corrected by static review, not by a green build. **Run `mvn test`
> yourself before deploying** to get a real, verified compile + test pass;
> it should succeed cleanly with the fixes applied.

---

## 4. Deploying online

You have two supported topologies. Pick one.

### Option A — Single VM with Docker Compose (DigitalOcean, EC2, Hetzner, etc.)

1. Point a domain's A record at your VM's IP.
2. SSH in, install Docker + the compose plugin.
3. Copy the `skillforge-application/` folder to the VM (git clone, scp, or rsync).
4. Create `skillforge-application/docker/.env`:
   ```env
   POSTGRES_PASSWORD=use-a-long-random-value
   JWT_SECRET=openssl-rand-hex-32-output-here
   CORS_ORIGINS=https://yourdomain.com
   SWAGGER_USERNAME=admin
   SWAGGER_PASSWORD=another-long-random-value
   ```
   Generate strong values with:
   ```bash
   openssl rand -hex 32
   ```
5. Start it:
   ```bash
   cd skillforge-application/docker
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   ```
6. Put TLS in front of nginx (either terminate TLS on a reverse proxy like
   Caddy/Traefik in front of this stack, or swap the bundled `nginx` service
   for `certbot`-managed nginx — the bundled `default.conf` is HTTP-only by
   design so it's simple to drop behind whatever TLS terminator you already run).
7. Bootstrap demo data (optional, skip in a real production org):
   ```bash
   curl -X POST https://yourdomain.com/api/skillforge/demo/bootstrap
   ```
8. Visit `https://yourdomain.com/login`.

**Updating a running deployment:**
```bash
cd skillforge-application/docker
git pull   # or re-sync files
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

### Option B — Split platform deploy (Railway / Render + Neon + Vercel)

This skips nginx entirely — each piece talks directly to the next.

**1. Database:** create a free Postgres on [Neon](https://neon.tech). Copy the connection string.

**2. Backend (Railway or Render):**
- New service → deploy from `skillforge-application/backend` (it has its own `Dockerfile`, platforms build it directly).
- Environment variables:
  ```
  DATABASE_URL=<neon connection string>
  JWT_SECRET=<openssl rand -hex 32>
  CORS_ORIGINS=https://your-frontend-domain.vercel.app
  SWAGGER_PASSWORD=<something long>
  SPRING_PROFILES_ACTIVE=prod
  ```
- `docker-entrypoint.sh` already normalizes Neon's `postgres://` URL format
  and forces `sslmode=require`, so no extra config needed there.
- Deploy, then note the public backend URL, e.g. `https://skillforge-api.up.railway.app`.

**3. Frontend (Vercel, Railway, or Render):**
- New project → deploy from `skillforge-application/frontend`.
- Environment variables:
  ```
  NEXT_PUBLIC_API_URL=https://skillforge-api.up.railway.app/api/v1
  BACKEND_API_URL=https://skillforge-api.up.railway.app/api/v1
  ```
  Setting `NEXT_PUBLIC_API_URL` makes the **browser** call the backend
  directly (bypassing the Next.js proxy route entirely) — simplest and
  fastest. If you'd rather keep the backend origin hidden from the browser,
  leave `NEXT_PUBLIC_API_URL` unset and only set `BACKEND_API_URL`; every
  call then goes through `/api/[...path]` on the frontend server instead.
- Deploy.

**4. Bootstrap + verify:**
```bash
curl -X POST https://skillforge-api.up.railway.app/api/v1/skillforge/demo/bootstrap
```
Visit your Vercel/Railway frontend URL → `/login` → sign in with the demo
admin credentials above.

---

## 5. Environment variable reference

| Variable | Used by | Required in prod | Notes |
|---|---|---|---|
| `DATABASE_URL` | backend | yes | `jdbc:postgresql://...` or Neon/Railway `postgres://...` (auto-converted) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | backend | yes (if not embedded in `DATABASE_URL`) | |
| `JWT_SECRET` | backend | yes | ≥32 random chars, `openssl rand -hex 32` |
| `CORS_ORIGINS` | backend | yes | comma-separated, must match your real frontend origin(s) exactly |
| `SWAGGER_USERNAME` / `SWAGGER_PASSWORD` | backend | yes | gates `/swagger-ui/**` and `/v3/api-docs/**` |
| `RATE_LIMIT_CAPACITY` / `RATE_LIMIT_REFILL_PER_MINUTE` | backend | no | defaults 120/120 per IP+route per minute |
| `GOOGLE_CLIENT_ID` | backend + frontend (`NEXT_PUBLIC_GOOGLE_CLIENT_ID`) | no | omit to hide the Google sign-in button entirely |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | backend | no | omit and password-reset emails just get logged instead of sent |
| `NEXT_PUBLIC_API_URL` | frontend (browser) | no | set to call backend directly; leave unset to route through the frontend's own proxy |
| `BACKEND_API_URL` | frontend (server) | yes if using the proxy | where `/api/[...path]` forwards to, must include `/api/v1` |

---

## 6. Troubleshooting quick reference

- **"Invalid email or password" on login even with correct credentials, and
  browser console shows `net::ERR_CONTENT_DECODING_FAILED`** — this was the
  bug fixed in this pass (double-gzip header mismatch in the proxy route).
  If you ever see this again after further edits to
  `src/app/api/[...path]/route.ts`, check that upstream `Content-Encoding`/
  `Content-Length` headers are still being stripped in
  `src/lib/proxy-headers.ts`.
- **Every API call 404s in the full docker-compose+nginx deployment, but
  works fine with `npm run dev`** — check `docker/nginx/default.conf`'s
  `/api/` block still points at `http://backend:8080/api/v1/` (not `/api/`).
  This was also fixed in this pass.
- **Demo login says "Invalid email or password" and you're sure the
  password is right** — you haven't called `POST /api/v1/skillforge/demo/bootstrap`
  yet against this database. It's a no-op if already bootstrapped, so it's
  safe to call again.
- **CORS errors in the browser console** — `CORS_ORIGINS` on the backend
  must exactly match the origin (scheme + host + port) the browser is
  actually loading the frontend from.
