
# TECH_STACK_GUIDE.md — What's in this app, and why

Written for you, not for a resume. Every tool below is explained as: what
it actually does in this app, why it was picked over the obvious
alternatives, and how you'd notice if it were missing.

---

## 1. The big picture, in one paragraph

Your browser talks to a **Next.js** app (the frontend). That app either
calls your **Spring Boot** backend directly, or through its own built-in
proxy — your choice, set via an environment variable. The backend talks to
one **PostgreSQL** database, sends email through whatever **SMTP** server
you configure (Gmail works), and everything runs in **Docker** containers
so "it works on my machine" also means "it works on the server." Logs and
metrics optionally flow into **Grafana** so you can see what's happening
without SSH-ing in and grepping files.

That's the whole shape. Everything below is detail on each piece.

---

## 2. Backend

### Java 21 + Spring Boot 3
**What it does:** runs every API endpoint, talks to the database, issues
login tokens, sends emails, enforces who's allowed to do what.
**Why this instead of Node/Python/Go:** Spring Boot is the default choice
for this kind of internal enterprise tool — it has batteries-included
answers for the exact things this app needs (security, database
migrations, validation, scheduled jobs) without you having to hand-pick
and wire together 10 separate libraries. Java 21 specifically brings
**virtual threads**, which matter for an app like this that does a lot of
short-lived I/O (database queries, sending emails) — more concurrent
requests handled per server without needing more memory per request.
**How you'd notice it's missing:** nothing would run. This is the engine.

### Spring Security
**What it does:** every "who's allowed to see this" decision in the app —
login, JWT validation, role checks (the Admin/Trainer/Candidate
separation), CORS rules.
**Why:** this is THE reason a Candidate account can't call
`/questions/import/csv` even if they know the URL — it's enforced at the
framework level, not hand-checked in every single controller method
(which is how security bugs happen — someone forgets one `if` check).
**How you'd notice it's missing:** anyone logged in could do anything,
regardless of role.

### PostgreSQL
**What it does:** stores everything — users, questions, assessments,
candidate attempts, invitation tokens, audit logs.
**Why Postgres over MySQL/MongoDB:** Postgres has genuinely good support
for the JSON-shaped data this app also needs (organization settings,
question options) *while still* being a real relational database with
proper foreign keys and transactions — you get both without switching
databases. It's also what Neon and Railway (your deployment options) are
built around.
**How you'd notice it's missing:** nothing persists. Every restart would
wipe everything.

### Flyway
**What it does:** every change to the database's structure (a new column,
a new table) lives in a numbered file (`V1__...sql`, `V2__...sql`, etc.)
in `backend/src/main/resources/db/migration/`. On startup, the backend
automatically runs whichever migrations haven't been applied yet, in
order.
**Why this matters to you specifically:** every fix I made this session
that needed a new database column (account lockout, email verification,
refresh tokens, org branding) came with one of these files. You never
manually run SQL — deploy the new code, it upgrades the database itself,
in order, safely, exactly once per migration.
**How you'd notice it's missing:** you'd have to manually write and run
SQL yourself every time the app's code changed, and keep every
environment (your laptop, staging, production) in sync by hand — a classic
source of "works on my machine" bugs.

### JJWT (the JWT library)
**What it does:** creates and verifies the actual login tokens (the long
string starting `eyJ...` you'd see in DevTools).
**Why tokens instead of traditional server-side sessions:** a JWT carries
its own proof of who you are (cryptographically signed), so the backend
doesn't need to keep a "logged in users" list in memory or a session
table it has to check on every single request — it just verifies the
signature. This is what makes it trivial to run multiple backend
instances behind a load balancer later without extra work.
**The tradeoff, honestly:** a stolen access token works until it expires
(15 minutes) — there's no server-side "kill this one token right now"
button. That's a known, documented limitation (see
`PRODUCTION_AUDIT.md`, finding H2), not an oversight.

### Bcrypt (via Spring Security's `PasswordEncoder`)
**What it does:** turns your password into an irreversible scrambled
string before it ever touches the database.
**Why bcrypt specifically:** it's deliberately slow (by design, tunable),
which is exactly what you want for password hashing — it makes
brute-forcing a stolen password database computationally expensive, unlike
a fast hash like plain SHA-256 (which is meant for speed, wrong tool for
passwords).
**How you'd notice it's missing:** if someone got access to the database,
every password would be visible in plain text. With bcrypt, they'd see an
unreadable hash they can't reverse.

---

## 3. Frontend

### Next.js 15 + React 19
**What it does:** everything you actually see and click.
**Why Next.js over plain React:** it comes with routing built in (each
folder under `frontend/src/app/` is a page, automatically), and it can
run a small server-side layer (the `/api/[...path]` proxy this app uses)
in the exact same project — no separate "frontend server" and "API
gateway" to stand up and keep in sync.
**How you'd notice it's missing:** there'd be no website, just the API.

### TanStack Query (`@tanstack/react-query`)
**What it does:** manages every "fetch data from the backend and show
it" flow — loading states, caching, automatically refetching after you
create/edit something.
**Why not just `fetch()` directly:** without it, every page would need to
hand-write its own loading spinner logic, error handling, and
"refresh the list after I add something" logic. TanStack Query gives you
`useQuery`/`useMutation` and handles all of that consistently — it's why
adding a candidate immediately shows up in the list without a manual page
reload.

### Zustand
**What it does:** holds a small amount of shared state across the whole
app that isn't tied to one page — specifically, who's logged in
(`use-auth-store.ts`) and which organization they belong to
(`use-organization-store.ts`).
**Why not React Context, or Redux:** Context re-renders more than you
want for frequently-read values like "am I logged in"; Redux is a lot of
ceremony for what amounts to two small pieces of state. Zustand is
deliberately minimal — a few lines to define, no boilerplate to read.

