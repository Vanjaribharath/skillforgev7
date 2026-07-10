# Deployment — Option B: Render (backend) + Neon (database) + Vercel (frontend)

No servers to manage, no Docker Compose, no nginx. Each piece is a separate
managed platform with a generous free tier. Good for getting a real public
URL in under 15 minutes.

---

## 1. Database — Neon (free)

1. Go to https://neon.tech → sign up → **Create a project**.
2. Name it `skillforge`, pick a region close to where Render will run
   (see Render's region list — pick the same one).
3. Once created, go to **Connection Details** and copy the connection
   string. It looks like:
   ```
   postgresql://neondb_owner:AbCd1234@ep-cool-name-12345.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
4. Keep this tab open — you'll paste this into Render next.

## 2. Backend — Render (free tier available)

1. Push `skillforge-application/backend` to a GitHub repo (or the whole
   monorepo — Render lets you set a root directory).
2. Go to https://render.com → **New +** → **Web Service**.
3. Connect your GitHub repo.
4. Settings:
   - **Root Directory:** `backend` (if it's a monorepo; leave blank if the
     repo root is the backend itself)
   - **Runtime:** Docker (Render will use `backend/Dockerfile` automatically)
   - **Region:** same as your Neon project
   - **Instance type:** Free (fine for testing; upgrade for production)
5. Environment variables (Render → your service → **Environment**):
   ```
   DATABASE_URL=postgresql://neondb_owner:AbCd1234@ep-cool-name-12345.us-east-2.aws.neon.tech/neondb?sslmode=require
   JWT_SECRET=<generate one — see below>
   CORS_ORIGINS=https://skillforge.vercel.app
   FRONTEND_BASE_URL=https://skillforge.vercel.app
   SWAGGER_USERNAME=admin
   SWAGGER_PASSWORD=<generate one>
   SPRING_PROFILES_ACTIVE=prod
   ```
   (You'll come back and fix `CORS_ORIGINS`/`FRONTEND_BASE_URL` once you
   know your actual Vercel URL in step 3 — Render lets you edit env vars
   and it auto-redeploys.)

   Generate secrets locally before pasting in:
   ```bash
   openssl rand -hex 32
   ```
6. Click **Create Web Service**. First build takes a few minutes — Render
   builds the Docker image, then `docker-entrypoint.sh` runs Flyway
   migrations against Neon automatically on boot.
7. Once live, note your backend URL, e.g. `https://skillforge-api.onrender.com`.
8. Verify:
   ```bash
   curl https://skillforge-api.onrender.com/actuator/health
   ```

> **Free tier note:** Render's free web services spin down after 15 minutes
> of no traffic and take ~30-60 seconds to wake back up on the next request.
> Fine for a demo; upgrade to a paid instance for anything real-time.

## 3. Frontend — Vercel (free tier available)

1. Go to https://vercel.com → **Add New** → **Project** → import the same
   GitHub repo.
2. **Root Directory:** `frontend`.
3. Framework preset: Vercel auto-detects Next.js — leave defaults.
4. Environment variables (Vercel → Project → **Settings** → **Environment
   Variables**):
   ```
   NEXT_PUBLIC_API_URL=https://skillforge-api.onrender.com/api/v1
   BACKEND_API_URL=https://skillforge-api.onrender.com/api/v1
   ```
   Setting `NEXT_PUBLIC_API_URL` makes the browser call Render directly —
   simplest option. (See `RUNBOOK.md` if you'd rather route everything
   through the frontend's own proxy instead and keep the backend URL
   server-side only.)
5. Click **Deploy**. Takes 1-2 minutes.
6. Note your live URL, e.g. `https://skillforge.vercel.app`.

## 4. Close the loop: fix CORS on the backend

Now that you know your real Vercel URL, go back to Render → your backend
service → **Environment** → update:
```
CORS_ORIGINS=https://skillforge.vercel.app
FRONTEND_BASE_URL=https://skillforge.vercel.app
```
Save — Render redeploys automatically.

## 5. Bootstrap demo data and log in

```bash
curl -X POST https://skillforge-api.onrender.com/api/v1/skillforge/demo/bootstrap
```
Visit `https://skillforge.vercel.app/login` and use the one-click demo
login buttons (Admin / Trainer / Candidate) added in this pass, or:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@apex.example` | `Demo@12345` |
| Trainer | `trainer@apex.example` | `Demo@12345` |
| Candidate | `candidate@apex.example` | `Demo@12345` |

## 6. Add real email (Gmail, in ~5 minutes)

See `GMAIL_SMTP_SETUP.md` for the full walkthrough. Short version — add to
Render's environment variables:
```
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=youraddress@gmail.com
SMTP_PASSWORD=<16-character Gmail App Password>
```

## 7. Custom domain (optional)

- **Vercel:** Project → Settings → Domains → add `app.yourdomain.com`,
  follow the DNS instructions it gives you (usually a CNAME).
- **Render:** Service → Settings → Custom Domain → add `api.yourdomain.com`,
  same idea.
- Then update `CORS_ORIGINS` / `FRONTEND_BASE_URL` on Render and
  `NEXT_PUBLIC_API_URL` on Vercel to the new domains, redeploy both.

## 8. Updating after code changes

Both platforms auto-deploy on `git push` to the branch you connected —
nothing manual to run. Watch the deploy logs in each dashboard.

## 9. Option A vs Option B — which one?

| | Option A (AWS EC2 + Docker Compose) | Option B (Render + Neon + Vercel) |
|---|---|---|
| Setup time | ~45 min | ~15 min |
| Ongoing ops | You manage the box, backups, updates | Platforms handle it |
| Cost at small scale | ~$15-20/mo (t3.small) | Free tier works for a demo; paid tiers scale smoothly |
| Cold starts | None | Render free tier sleeps after 15 min idle |
| Best for | You want one place to control everything, or already run infra on AWS | You want a live URL fast with minimal maintenance |
