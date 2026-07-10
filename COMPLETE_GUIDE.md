
# SkillForge — Complete Guide: Login, Roles, Users, Passwords, Errors, Monitoring

One file, everything in it. Read top to bottom the first time; use it as a
reference after that.

---

## 1. How to log in (fastest way)

1. Start the app locally (`cd docker && docker compose up --build`) or open
   your deployed URL.
2. Run the demo bootstrap **once** per fresh database:
   ```bash
   curl -X POST http://localhost:8080/api/v1/skillforge/demo/bootstrap
   ```
3. Go to `/login`. Click one of the three buttons — **Log in as Admin**,
   **Log in as Trainer**, **Log in as Candidate**. One click, you're in.

No typing required. If you want to type it manually instead:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@apex.example` | `Demo@12345` |
| Trainer | `trainer@apex.example` | `Demo@12345` |
| Candidate | `candidate@apex.example` | `Demo@12345` |

---

## 2. What each role can actually see and do

This is enforced **on the backend now**, not just hidden in the UI — a
candidate's login token is rejected with a 403 if it's used to call any
staff endpoint directly, not just hidden from the sidebar.

| Role | Sidebar shows | Can do |
|---|---|---|
| **Admin** (`ORG_ADMIN`/`PLATFORM_ADMIN`) | Everything | Manage org settings, invite/create trainers & candidates, author questions, publish assessments, view reports, view audit log, everything a Trainer can do |
| **Trainer** (`TRAINER`/`EVALUATOR`) | Command, Questions, Assessments, Candidates, Live, Reports, Test Player, Search | Everything except Settings and Admin (org-level config, audit log) |
| **Candidate** | Test Player only | Enter an invitation token and take an assigned assessment. Cannot browse anything else — typing `/admin` etc. into the URL bar redirects them straight back to the Test Player |

---

## 3. How to create a new user (trainer or candidate)

**Candidates — via the UI (this exists today):**
1. Log in as Admin or Trainer.
2. Go to **Candidates** -> **Add candidate**. Fill in email/name -> Save.
   Or use **CSV upload** to add a batch at once.

**What actually happens when you do that** (this is a real, working,
already-implemented flow, verified by reading the exact service code):
1. A real `sf_users` row is created with `role = CANDIDATE`.
2. A random 12-character password is auto-generated and bcrypt-hashed.
3. An email is sent (or logged, if SMTP isn't configured — see section 7 of
   `GMAIL_SMTP_SETUP.md`) containing that email + temporary password.
4. **That candidate can now log in** at `/login` (Candidate tab) with that
   email/password, exactly like an admin or trainer logs in.

**This is a separate thing from an assessment invitation token.** Two
different concepts, easy to conflate:

| | Candidate user account | Assessment invitation |
|---|---|---|
| Created from | Candidates page -> Add candidate | Assessments page -> invite candidates to a specific published assessment |
| What it is | Email + password, like any login | A one-time secret token tied to **one candidate + one specific assessment** |
| Lets them | Log in to `/login` at all | Actually start and take that one assessment via the Test Player |
| Reused across assessments? | Yes, same login every time | No — a new token is generated per assessment invitation |

**One candidate, one email, one link — confirmed by the code:** every call
to the invite endpoint generates a brand new random token
(`UUID.randomUUID() + "." + UUID.randomUUID()`), hashed and stored against
that one candidate + that one assessment, and emails it to that candidate's
address. There's no shared/reusable link — each invitation is unique.

**The actual end-to-end test-taking flow:**
1. Admin/Trainer creates the candidate (above) — they now have a login,
   but nothing to take yet.
2. Admin/Trainer publishes an assessment (Assessments page).
3. Admin/Trainer selects that candidate and clicks **Invite** on the
   published assessment. This is what generates the one-time token and
   emails it.
4. Candidate receives the email (or you retrieve the token from backend
   logs if SMTP isn't set up — search logs for "SMTP not configured").
5. Candidate goes to `/candidate` (Test Player), pastes the token, clicks
   **Start Assessment**. The backend validates the token
   (`/candidate/link/validate`), confirms it isn't expired/used, then
   starts the actual attempt.
6. Candidate answers questions (autosaved as they go) and submits.

**Trainers — now has a real UI.** Log in as Admin, go to **Trainers** ->
**Add trainer**, fill in email/name -> Save. Same underlying mechanism as
adding a candidate: a real account is created, a password is
auto-generated, and credentials are emailed (or logged, if SMTP isn't
configured). If you'd rather do it via the API directly:
```bash
curl -X POST http://localhost:8080/api/v1/skillforge/trainers \
  -H "Authorization: Bearer <your admin access token>" \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "<your org id>",
    "email": "newtrainer@apex.example",
    "fullName": "New Trainer",
    "password": "SomeStrongPassw0rd!"
  }'
