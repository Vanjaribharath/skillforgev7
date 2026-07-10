# MERGE_AND_FIX_REPORT.md

**Date:** 2026-07-04
**Scope:** Reconciling `skillforge-production-final-2.zip` (infra/security hardening, 3 rounds, this session) with `skillforge-upgrade-pack-v2.zip` (a separate login/answer-key fix pass against a different snapshot).

---

## 0. How I actually did this (read this before the results)

I did not treat either package's own documentation as ground truth. I extracted both, read every file the upgrade pack's `backend-changes/` touches in full, and checked each specific claim against the real schema and real code rather than the accompanying `AUDIT.md`. This mattered: one claim in that document turned out to be materially misleading (Section 1 below), and one bug it fixed turned out to reintroduce a bug I'd already found and fixed differently (Section 2).

`skillforge-production-final-2.zip` — confirmed byte-identical to this session's own prior output (diffed the two directory trees; the only difference was `.github/` and `.gitignore`, missing from the zip because my own packaging command's exclude pattern `*.git*` accidentally also matched `.github`, not because the files didn't exist — fixed in this pass's packaging).

## 1. The one thing I would not have merged silently

The upgrade pack's `SecurityConfig.java` introduces `executionos.open-access`, **defaulting to `true`**, which turns 38 of its 39 endpoints into `.anyRequest().permitAll()` — no authentication at all unless a Railway env var is explicitly flipped to `false`. Only the new trainer answer-key endpoint keeps a `@PreAuthorize` backstop (verified real — Spring's `AnonymousAuthenticationToken` genuinely lacks `ROLE_TRAINER` etc., so this specific check would hold even in open-access mode). Everything else — candidate PII, organization data, user management, admin functions — would have zero protection by default.

I flagged this and asked before proceeding. **Confirmed: keep the existing specific, role-gated rules; do not adopt the open-access toggle at all.** It is not present anywhere in the merged codebase.

## 2. Real conflicts found and how each was resolved