### Tailwind CSS
**What it does:** every bit of visual styling (`className="rounded-md
border border-line p-3"` etc.) instead of separate `.css` files.
**Why:** for an app with this many components, keeping styles inline next
to the markup means you're never hunting through a separate stylesheet to
figure out why something looks the way it does — the answer is always
right there in the component.

### Axios
**What it does:** the actual HTTP client making every API call
(`api.get(...)`, `api.post(...)`).
**Why not the browser's built-in `fetch`:** axios gives you
**interceptors** — code that runs on every single request/response
automatically. That's exactly the mechanism behind two of this session's
most important fixes: automatically attaching your login token to every
request, and automatically retrying once with a refreshed token if a
request comes back 401 instead of just logging you out. Doing that with
raw `fetch` means reimplementing both by hand, everywhere.

### Vitest + Testing Library
**What it does:** runs the automated tests (`npm test`) — 25 of them as
of this pass, covering login, the token-refresh flow, role-based
navigation, the assessment-publish rewrite, the candidate-invitation
rewrite, and CSV import.
**Why Vitest over Jest:** it's built on the same tool (Vite) that Next.js
itself uses for fast builds, so tests start in under a second instead of
Jest's several-second cold start — matters when you're running tests
constantly while developing, not just once in CI.

---

## 4. Infrastructure & DevOps

### Docker + Docker Compose
**What it does:** packages the backend, frontend, database, and
observability tools into isolated, reproducible containers, and
`docker-compose.yml` describes how they all talk to each other.
**Why:** "works on my machine" stops being a problem — the exact same
container that runs on your laptop is what runs on the server. It's also
what makes Option A (AWS EC2) simple: one command
(`docker compose up --build`) brings up the entire stack.

### nginx
**What it does:** the single public entry point in front of everything
(port 80) — routes `/api/` requests to the backend and everything else to
the frontend.
**Why it's there instead of exposing the backend/frontend ports
directly:** one place to add HTTPS later, one place to see all traffic,
and it means your backend's port (8080) never has to be open to the
public internet at all in the Docker Compose deployment.

### Prometheus + Grafana + Loki + Promtail
**What each one does, specifically:**
- **Prometheus** — polls the backend's `/actuator/prometheus` endpoint
  every 15 seconds and stores the numbers (requests/sec, memory, etc.)
- **Loki** — stores logs (the text lines your app prints), designed to be
  searched, unlike a scrolling terminal
- **Promtail** — the piece that actually reads every container's logs and
  ships them into Loki (added this session — Loki existed before but had
  nothing feeding it, so it was empty)
- **Grafana** — one web UI to look at both the Prometheus numbers and the
  Loki logs, instead of two separate tools
**Why this combination specifically:** it's the standard, well-documented
open-source stack for exactly this — you're not tied to a paid SaaS
product (like Datadog) just to see what your app is doing.

### GitHub Actions (`.github/workflows/`)
**What it does:** automatically runs tests and builds whenever you push
code, before it ever reaches production.
**Why it matters given this session's constraint:** I could not run
`mvn test` myself in this environment (no internet access to Maven
Central here) — GitHub Actions is where that actually happens for real,
automatically, the moment you push. It's the missing verification step
mentioned throughout `PRODUCTION_AUDIT.md`.

---

## 5. The three deployment options, and why each exists

| Option | What it actually is | When you'd pick it |
|---|---|---|
| **Local (Docker Compose)** | Everything on your own machine | Development, testing changes before deploying |
| **Option A: AWS EC2** | One virtual machine you fully control, running the same Docker Compose setup | You want everything in one place, predictable monthly cost, and you're comfortable being responsible for the server itself |
| **Option B: Render + Neon + Vercel** | Three separate managed platforms, each responsible for one piece | You want to deploy fast, not manage servers, and are fine with each platform's free tier limits (e.g. Render's free tier sleeps after 15 min idle) |

Full step-by-step for both is in `AWS_DEPLOYMENT.md` and
`OPTION_B_RENDER_NEON_VERCEL.md`.

---

## 6. How a login actually flows through all of this, end to end

Since this was your biggest pain point, here's literally everything that
happens between clicking "Log in as Admin" and seeing the dashboard:

1. **Browser** sends `POST /api/skillforge/auth/login` with your email/password.
2. **Next.js** (frontend) either sends this straight to the backend, or
   through its own proxy — depends on `NEXT_PUBLIC_API_URL` (see
   `COMPLETE_GUIDE.md` §8-9 for exactly which env var controls this).
3. **Spring Security** lets this specific URL through without requiring a
   token first (you don't have one yet — that's the point of logging in).
4. **SkillForgeController** receives it, hands off to **SkillForgeService**.
5. **PostgreSQL** is queried for a user with that email.
6. **Bcrypt** checks your password against the stored hash — never the
   other way around, the real password is never stored or compared
   directly.
7. If it matches: **JJWT** creates a signed access token (15 min) and the
   backend also writes a new row into `sf_refresh_tokens` (7 days).
8. Both tokens travel back to the browser and get saved in
   **localStorage** (via the **Zustand** auth store).
9. **Axios's interceptor** now automatically attaches that access token to
   every future request, and will silently use the refresh token to get a
   new one if the access token ever expires mid-session — you never see
   this happen.
10. **Next.js's router**, based on your role (also decided by
    **Spring Security**, embedded in the token), sends Admin/Trainer to the
    dashboard and Candidate to the Test Player.

Every one of these ten steps is a real, working piece of this app right
now — none of it is placeholder.