```
Get `<your admin access token>` from the browser: log in as Admin, open
DevTools -> Application -> Local Storage -> `executionos.accessToken`. Get
`<your org id>` the same way from `executionos.user` -> `organizationId`.

**Registering a brand new organization from scratch** (not using the demo
org at all):
```bash
curl -X POST http://localhost:8080/api/v1/skillforge/auth/register-organization \
  -H "Content-Type: application/json" \
  -d '{
    "organizationName": "Your Company",
    "slug": "your-company",
    "adminEmail": "you@yourcompany.com",
    "adminFullName": "Your Name",
    "adminPassword": "SomeStrongPassw0rd!"
  }'
```
This creates the org and its first ORG_ADMIN — **but that admin account
starts as `INVITED`, not active.** Check the email you gave it (or backend
logs if SMTP isn't configured — search for "SMTP not configured") for a
verification link/token, then visit `/verify-email?token=...` (or paste the
token manually on that page). Only after that can they log in and create
trainers/candidates as above.

---

## 3a. Why the Questions page shows nothing, and how the CSV format works

**Why it's empty:** the Questions page only ever shows questions with
`status = APPROVED`. Nothing creates any automatically — not the demo
bootstrap (it only creates the 3 demo user accounts, zero questions), not
the app itself. You have to put questions in through one of two paths:

**Path 1 — CSV import through the UI** (Question Bank page -> Import CSV).
Exact required format (also shown in-app with a downloadable sample):
```
subject,prompt,type,difficulty,options,correct_answer,expected_time_seconds,marks,topic,explanation
```
- **subject** — must match an existing subject name shown in the filter
  dropdown (e.g. Java, Linux, SQL).
- **type** — one of `MULTIPLE_CHOICE`, `MULTIPLE_SELECT`, `TRUE_FALSE`,
  `FILL_BLANK`, `CODE_OUTPUT`, `ORDERING`, `SCENARIO`.
- **difficulty** — `EASY`, `MEDIUM`, or `HARD`.
- **options** — pipe-separated, e.g. `Yes|No|Maybe` (blank for free-text
  types).
- **correct_answer** — must exactly match one of the options for
  MULTIPLE_CHOICE/TRUE_FALSE; pipe-separated for MULTIPLE_SELECT.
- **expected_time_seconds, marks, topic, explanation** — optional,
  sensible defaults used if blank.
- Every row is validated independently — a bad row never fails the whole
  file, the import summary tells you exactly how many imported vs failed
  and why, per row.

**Path 2 — there are already 270 real questions sitting unused in this
repo, ready to import.** The `question-bank/` folder at the project root
has 9 JSON files (Java, Linux, SQL, Shell, Splunk, ServiceNow, New Relic,
Spring Boot, AI Prompt Engineering — 270 questions total, verified by
actually running the script's dry-run mode) plus
`question-bank/import_question_bank.py`, a script that writes them
directly into Postgres. **Nobody has run it yet against your database** —
that's the actual, complete reason the Questions page is empty even though
"we have questions in a folder." To load them:
```bash
cd question-bank
pip install psycopg2-binary --break-system-packages
export DATABASE_URL="postgresql://user:pass@host:5432/executionos"
python3 import_question_bank.py --organization-id <your-real-org-id> --dry-run   # preview first
python3 import_question_bank.py --organization-id <your-real-org-id>            # actually import
```
Safe to re-run — it skips anything already imported (matches on
organization + question code), so you can't accidentally double-import.

---

## 3b. Every frontend API call, checked against the backend — confirmed matching

Every single call the frontend makes (`api.get/post/put(...)` across all
pages) was cross-checked line-by-line against every `@GetMapping`/
`@PostMapping`/`@PutMapping` actually declared in `SkillForgeController.java`.
All of them match exactly — same path, same HTTP method. There is no
frontend call pointing at a URL the backend doesn't actually expose. If you
ever see a 404 in the Network tab, it's an environment/routing issue
(wrong `NEXT_PUBLIC_API_URL`, or nginx `/api/` -> `/api/v1/` rewrite missing
— see `RUNBOOK.md` troubleshooting), not a mismatch in the code itself.

---

## 3c. Getting a 403 on CSV import (or any staff page)?

This is very likely **working as intended, not a bug** — read this before
assuming it's broken. Earlier in this project, every staff endpoint
(question import, candidate management, assessment publishing, reports)
only required "any logged-in user" — meaning a Candidate account could
call them too. That was fixed: those endpoints now correctly require an
Admin/Trainer/Evaluator role. A 403 means **you're logged in as the wrong
role for that action** — most likely you used the Candidate quick-login
button earlier in the same browser tab and it's still active.

To fix: open DevTools -> Application -> Local Storage -> check
`executionos.user` -> `role`. If it says `CANDIDATE`, sign out and log back
in as Admin or Trainer, then retry the CSV import. If it already says
`ORG_ADMIN` or `TRAINER` and you still get 403, that's unexpected —
capture the exact response body (Network tab -> the failed request ->
Response) and check it against `PRODUCTION_AUDIT.md`; it's not a known
issue as of this pass.

---

## 4. How passwords actually work

- Passwords are hashed with **bcrypt** before storage — the database never
  contains a plaintext or reversible password, ever, for any role.
- **Forgot password:** click "Forgot password?" on the login page -> enter
  email -> backend always says "if that email has an account, a reset link
  was sent" (deliberately, so it can't be used to check which emails have
  accounts) -> check your inbox (or backend logs if SMTP isn't set up yet,
  see section 7) -> link is valid for 1 hour.
- **Sessions:** logging in issues two tokens:
  - An **access token** (15 minutes) — sent on every API call.
  - A **refresh token** (7 days) — used automatically, silently, in the
    background to get a new access token when the old one expires. You
    will not be randomly logged out every 15 minutes; this happens
    invisibly. You only get logged out for real if the refresh token itself
    expires (7 days of no activity) or you click **Sign out** (which also
    revokes it server-side immediately).

---

## 5. Google Sign-In (OAuth) — setup

This already exists in the code; it's off until you configure it. It's
**"sign in", not "sign up"** — it logs in an *existing* SkillForge account by
matching the Google account's email; it will never silently create a new
account with a guessed role.

### 5a. Create the OAuth Client ID (Google Cloud Console)

1. Go to https://console.cloud.google.com/apis/credentials
2. Create a project if you don't have one already (top-left project selector).
3. **Create Credentials** -> **OAuth client ID**.
4. If prompted, configure the **OAuth consent screen** first: User type
   "External" (or "Internal" if using Google Workspace), fill app name +
   support email, save.
5. Back in Credentials -> **Create Credentials** -> **OAuth client ID**:
   - Application type: **Web application**
   - Name: `SkillForge`
   - **Authorized JavaScript origins:** add every origin you'll actually
     load the login page from, e.g.:
     ```
     http://localhost:3000
     https://app.yourdomain.com
     ```
   - Leave "Authorized redirect URIs" empty — this app uses Google's
     client-side Sign-In SDK (a popup/button), not a redirect flow.
6. Click **Create**. Copy the **Client ID** (looks like
   `123456789-abc123.apps.googleusercontent.com`).

### 5b. Set it on both frontend and backend

```env
# Backend
GOOGLE_CLIENT_ID=123456789-abc123.apps.googleusercontent.com