| File / concern | Production-hardened (base) | Upgrade pack | Resolution |
|---|---|---|---|
| `SecurityConfig.java` | Specific role-gated rules per endpoint | Blanket `permitAll()` unless toggled | **Kept base.** Added the upgrade pack's `@PreAuthorize` on the new answer-key endpoint as an *additional* layer — pure upside, no security downgrade. |
| Auth resolution (`JwtAuthenticationFilter` / bridge) | Resolves by `uid` (UUID) from the JWT claim | Resolves by `email` via `SfUserRepository.findByEmail` returning `Optional<SfUser>` | **Kept base's `uid`-based resolution.** The upgrade pack's version inherits a bug I'd already found and fixed this session: `sf_users` only has `UNIQUE(organization_id, email)`, not a global unique constraint — the same email can legitimately exist across two organizations. An `Optional`-returning `findByEmail` throws `IncorrectResultSizeDataAccessException` the moment that happens; in the upgrade pack's filter this gets silently swallowed by its own catch block, so the person just never gets authenticated, with no clear error. Did not import this pattern. |
| `ROLE_ADMIN` grant | ORG_ADMIN/PLATFORM_ADMIN also get `ROLE_ADMIN`, so `/actuator/metrics` and `/api/v1/admin/**` work for them | Not present — SkillForge admins couldn't reach `hasRole("ADMIN")`-gated endpoints even with `open-access=false` | **Kept base's grant.** Not present in the merge; noting it here since it's a real gap in the upgrade pack's own "safe" branch that its own audit didn't catch. |
| Public catalog answer leak | Already fixed this session: `CatalogQuestion` (public) has no `correctAnswer`; `TrainerCatalogQuestion` (role-gated `/questions/{id}/full`) does | Independently found and fixed the same leak, via a different endpoint shape | Both approaches are valid; kept base's since it was already integrated with this session's other catalog work. The upgrade pack correctly diagnosed a real, serious bug — full credit for that, even though I didn't adopt its specific fix shape. |
| `examQuestions` (candidate's real per-attempt questions) | **Did not exist** — candidates got a subject-filtered catalog browse, a gap I flagged repeatedly | New method, resolves from the assessment's actual blueprint, deterministic per-attempt shuffle within difficulty bands, N+1-safe batch fetching | **Adopted, adapted to base's field names and `findAllById`** (built into `JpaRepository`, making the upgrade pack's redundant custom `findByIdIn` unnecessary). This closes a real, previously-documented gap. Wired into `candidate-player.tsx`, replacing the old catalog-browse fallback. |
| `answerKey` (trainer view of a submitted attempt) | **Did not exist** | New method: blocks at the service layer if the attempt isn't submitted yet (defense in depth beneath the `@PreAuthorize`), returns question + correct answer + candidate's answer + marks side by side | **Adopted as-is** (adapted to base's field names), with the `@PreAuthorize` role check added on the controller method. Verified the response DTO (`CandidateQuestionView`) used by the *candidate*-facing endpoint excludes `correctAnswer`; only the trainer-only DTO includes it. |
| `sf_questions` schema (`topic`, `sort_order`, `shuffle_group`) | Not present | New columns + indexes + an import-batch ledger table | **Adopted.** Migration renumbered `V7→V9` (base already has a real V7 and V8 from this session's own earlier audit rounds — seebelow). Entity fields, getters/setters, and CSV importer updated to match. |
| Question bank (270 questions, 9 subjects) | Not present | Real, reviewed content — see Section 3 | **Adopted as-is**, plus its `MASTER_GENERATION_PROMPT.md` for generating more later. |

## 3. Question bank due diligence

Read every line of `import_question_bank.py` before including it: parameterized queries throughout, no injection risk from its own construction, proper transaction/rollback handling, and its column assumptions (including `sf_subjects.active`, which does exist) checked out against the real schema. Scanned all 270 questions for suspicious content — one flagged hit ("ignore previous instructions") turned out to be a legitimate AI-Prompt-Engineering question *about* defending against prompt injection, not an actual attempt at one. Full detail and usage instructions in `question-bank/README.md`.

**Not run.** No network or live Postgres in this sandbox — verified via static review and schema cross-check only, not an actual end-to-end import. Run `--dry-run` first, then a real import against a non-production database.

## 4. Migration renumbering

The upgrade pack's `V7__question_bank_upgrade.sql` was written against a snapshot that had its own different V1–V6 (matching what you showed me earlier — your manual `V3`–`V6` audit-column fixes). This session's base codebase already has its own, different `V7` (the systematic audit-column fix from three rounds ago) and `V8` (password reset columns). Renumbered the upgrade pack's migration to `V9` — same content, corrected header comment, no collision.

## 5. Login flow — verified live, not assumed

Per your explicit instruction not to assume this was fixed: re-checked it directly this pass.
- `api-client.ts` reads `localStorage["executionos.accessToken"]`; `use-auth-store.ts` writes to the same key. Confirmed matching, not just claimed.
- `/login` page exists, posts to the real `/api/v1/skillforge/auth/login`, stores via `setSession()`.
- `app-shell.tsx` redirects unauthenticated visits to `/login`; logout clears the store and redirects back.
- **Gap found and fixed in this pass:** there was no handling for a token that expires *mid-session* — only the "never had one" case was covered. Added a response interceptor in `api-client.ts` that clears the stale session and redirects to `/login` on any `401`.

## 6. Verified vs. not verified (full honesty, matching the standard both prior documents set)

**Verified via static analysis this pass:**
- All YAML/JSON/XML across the repo parses correctly
- Every touched Java file has balanced braces/parens
- Zero Flyway migration version collisions
- Zero-exclusion entity↔schema diff across all 17 SkillForge entity tables, including the new V9 columns — no mismatches
- The `@PreAuthorize` claim on the answer-key endpoint (independently re-derived, not just trusted)
- The candidate-facing DTO genuinely excludes `correctAnswer`
- Login flow wiring, directly re-checked rather than assumed

**Not verified — no way to in this sandbox (no network, no Maven, no live Postgres, no Docker):**
- The merged code has never been compiled (`mvn verify`)
- The question bank has never actually been imported against a real database
- No integration test has actually run (Testcontainers-based tests are still to be written — see open items below)
- No live deployment or Railway/Render run has happened with these changes

## 7. What's still open after this pass

This request also asked for: wiring the 8 mock ExecutionOS-adjacent frontend routes to real data, a Testcontainers-based integration test suite, and 25 regenerated planning documents plus a changelog. None of that is in this delivery — the merge and its verification alone was substantial enough to do carefully rather than rush alongside everything else. Continuing with those next.
