
# PRODUCTION_AUDIT.md

Audit date: this session. Scope: full repository (`skillforge-application/`),
covering the state after several prior fix passes in this conversation
(login/proxy bug, nginx routing bug, publish/invite flow, RBAC, refresh
tokens, observability wiring — see `COMPLETE_GUIDE.md` §9 for what's already
resolved). This document reports what is **still** true as of right now,
not a history of every past fix.

**Read this before reading anything else I write about this codebase.**
The requesting prompt set a "Honesty Rule" — this audit follows it: nothing
below is softened, and the closing section is explicit about what a single
pass can and cannot responsibly claim to finish.

---

## Update — fixes made in this pass (read this first)

Since the audit above was written, the following were implemented and are
now considered resolved (details in each item, kept below for the
before/after record):

- **C3 (Analytics/Search fake data):** resolved via removal + real rebuild.
  `/analytics` was deleted entirely (it was orphaned dead code — not even
  linked from any nav). `/search` was rewired to a real, working
  implementation (case-insensitive search across candidates, approved
  questions, and assessments, scoped to the caller's organization) —
  replacing the old always-empty stub. The fake `/dashboard/*` and
  `/analytics/*` backend endpoints were deleted, not just hidden.
- **C4 (Settings page fully fake):** resolved by scoping down to only what's
  real. Organization branding (name, primary color, certificate prefix,
  locale) now actually persists via a new `PATCH /organizations/{id}`
  endpoint. The SMTP/security-policy/integrations cards were removed
  entirely rather than given a fake save button, since their toggles were
  never read anywhere else in the codebase (candidate-player.tsx doesn't
  consult them) — persisting them would still have been misleading.
- **C5 (no email verification):** resolved for the one truly self-service
  signup path. `register-organization` now creates the account as
  `INVITED`, emails a verification link/token, and requires
  `POST /auth/verify-email` before login succeeds. New `/verify-email`
  frontend page added.
- **H1 (no brute-force lockout):** resolved. 5 failed attempts locks the
  account for 15 minutes, tracked per-account (not just per-IP).
- **H3 (no Trainer creation UI):** resolved. New `/trainers` page, admin-only,
  mirroring the existing Candidates page's add-user flow.
- **H4 (CSV import unbounded):** resolved. Explicit 2MB / 5,000-row caps
  with a clear error message before parsing begins.
- **M1 (IDOR on /organizations/{id}):** resolved. Both GET and the new
  PATCH now verify the caller's own JWT-derived organizationId matches the
  requested id.

**Still open, unchanged from the original audit:** C1/C2 (near-zero backend
test coverage, unverifiable by execution in this sandbox), H2 (no
access-token blacklist — architectural tradeoff, not a quick fix), H5/H6
(test coverage for the new RBAC/auth work specifically), M2 (Reports
page still partially estimated), M4 (no dark mode), M5 (CI unverified),
M6 (no concurrent-session limit), and all Low items.

---



- Full-text search across `frontend/src` and `backend/src` for every
  banned keyword (mock, fake, placeholder, stub, todo, sample, temporary,
  dummy, hardcoded, coming soon, not implemented, simulate, test only,
  example, demo).
- Manual read-through of every controller, security config, and the
  entry points for every frontend page, carried over from multiple prior
  deep-dive passes earlier in this same session (login flow, JWT/refresh,
  RBAC, CSV import, publish flow, invitations, admin endpoints).
- **What this audit could NOT do:** run the backend (`mvn test`,
  `mvn compile`) or a live Postgres instance — this sandbox has no network
  access to Maven Central. Every backend finding below is from static
  reading of the source, not from a running system. Findings marked
  "(unverified by execution)" should be confirmed by actually running the
  app before you treat them as certain.

---

## CRITICAL

### C1. Backend has effectively zero automated test coverage
- **File:** `backend/src/test/java/com/executionos/ExecutionOsApplicationTests.java`
- **Line:** entire file — one `contextLoads()`-style smoke test, nothing else.
- **Explanation:** There are no controller tests, no service tests, no
  repository tests, no security/RBAC tests, no auth/refresh-token tests
  anywhere in the backend. "95%+ meaningful coverage" is not close to true;
  actual coverage of business logic is effectively 0%.
- **Impact:** Every backend change (including everything fixed earlier in
  this session) is unverified by any automated check. Regressions are
  invisible until manually clicked through.
- **Fix:** Needs a real test suite: `@WebMvcTest`/`@SpringBootTest` slice
  tests per controller (Auth, SkillForge CRUD, Admin), a dedicated
  `SecurityConfigTest` asserting role matrices (a CANDIDATE token gets 403
  on every staff endpoint, a TRAINER token gets 403 on `/admin/**` and
  `/settings`-equivalent endpoints), and a `JwtServiceTest`/
  `SkillForgeServiceTest` covering login/refresh/logout/rotation. This is
  a multi-day engineering effort on its own, not a quick add.

### C2. Backend test suite cannot be executed in this environment
- **Explanation:** This sandbox's network egress does not include Maven
  Central (`repo1.maven.org`) or any Maven mirror. `mvn compile` /
  `mvn test` cannot run here at all.
- **Impact:** Every backend code change made in this entire conversation
  (JWT refresh tokens, RBAC matchers, the AdminController DTO fix, the
  publish-flow fix, etc.) has been verified only by careful manual
  static review (import checks, brace/paren balance, cross-referencing
  every field/method against its actual declaration) — never by an actual
  compiler or test run.
- **Fix:** Run `cd backend && mvn clean verify` in an environment with
  real internet access before deploying anything from this session.
  This is the single most important verification step missing right now.

### C3. Several visible features are permanently fake/placeholder, violating the "fully work or remove" rule
- **Files:**
  - `backend/src/main/java/com/executionos/controller/ResourceControllers.java`,
    lines 113, 118, 282, 288–293, 295 — `/api/v1/dashboard/today`,
    `/dashboard/stats`, `/dashboard/streaks`, `/analytics/weekly`,
    `/analytics/categories`, `/analytics/deep-work`, and `/search` all
    return `Map.of(..., "isPlaceholder", true)` with hand-typed numbers
    (`"tasksDone", 5`, `"streak", 12`, etc.) — never a real query.
  - `frontend/src/app/analytics/page.tsx` renders these directly.
  - `frontend/src/app/search/page.tsx` calls the same `/search` stub —
    always zero results, for any query, forever.
- **Explanation:** These are self-labeled (`isPlaceholder: true`) rather
  than silently faked, which is better than nothing, but the task's own
  rule is explicit: every visible feature must fully work end-to-end **or
  be completely removed** — "labeled as fake" is not one of the two
  allowed states.
- **Impact:** Two entire nav items (Analytics, Search) are permanently
  non-functional for any real user.
- **Fix — this is a product decision, not just an engineering one:**
  - **Analytics:** either build real aggregation queries against
    `Task`/`FocusSession` (needs new repository query methods + a real
    service layer — a genuine feature build), or delete the `/analytics`
    page and its nav entry entirely.
  - **Search:** either build a real search implementation (Postgres
    full-text search across candidates/questions/assessments is the
    realistic minimum bar — a genuine feature build, likely 1-2 days), or
    delete the `/search` page and its nav entry entirely.
  - I have not made this call unilaterally because deleting visible
    product surface area is a decision you should sign off on, not one a
    single automated pass should make silently.

### C4. Settings page has no backend at all
- **File:** `frontend/src/app/settings/page.tsx`, line ~16 (self-documenting
  comment already present).
- **Explanation:** Every field (branding, SMTP config, security policy
  toggles, webhook URL) is local component state only. "Save changes"
  does nothing server-side. No `SfOrganizationSettings`-style entity,
  repository, or endpoint exists anywhere in the backend.
- **Impact:** An admin can "configure" OTP verification, fullscreen
  requirement, copy-paste detection, etc. and it has **zero effect** on
  actual candidate exam delivery — those toggles are not read anywhere
  else in the codebase. This is actively misleading, not just incomplete.
- **Fix:** Same two-path rule as C3 — either build the full persistence +
  actually wire each toggle into `candidate-player.tsx`'s real behavior
  (a genuine feature build touching both backend and the exam-taking UI),
  or remove the Settings page and its nav entry until it's real.

### C5. No email verification flow
- **Explanation:** `SkillForgeService.createUser(...)` sets new accounts to
  `UserStatus.ACTIVE` immediately — there is no `PENDING_VERIFICATION`
  status, no verification token/email, no `/auth/verify-email` endpoint
  anywhere in `SkillForgeController.java`.
- **Impact:** Anyone can be granted an active account with an email they
  don't own or control (relevant for candidate self-registration flows if
  those are ever added; less relevant today since only admins create
  users, but the requirement explicitly asked this be checked).