# Frontend
NEXT_PUBLIC_GOOGLE_CLIENT_ID=123456789-abc123.apps.googleusercontent.com
```
**Both must be the exact same value.** The backend verifies the token's
`aud` (audience) claim matches this — if they don't match, sign-in fails
with "Invalid Google token."

### 5c. Verify

Restart both frontend and backend after setting the vars. A "Sign in with
Google" button will now appear on the login page automatically (it's
conditionally rendered — no button at all if the client ID isn't set,
rather than a broken one). Click it, pick a Google account whose email
matches an existing SkillForge user (created via section 3) -> logs
straight in.

**Common error:** "No SkillForge account exists for you@gmail.com — ask an
admin to invite you first." This means Google auth worked correctly, but
that email doesn't have a SkillForge account yet — create one (section 3)
with that exact email, then try again.

---

## 6. Login failed? Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "Invalid email or password." | Wrong password, or account doesn't exist yet | Double check; if it's a demo account, make sure you called `demo/bootstrap` first (section 1) |
| "Account is not active." | User status isn't `ACTIVE` (e.g. suspended) | An admin needs to reactivate the account via the API/DB |
| Login spins forever / network error in console | Backend isn't running, or CORS_ORIGINS on the backend doesn't include the exact origin you're loading the frontend from | Check `docker compose ps` / backend logs; fix `CORS_ORIGINS` to match exactly (scheme + host + port) |
| `net::ERR_CONTENT_DECODING_FAILED` in browser console | Already fixed in this codebase (was a proxy header bug) — if you see this again after further edits, check `src/lib/proxy-headers.ts` | See `RUNBOOK.md` troubleshooting section |
| Logged in, then kicked back to `/login` after ~15 min | Should no longer happen — refresh token wiring was added in this pass. If it still happens, check the browser console for a failed `POST /auth/refresh` and read the error body | Confirm `sf_refresh_tokens` table exists (Flyway migration `V10`) |
| 403 Forbidden on an API call | You're calling a staff-only endpoint with a Candidate token, or vice versa | Expected — role enforcement is intentional (section 2). Log in with the right role |
| Google Sign-In button doesn't appear at all | `NEXT_PUBLIC_GOOGLE_CLIENT_ID` isn't set on the frontend | Set it and restart the frontend (section 5) |
| Candidate has no invitation token | Nobody invited them yet, or SMTP isn't configured | Have an admin/trainer invite them from Candidates/Assessments; if SMTP isn't set up, check backend logs for the token (search "SMTP not configured") |

---

## 7. Tracking logs, errors, and every API call — tools, URLs, credentials

This app ships with a full observability stack in `docker-compose.yml`.
All of it is now pre-wired (this pass added Promtail and Grafana
auto-provisioning — previously Loki ran with nothing feeding it logs into
it, and Grafana had no datasources configured).

| Tool | What it's for | URL (local) | Username | Password |
|---|---|---|---|---|
| **Grafana** | Dashboards for both metrics and logs, one UI | http://localhost:3001 | `admin` | `admin` by default (change via `GRAFANA_ADMIN_PASSWORD` env var — **required** in the prod overlay, no default allowed) |
| **Prometheus** | Raw metrics (requests/sec, JVM memory, GC, etc.) | http://localhost:9090 | — | — (internal only, not meant to be exposed publicly) |
| **Loki** | Log storage, queried through Grafana (not usually opened directly) | http://localhost:3100 | — | — |
| **Swagger UI** | Interactive API docs — browse and try every endpoint | http://localhost:8080/swagger-ui/index.html | `admin` | `change-this-swagger-password` by default (`SWAGGER_PASSWORD` env var) |

### 7a. Fastest way to see what's happening right now
```bash
docker compose logs -f backend    # every backend log line, live
docker compose logs -f frontend
docker compose logs -f            # everything, all services, interleaved
```

### 7b. Searching historical logs across all services (Grafana + Loki)
1. Open http://localhost:3001, log in.
2. Left nav -> **Explore** -> select the **Loki** data source (already
   added, nothing to configure).
3. Query examples:
   ```
   {container="skillforge-backend-1"}
   {container="skillforge-backend-1"} |= "ERROR"
   {container="skillforge-backend-1"} |= "SMTP not configured"
   ```
4. This is genuinely more useful than `docker compose logs` once you have
   more than one instance or want to search across days of history.

### 7c. Metrics dashboard
1. Grafana -> **Dashboards** -> the **skillforge** dashboard is already
   loaded (from `docker/grafana/dashboards/skillforge.json`) — no manual
   import needed.
2. Backend also exposes raw metrics at
   `http://localhost:8080/actuator/prometheus` (Basic Auth: same as
   Swagger) if you want to point any other tool at it.

