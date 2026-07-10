# AWS Deployment — Option A: Single EC2 Instance + Docker Compose

This is the simplest AWS path: one EC2 box running the same
`docker compose` stack you already run locally, fronted by nginx (bundled),
with TLS added via Caddy or Certbot. Good for internal/enterprise use like
this app's actual audience (Apex Learning Cloud-style single tenant or a
handful of orgs). Every command is copy-paste runnable.

---

## 1. Create the EC2 instance

1. AWS Console → EC2 → **Launch instance**.
2. Name: `skillforge-prod`.
3. AMI: **Ubuntu Server 24.04 LTS**.
4. Instance type: **t3.small** minimum (2 vCPU/2GB) for Docker + Postgres +
   Spring Boot + Next.js all on one box; **t3.medium** if you expect real
   concurrent load.
5. Key pair: create a new one (e.g. `skillforge-key`), download the `.pem`,
   `chmod 400 skillforge-key.pem`.
6. Network settings → create a new security group `skillforge-sg` with:
   - SSH (22) — Source: **My IP** (not 0.0.0.0/0)
   - HTTP (80) — Source: Anywhere (0.0.0.0/0)
   - HTTPS (443) — Source: Anywhere (0.0.0.0/0)
7. Storage: 20 GB gp3 minimum.
8. Launch instance.

## 2. Point a domain at it

1. Note the instance's **public IPv4 address** (or allocate + associate an
   **Elastic IP** so it doesn't change on reboot — EC2 → Elastic IPs →
   Allocate → Associate with your instance).
2. In your DNS provider (Route 53 or wherever your domain is), create an
   **A record**: `app.yourdomain.com` → the Elastic IP.

## 3. SSH in and install Docker

```bash
ssh -i skillforge-key.pem ubuntu@app.yourdomain.com
```

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
sudo apt-get install -y docker-compose-plugin
docker --version
docker compose version
```

## 4. Get the code onto the instance

Easiest is to zip-upload from your machine:

```bash
# on your local machine, from wherever you extracted the deliverable
scp -i skillforge-key.pem -r skillforge-application ubuntu@app.yourdomain.com:~/
```

Or, if you push this to a private GitHub repo instead:
```bash
# on the EC2 instance
git clone https://github.com/yourorg/skillforge-application.git
cd skillforge-application
```

## 5. Configure environment variables

```bash
cd ~/skillforge-application/docker
nano .env
```
Paste, replacing every placeholder:
```env
POSTGRES_PASSWORD=REPLACE_ME
JWT_SECRET=REPLACE_ME
CORS_ORIGINS=https://app.yourdomain.com
FRONTEND_BASE_URL=https://app.yourdomain.com
SWAGGER_USERNAME=admin
SWAGGER_PASSWORD=REPLACE_ME
```
Generate strong values for each `REPLACE_ME`:
```bash
openssl rand -hex 32
```
Run that three times and paste one output into `POSTGRES_PASSWORD`, one into
`JWT_SECRET`, one into `SWAGGER_PASSWORD`. Save and exit (`Ctrl+O`, `Enter`, `Ctrl+X`).

## 6. Add TLS (HTTPS) in front of the bundled nginx

The bundled `docker/nginx/default.conf` is HTTP-only by design (see
`RUNBOOK.md`). The simplest way to add TLS on a single EC2 box is to put
**Caddy** in front of it as a second, tiny reverse proxy — it gets you free
auto-renewing Let's Encrypt certificates with almost no config.

```bash
sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt-get update && sudo apt-get install -y caddy
```

Edit Caddy's config to reverse-proxy to the Docker stack's nginx (which we'll
bind to `127.0.0.1:8081` instead of the public port 80, so Caddy owns 80/443):

```bash
sudo tee /etc/caddy/Caddyfile > /dev/null << 'EOF'
app.yourdomain.com {
    reverse_proxy 127.0.0.1:8081
}
EOF
sudo systemctl restart caddy
```

Now change the compose file's nginx port mapping so it only listens on
localhost:8081 instead of the public 0.0.0.0:80:

```bash
cd ~/skillforge-application/docker
```
Open `docker-compose.yml`, find the `nginx` service's `ports:` section, and
change `"80:80"` to `"127.0.0.1:8081:80"`.

## 7. Start the stack

```bash
cd ~/skillforge-application/docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose ps
```
Wait ~60 seconds for health checks to go green (Postgres → backend runs
Flyway migrations → frontend → nginx).

```bash
docker compose logs -f backend   # watch for "Started ExecutionOsApplication"
```

## 8. Bootstrap demo data and verify

```bash
curl -X POST https://app.yourdomain.com/api/skillforge/demo/bootstrap
```
Then visit `https://app.yourdomain.com/login` in a browser and sign in with
`admin@apex.example` / `Demo@12345`. For a real deployment, skip the demo
bootstrap and instead register your real organization via
`POST /api/v1/skillforge/organizations` (see `SkillForgeController.java`).

## 9. Keep it running / operate it

**View logs:**
```bash
docker compose logs -f backend
docker compose logs -f frontend
```

**Retrieve an invitation token when SMTP isn't configured yet** (see the
"how do candidates get a token" fix in this pass):
```bash
docker compose logs backend | grep "SMTP not configured" -A 5
```

**Update to a new version:**
```bash
cd ~/skillforge-application
git pull                     # or re-scp the updated folder
cd docker
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

**Back up the database:**
```bash
docker compose exec postgres pg_dump -U executionos executionos > backup-$(date +%F).sql
```

**Restart everything:**
```bash
docker compose restart
```

**Stop everything (keeps data):**
```bash
docker compose down
```

## 10. Recommended next steps for a real production org

- Move Postgres off the EC2 box onto **Amazon RDS for PostgreSQL** (better
  backups, multi-AZ failover) — point `DATABASE_URL` at the RDS endpoint
  instead of the local `postgres` container, and remove the `postgres`
  service from `docker-compose.yml`.
- Configure real SMTP (`SMTP_HOST`/`SMTP_USERNAME`/`SMTP_PASSWORD`) via
  **Amazon SES** so candidate invitations actually deliver by email instead
  of only logging.
- Put **CloudWatch Agent** or the bundled Prometheus/Grafana (already in
  `docker-compose.yml`) behind their own subdomain with auth, for
  monitoring.
- Take an EC2 AMI snapshot periodically, or move to an Auto Scaling Group
  with a load balancer once you outgrow a single instance.