- **Fix:** Add a `PENDING_VERIFICATION` status, a verification token
  (mirroring the existing password-reset token pattern exactly), a
  `/auth/verify-email` endpoint, and block login until verified (or allow
  login but restrict sensitive actions — a product decision).

---

## HIGH

### H1. No account-level brute-force protection, only IP-based rate limiting
- **File:** `backend/src/main/java/com/executionos/security/RateLimitFilter.java`
- **Explanation:** Limiting is keyed on `remoteAddr + URI` only. There is
  no failed-attempt counter per account, no temporary account lockout, no
  CAPTCHA trigger.
- **Impact:** A distributed attacker (rotating source IPs) can
  credential-stuff a single account indefinitely; conversely, many
  legitimate users behind one NAT/proxy IP can be throttled by one
  person's mistakes.
- **Fix:** Add a `failed_login_attempts` + `locked_until` pair of columns
  on `sf_users` (mirrors the existing `reset_token_hash` migration
  pattern), increment on failed password match in
  `SkillForgeService.login()`, lock for a backoff window after N failures.

### H2. Stolen access tokens cannot be individually revoked before natural expiry
- **Explanation:** Only refresh tokens are tracked/revocable
  (`sf_refresh_tokens`, added this session). A stolen **access token**
  remains valid for its full 15-minute lifetime no matter what — sign-out
  only revokes the refresh token, it cannot invalidate an already-issued
  access token.