### 7d. Tracing a specific API call end-to-end
1. Browser DevTools -> Network tab -> click the failing request ->
   **Headers** tab, note the timestamp.
2. Grafana -> Explore -> Loki -> query
   `{container="skillforge-backend-1"}` around that timestamp, or search
   for the request path, e.g. `|= "/skillforge/auth/login"`.
3. Every request also gets Spring's default access-log-style line in
   stdout with method, path, and status — searchable the same way.

### 7e. Turning observability off (if you don't need it locally)
Comment out (or delete) the `prometheus`, `loki`, `promtail`, and `grafana`
blocks in `docker/docker-compose.yml` — everything else (the app itself)
works completely independently of them.

---

## 8. Complete environment variable reference — exact names, for every platform

Every single environment variable this app reads, anywhere. Nothing
assumed, nothing skipped. Copy the exact variable **names** — the values
are yours to generate/fill in.

### Backend — set these wherever the backend runs (Render, Railway, EC2, Docker Compose)

| Variable | Required? | Example / how to generate | What it's for |
|---|---|---|---|
| `DATABASE_URL` | **Yes** | `postgresql://user:pass@host:5432/dbname` (Neon/Railway give you this directly) | Database connection |
| `DATABASE_USERNAME` | Only if not embedded in `DATABASE_URL` | `executionos` | Fallback DB username |
| `DATABASE_PASSWORD` | Only if not embedded in `DATABASE_URL` | `executionos` | Fallback DB password |
| `JWT_SECRET` | **Yes** | `openssl rand -hex 32` | Signs every login/session token — must be ≥32 characters |
| `CORS_ORIGINS` | **Yes** | `https://your-frontend.vercel.app` | Must exactly match your frontend's real URL (scheme+host, no trailing slash) — comma-separate multiple |
| `FRONTEND_BASE_URL` | **Yes** | `https://your-frontend.vercel.app` | Used to build the link inside candidate invitation emails |
| `SWAGGER_USERNAME` | Recommended | `admin` | Basic-auth username protecting `/swagger-ui` and `/actuator/prometheus` |
| `SWAGGER_PASSWORD` | Recommended | `openssl rand -hex 16` | Basic-auth password for the same |
| `GOOGLE_CLIENT_ID` | Only if using Google Sign-In | from Google Cloud Console (see section 5) | Verifies Google login tokens |
| `SMTP_HOST` | Only if you want real emails sent | `smtp.gmail.com` | See `GMAIL_SMTP_SETUP.md` |
| `SMTP_PORT` | Only with SMTP_HOST | `587` | |
| `SMTP_USERNAME` | Only with SMTP_HOST | your email address | |
| `SMTP_PASSWORD` | Only with SMTP_HOST | Gmail App Password (not your real password) | |
| `RATE_LIMIT_CAPACITY` | No (default 120) | `120` | Requests per IP+route per minute before a 429 |
| `RATE_LIMIT_REFILL_PER_MINUTE` | No (default 120) | `120` | |
| `MAX_UPLOAD_BYTES` | No (default 52428800 = 50MB) | `52428800` | File upload size cap (ExecutionOS-side attachments) |
| `ALLOWED_UPLOAD_TYPES` | No (has a sensible default list) | `application/pdf,image/png,...` | |
| `VIRUS_SCAN_ENABLED` | No (default false) | `false` | |
| `SPRING_PROFILES_ACTIVE` | Recommended | `prod` | Switches logging verbosity/behavior |
| `PORT` | No (default 8080; Render/Railway set this automatically) | — | Don't set this manually on Render/Railway — they inject it |

