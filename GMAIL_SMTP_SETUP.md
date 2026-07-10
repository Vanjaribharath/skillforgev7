# Email — Gmail SMTP Setup

**Short answer: yes, Gmail works fine** for this app's volume (candidate
invitations, password resets) — a personal Gmail account or Google
Workspace account both work identically here. Two things to know going in:

- Gmail **will not** accept your normal account password over SMTP anymore.
  You must create a 16-character **App Password**, which requires
  2-Step Verification to be turned on first.
- Free Gmail accounts cap out around **500 emails/day**; Workspace accounts
  around 2,000/day. Fine for invitations/resets, not for bulk email.

## 1. Turn on 2-Step Verification

1. Go to https://myaccount.google.com/security
2. Under "How you sign in to Google," turn on **2-Step Verification** (skip
   if already on).

## 2. Create an App Password

1. Go to https://myaccount.google.com/apppasswords
   (if that link 404s, search "App Passwords" in your Google Account
   settings — it only appears once 2-Step Verification is on)
2. App name: type anything, e.g. `SkillForge`.
3. Click **Create**. Google shows a **16-character password** like
   `abcd efgh ijkl mnop`. Copy it (remove the spaces when you use it).
4. This is shown once — if you lose it, delete it and generate a new one.

## 3. Set the environment variables

Wherever your backend runs (Docker Compose `.env`, Render/Railway dashboard,
etc.), set:

```env
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=youraddress@gmail.com
SMTP_PASSWORD=abcdefghijklmnop   # the 16-char App Password, no spaces
```

That's it — `application.yml` already has STARTTLS enabled for port 587,
which is exactly what Gmail's SMTP requires. No code changes needed.

## 4. Verify it's actually being used

Restart the backend after setting the variables, then trigger any email
(invite a candidate, or use "Forgot password?" on the login page). Check
the backend logs:

- **Working:** no "SMTP not configured" line appears, and the email
  actually lands in the recipient's inbox (check spam the first time).
- **Not working:** you'll still see `SMTP not configured (SMTP_HOST unset)`
  in the logs — means the env var didn't reach the container/process.
  Double check spelling and that you restarted the backend after setting it.

## 5. Common Gmail SMTP errors

| Error in logs | Cause | Fix |
|---|---|---|
| `535-5.7.8 Username and Password not accepted` | Using your real Gmail password instead of an App Password | Generate and use an App Password (Step 2) |
| `534-5.7.9 ... Please log in with your web browser` | 2-Step Verification isn't actually on, or it's a Workspace account with "less secure apps" blocked by admin policy | Turn on 2-Step Verification; if Workspace, ask your admin to allow SMTP/App Passwords |
| Email sends but always lands in spam | Gmail's own anti-spam heuristics for automated mail | Fine for a demo/internal tool; for real production volume, use Amazon SES or a transactional provider (Postmark, SendGrid) instead — same env vars, different `SMTP_HOST` |

## 6. When to move off Gmail

Gmail SMTP is genuinely fine to start with. Move to **Amazon SES**,
**Postmark**, or **SendGrid** once you need: higher volume, better
deliverability/analytics, or you're already on AWS (Option A) and want one
less external dependency. All three are drop-in — same four env vars, just
a different host/username/password.