- **Impact:** Bounded blast radius (15 minutes) but not zero. This is a
  common, generally-accepted JWT tradeoff, not a defect introduced by this
  session — flagging it because the request explicitly asked about "token
  blacklist."
- **Fix (if you want zero-tolerance):** Either shorten the access token
  window further (e.g. 5 minutes) or add a server-side denylist (Redis set
  of revoked JTIs, checked on every request) — the latter reintroduces
  state into an otherwise-stateless auth system, a real architectural
  tradeoff worth discussing before building.

### H3. No UI exists to create a Trainer account
- **File:** no corresponding page under `frontend/src/app/`; API exists
  (`POST /api/v1/skillforge/trainers`).
- **Impact:** An admin cannot onboard a trainer without using `curl`/Postman
  directly — documented as a known gap in `COMPLETE_GUIDE.md` §3, but still
  open.
- **Fix:** A `/users` (or `/trainers`) admin page mirroring the existing
  `/candidates` page's "Add candidate" form, pointed at
  `POST /trainers` instead.

### H4. CSV question import has no explicit size/row limits or content-type check
- **File:** `backend/src/main/java/com/executionos/skillforge/model/SkillForgeController.java`,
  line 247 (`@PostMapping("/questions/import/csv")`, `@RequestBody String csvContent`).
- **Explanation:** Unlike file uploads elsewhere in the app (which go
  through `UploadValidationService`), this endpoint accepts an arbitrary
  raw string body with no explicit max-row-count or max-size check beyond
  whatever the servlet container's default POST size limit happens to be.
- **Impact:** A very large paste could tie up server memory/CPU parsing
  it row-by-row; no application-level guardrail exists specifically for
  this endpoint.
- **Fix:** Add an explicit row-count cap (e.g. reject anything over 5,000
  rows with a clear 400) before the parse loop in
  `SkillForgeService.importQuestionsFromCsv`.

### H5. No repository/service/security test coverage means RBAC changes made this session are unverified by anything except manual reasoning
- Directly related to C1, called out separately because it's specifically
  about the RBAC matrix added this session
  (`SecurityConfig.java` — the new `hasAnyRole("ADMIN","TRAINER","EVALUATOR")`
  block covering candidates/questions/assessments/etc.).
- **Fix:** At minimum, one `@SpringBootTest` + `MockMvc` test class that
  logs in as each of the three roles and asserts 200 vs 403 across every
  matcher group — this is the highest-leverage single test class to write
  next, given it directly protects the most security-sensitive change made
  in this session.

### H6. Frontend test coverage is real but narrow
- **Files:** `frontend/src/**/*.test.{ts,tsx}` — 16 tests across 4 files
  (login page, api-client refresh flow, proxy headers, nav role-gating).
- **Explanation:** These are real, passing, meaningful tests — not
  padding — but they cover only the auth/proxy work done this session.
  Zero component tests exist for question-bank-workbench,
  assessment-workbench, candidate-player, candidates page, reports page,
  or any other page.
- **Impact:** "95%+ meaningful coverage" is not close to true on the
  frontend either, despite the auth-path tests being solid.
- **Fix:** Component tests per major workbench, prioritized by how much
  was changed this session: `assessment-workbench.tsx` (publish flow
  rewrite) and `candidate-player.tsx` (invitation flow rewrite) first.

---

## MEDIUM

### M1. `/api/v1/skillforge/organizations/{id}` has no role restriction
- Falls under the generic `.anyRequest().authenticated()` catch-all — any
  authenticated role (including CANDIDATE) can read any organization's
  branding info by ID. Low sensitivity (name/color, not secrets) but
  technically an IDOR (any org ID, not just your own) — worth a
  `.organizationId().equals(...)` ownership check regardless of role.