### Frontend — set these wherever the frontend runs (Vercel, Netlify, Render, Railway)

| Variable | Required? | Example | What it's for |
|---|---|---|---|
| `NEXT_PUBLIC_API_URL` | **Yes** (pick one strategy below) | `https://your-backend.onrender.com/api/v1` | If set, the **browser** calls the backend directly. Simplest option. |
| `BACKEND_API_URL` | Only if you leave `NEXT_PUBLIC_API_URL` unset | `https://your-backend.onrender.com/api/v1` | If `NEXT_PUBLIC_API_URL` is unset, the frontend's own server-side proxy (`/api/[...path]`) forwards here instead, keeping the backend URL out of the browser entirely |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | Only if using Google Sign-In | same value as backend's `GOOGLE_CLIENT_ID` | Must be **identical** to the backend's value |

**Pick exactly one of these two strategies for connecting them — don't mix:**
- **Strategy A (simpler, recommended to start):** set `NEXT_PUBLIC_API_URL`
  on the frontend to your backend's public URL + `/api/v1`. Done — the
  browser talks straight to the backend.
- **Strategy B (backend URL hidden from the browser):** leave
  `NEXT_PUBLIC_API_URL` unset, set `BACKEND_API_URL` instead. Every
  frontend API call goes to the frontend's own domain first, which
  quietly forwards it server-side.

### Observability (optional, Docker Compose only — not used on Vercel/Render/Railway)

| Variable | Required? | Example |
|---|---|---|
| `GRAFANA_ADMIN_PASSWORD` | Recommended for anything beyond a laptop demo | `openssl rand -hex 16` |

---

## 9. Platform-by-platform: exactly where to paste these

### Render (backend)
1. Dashboard -> your service -> **Environment** tab -> **Add Environment Variable** -> paste each `Key` / `Value` pair from the backend table above, one at a time -> **Save Changes** (auto-redeploys).

### Railway (backend, if using instead of Render)
1. Project -> your service -> **Variables** tab -> **New Variable** -> paste each pair -> Railway redeploys automatically on save.
2. Railway auto-injects its own `DATABASE_URL` if you add a Railway Postgres plugin to the same project — use that value as-is instead of Neon's.

### Vercel (frontend)
1. Project -> **Settings** -> **Environment Variables** -> add each `Key`/`Value` -> choose **Production** (and **Preview** if you want preview deploys to also work) -> **Save**.
2. Any env var change requires a **redeploy** to take effect (Vercel -> Deployments -> ... -> Redeploy) — saving alone does not restart a running deployment.

### Netlify (frontend, if using instead of Vercel)
1. Site -> **Site configuration** -> **Environment variables** -> **Add a variable** -> paste each pair -> Save.
2. Netlify's Next.js support needs the `@netlify/plugin-nextjs` plugin — check "Next.js Runtime" is enabled under **Site configuration -> Build & deploy**; without it, the `/api/[...path]` server-side proxy route won't work correctly if you're using Strategy B above. If you hit this, use Strategy A (`NEXT_PUBLIC_API_URL`) instead on Netlify.
3. Same as Vercel: redeploy after changing env vars (**Deploys** -> **Trigger deploy**).

---

## 10. The most common way this breaks across platforms, and the fix

**Symptom:** frontend loads fine, but every API call fails (network error,
CORS error, or 404) once both are deployed separately.

**Checklist, in order:**
1. Open your frontend's real URL. Open DevTools -> Network tab -> try to
   log in -> click the failed request -> check the **Request URL** it
   actually tried to call.