### M2. Reports page mixes real and estimated data without a clear visual distinction
- **File:** `frontend/src/app/reports/page.tsx`, line ~23 (comment:
  "Calculate dynamic report rows based on candidates (Mocking batches
  since they aren't fully resolved yet)").
- **Impact:** Numbers shown look uniformly "real" to a viewer even though
  part of the calculation is estimated because batch resolution isn't
  wired up yet.
- **Fix:** Either finish batch resolution (check `SfBatchRepository` join)
  or visually flag the estimated columns, not just in a code comment.

### M3. No email verification means password-reset and invitation emails are the only place `EmailService` is exercised
- Related to C5 — noting separately because it means the email pipeline
  (SMTP config, Gmail setup per `GMAIL_SMTP_SETUP.md`) has only two real
  callers in the whole app right now (`forgotPassword`, `inviteCandidates`,
  and account-creation). Low risk, just worth knowing the blast radius of
  "did I configure SMTP correctly" is currently small.

### M4. No dark mode
- Explicitly requested ("Dark mode. Light mode.") — the UI is light-mode
  only, no theme toggle, no `prefers-color-scheme` handling anywhere in
  `frontend/src/app/globals.css` or Tailwind config.
- **Fix:** Real feature build — Tailwind dark: variants across every
  component, a theme store, a toggle in the app shell. Not a quick add
  given how many components exist.

### M5. CI/CD workflows exist but are unverified in this session
- **Files:** `.github/workflows/{backend-ci,frontend-ci,docker,security,deploy}.yml`
  all exist.
- **Explanation:** I did not (and could not, no GitHub Actions runner
  here) actually trigger these and confirm they pass on the current code.
- **Fix:** Push this branch and watch the Actions tab. If `backend-ci.yml`
  runs `mvn test`, it will now at least execute what C1/C2 above could not
  verify locally — treat its result as the real verification for the
  backend changes made this session.

### M6. Concurrent session limiting doesn't exist
- Explicitly requested ("Concurrent session protection"). Nothing in
  `sf_refresh_tokens` prevents a user from being logged in from 10 devices
  simultaneously with 10 valid refresh tokens at once. Not necessarily
  wrong for this kind of app (assessment platforms often don't restrict
  this), but flagging since it was explicitly asked about.

---

## LOW

### L1. No dedicated accessibility audit performed
- Explicitly requested ("Accessible. Keyboard navigation."). Spot checks
  during this session (form labels, button roles) look reasonable, but a
  real audit (axe-core, keyboard-only walkthrough of every page) has not
  been done.

### L2. No animations / loading-skeleton polish pass
- Pages use basic conditional rendering (`{loading ? ... : ...}`), not
  skeleton loaders or transitions. Functional, not "enterprise-polished."

### L3. Documentation sprawl
- The repo root now has 9 markdown files (`README.md`, `RUNBOOK.md`,
  `AWS_DEPLOYMENT.md`, `OPTION_B_RENDER_NEON_VERCEL.md`,
  `GMAIL_SMTP_SETUP.md`, `COMPLETE_GUIDE.md`, `FINAL_AUDIT_REPORT.md`,
  `MERGE_AND_FIX_REPORT.md`, `DEPLOYMENT*.md` x3) with overlapping content
  written across different sessions. Not a functional defect, but worth
  consolidating so a new reader isn't unsure which doc is current.

---

## Summary table

| Severity | Count | Realistic effort to close all |
|---|---|---|
| Critical | 5 | Multi-week (C1/C2 are process/verification; C3/C4/C5 are genuine feature builds or deliberate removals) |
| High | 6 | Several days |
| Medium | 6 | 1-2 days |
| Low | 3 | Ongoing polish |

---

## What I am and am not doing next, and why

Per the brief's own **Honesty Rule**: I am not going to claim I can take
this from its current state to a verified "zero mock, 95%+ coverage,
full OWASP-reviewed, dark mode, CI-green, enterprise" application in this
pass, or represent partial work as complete. That is realistically weeks
of work across backend test authoring, two real feature builds (Analytics,
Search) or their removal, a Settings persistence layer, email verification,
and a UI polish pass — not something a single response can honestly
deliver and verify.

**What I can do right now, if you tell me to proceed:** fix every Critical
item that has a single unambiguous correct answer without needing a
product decision from you first — that's C1 is a large open-ended
project I'd want to scope with you rather than dump, but C5 (email
verification) and H1/H3/H4 have clear, bounded implementations I can do
immediately. C3 and C4 need your call on **build real vs. remove** before
I touch them, since removing visible product surface area isn't mine to
decide unilaterally.

Tell me which of these two paths you want for Search/Analytics/Settings —
**build for real** or **remove until they're real** — and I'll implement
whichever you pick, plus H1/H3/H4/C5, verified the same rigorous way as
this session's earlier fixes (tests where I can run them, careful static
review with explicit disclosure where I can't).