2. Is that URL pointing at your real backend domain (Strategy A) or your
   own frontend domain + `/api/...` (Strategy B)? Whichever it is, that
   tells you which env var is wrong.
3. **CORS error specifically** ("has been blocked by CORS policy" in the
   console): your backend's `CORS_ORIGINS` doesn't exactly match your
   frontend's real URL. Common mistakes: trailing slash, `http` vs
   `https`, `www.` vs no `www.`, wrong subdomain. Fix it on
   Render/Railway's environment variables and redeploy the **backend**.
4. **404 on every call:** your `NEXT_PUBLIC_API_URL` or `BACKEND_API_URL`
   is missing the `/api/v1` suffix, or points at the wrong backend URL
   entirely (e.g. still `localhost`). Fix it on Vercel/Netlify's
   environment variables and redeploy the **frontend**.
5. **Everything looks right but still fails:** confirm the backend is
   actually up — `curl https://your-backend-url/actuator/health` should
   return `{"status":"UP"}`. If that fails, the problem is entirely on the
   backend side (check its deploy logs), not a frontend/CORS issue at all.

---

## 11. Minimum-step deployment recap

Full detail is in separate files; this is the "just get it running" summary.

**Local:**
```bash
cd docker && docker compose up --build
curl -X POST http://localhost:8080/api/v1/skillforge/demo/bootstrap
# open http://localhost:3000/login
```

**Option A — AWS EC2 + Docker Compose:** see `AWS_DEPLOYMENT.md`.
Summary: launch Ubuntu EC2 -> install Docker -> copy code over -> set
`.env` -> `docker compose -f docker-compose.yml -f docker-compose.prod.yml
up -d --build` -> add Caddy for free HTTPS -> bootstrap demo data -> done.

**Option B — Render + Neon + Vercel:** see `OPTION_B_RENDER_NEON_VERCEL.md`.
Summary: Neon project (database) -> Render web service from `backend/`
(set env vars) -> Vercel project from `frontend/` (set env vars) -> fix
CORS once you know your real URLs -> bootstrap demo data -> done. About 15
minutes, free tier.

**Gmail SMTP (either option):** see `GMAIL_SMTP_SETUP.md`. Summary: turn on
2-Step Verification -> generate an App Password -> set 4 env vars
(`SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`, `SMTP_USERNAME`,
`SMTP_PASSWORD`) -> restart backend.

---

## 12. What was fixed in this pass, specifically for login/roles/JWT

Being precise about what changed, since this file promises "perfect and
correct":

- SkillForge sessions previously had **no refresh mechanism at all** —
  every login hard-expired in 15 minutes with no renewal path. Added a real
  refresh-token table, endpoint, and rotation-on-use (old token revoked
  every time a new one is issued), plus a frontend interceptor that renews
  silently on a 401 before ever bothering the user.
- **Sign out previously only cleared local browser state** — the refresh
  token stayed valid server-side indefinitely. Sign-out now also revokes it
  on the backend.
- **Every staff-only SkillForge endpoint (candidates roster, question bank,
  assessments, reports, org management) was only protected by "any
  authenticated user"** — meaning a Candidate's own valid token could call
  any of them directly via the API, bypassing the UI entirely. Now
  explicitly restricted to `ADMIN`/`TRAINER`/`EVALUATOR` roles server-side.
- Google Sign-In was already implemented correctly server-side (verifies
  the token against Google, checks audience + email verification, only
  logs into existing accounts) — wired its response through the same
  session/refresh-token issuance as normal login for consistency.
- Frontend now actually filters the sidebar and guards routes by role, so
  Admin/Trainer/Candidate visibly differ instead of showing the identical
  dashboard to everyone.

**What I could not verify by actually running it:** this sandbox has no
access to Maven Central, so the backend changes are correct by careful
static review (every field/method reference double-checked against the
actual entity/DTO definitions, braces/parens balance-checked) but not by a
green `mvn test` run. **Run `mvn test` yourself** before trusting this in
production — I'd rather say that plainly than claim a guarantee I can't
back up.
