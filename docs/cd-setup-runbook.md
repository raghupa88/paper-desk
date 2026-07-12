# CD setup runbook — accounts, credentials, secrets

One-time manual setup needed before the Cloudflare CD pipeline can deploy this
app. None of this is automatable from CI on first use (console/account-level
steps); do this once, then the pipeline in `.github/workflows/cd.yml`
(once added) handles every deploy after.

## 1. Cloudflare account + API token

1. Sign up / log in at https://dash.cloudflare.com.
2. Confirm **Workers & Containers** is available on the account (Containers is
   a newer product — check the Workers section of the dashboard; it may
   require opting into a beta or a specific plan tier).
3. Install Wrangler locally: `npm install -g wrangler` (or use `npx wrangler`
   without a global install).
4. `wrangler login` — opens a browser to authorize the CLI against your
   account. This is enough for you to run manual `wrangler deploy` commands
   yourself.
5. For GitHub Actions to deploy later, create a scoped **API token** instead
   (Dashboard → My Profile → API Tokens → Create Token): grant
   `Workers Scripts: Edit`, `Workers Routes: Edit`, `Account Settings: Read`,
   and whatever the current Containers permission is called (check the
   token-creation UI's permission list — Containers is new enough that the
   exact permission name may have changed since this was written).
6. Note your **Account ID** (Dashboard → right sidebar of any zone, or
   `wrangler whoami`).

**Send back to me** (or add directly as GitHub Actions secrets yourself —
Settings → Environments → `production` → Secrets, see §3):
- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`

## 2. Oracle database (real Oracle for prod — the Flyway migrations are
   Oracle-dialect, so this must be real Oracle, not Postgres/MySQL)

Oracle Cloud's Always Free Autonomous Database requires a credit card for
identity verification at signup, even though the tier itself is free. If
you'd rather not hand that over, self-host **Oracle Database Free (23ai)**
instead — the same image this repo already uses for local dev/test parity
(`docker-compose.yml`, `gvenzl/oracle-free:23-slim`, no Oracle account or
card needed at all) — and reach it from the Cloudflare Container over a
**Cloudflare Tunnel** rather than a public IP. This reuses the Cloudflare
account from §1 and needs nothing new signed up for.

**⚠️ UNVERIFIED end-to-end** — this wiring hasn't been tested live yet
(same caveat as the Worker↔Container spike in the CD plan). The mechanism
below (`cloudflared access tcp` run *inside* the backend container, talking
to a tunnel hosted on your machine) is a documented Cloudflare Zero Trust
pattern for reaching a private TCP service, but the exact current CLI
flags/product naming should be checked against Cloudflare's live docs at
setup time, and it must be proven to survive an idle JDBC pool (HikariCP)
for several minutes before this is trusted for real use — do that check
before wiring `cd.yml` around it.

**Real trade-off to accept going in**: the database now lives on a machine
*you* own. Production availability is capped at that machine's uptime — if
it's off or your internet drops, the app's database is unreachable. Fine
for a personal/small-classroom project; not what you'd want for anything
with real uptime expectations.

1. **Run the DB locally** (already set up): `docker compose up -d oracle`
   from the repo root. This starts Oracle Free 23ai on port 1521 and
   auto-creates the `paperdesk` app user via the `APP_USER`/
   `APP_USER_PASSWORD` env vars in `docker-compose.yml` — no ADMIN-password
   SQL Worksheet step needed, unlike Oracle Cloud ADB. Confirm it's healthy:
   `docker compose ps` should show `healthy`.
2. **Create a Cloudflare Tunnel** exposing that port *privately* (not a
   public hostname anyone can hit) — Zero Trust dashboard → Networks →
   Tunnels → Create a tunnel, then run the generated `cloudflared tunnel
   run` command on the same machine as the Oracle container, pointed at
   `tcp://localhost:1521`.
3. **Protect it with a service token**, not an open route — Zero Trust →
   Access → Service Auth → Create Service Token. This is what lets the
   backend container authenticate non-interactively (no browser login
   possible inside a container).
4. **Bundle `cloudflared` into the backend's Docker image** (small addition
   to the existing multi-stage `backend/Dockerfile`) and have the
   container's entrypoint start `cloudflared access tcp --hostname
   <your-tunnel-hostname> --url 127.0.0.1:1521 --service-token-id <id>
   --service-token-secret <secret>` as a background process *before*
   starting the JVM, so Spring's datasource URL just points at
   `jdbc:oracle:thin:@localhost:1521/FREEPDB1` (the local proxy the
   `cloudflared access` process sets up) — the JVM never talks to the
   tunnel directly.

**Send back to me** (or add directly as GitHub Actions secrets — see §3):
- `SPRING_DATASOURCE_URL` (`jdbc:oracle:thin:@localhost:1521/FREEPDB1` —
  fixed, since the JVM always talks to the local `cloudflared access` proxy)
- `SPRING_DATASOURCE_USERNAME` (`paperdesk`)
- `SPRING_DATASOURCE_PASSWORD` (the `APP_USER_PASSWORD` from
  `docker-compose.yml` — change it from the `paperdesk` placeholder before
  this goes anywhere near production)
- `CLOUDFLARE_TUNNEL_HOSTNAME` — the private hostname from step 2
- `CLOUDFLARE_SERVICE_TOKEN_ID` / `CLOUDFLARE_SERVICE_TOKEN_SECRET` — from
  step 3

## 3. GitHub Actions secrets

Add these under **Settings → Environments → `production`** (create the
Environment if it doesn't exist) — or repo-level Secrets if you'd rather not
use Environments:

| Secret | Where it comes from |
|---|---|
| `CLOUDFLARE_API_TOKEN` | §1 |
| `CLOUDFLARE_ACCOUNT_ID` | §1 |
| `SPRING_DATASOURCE_URL` | §2 |
| `SPRING_DATASOURCE_USERNAME` | §2 |
| `SPRING_DATASOURCE_PASSWORD` | §2 |
| `CLOUDFLARE_TUNNEL_HOSTNAME` | §2 |
| `CLOUDFLARE_SERVICE_TOKEN_ID` | §2 |
| `CLOUDFLARE_SERVICE_TOKEN_SECRET` | §2 |
| `PAPERDESK_JWT_SECRET` | generate yourself: `openssl rand -base64 48` — a random value, not something I need to see |
| `PAPERDESK_ALLOWED_ORIGIN` | the real public hostname once you've picked a domain, e.g. `https://paperdesk.example.com` |

**Prefer setting secret values yourself directly in the GitHub UI** rather
than pasting them into chat — I don't need to see the raw values to write the
workflow that consumes them (`${{ secrets.NAME }}`).

## 4. What happens after this

Once §1–§3 are done, tell me and I'll:
1. Do a manual `wrangler deploy` (walking you through running it, since it
   needs your logged-in `wrangler` session) to validate the Worker routing
   and Container binding actually work end-to-end — this is the Phase 0
   validation spike from the CD plan, and it has to happen against a real
   account since Cloudflare Containers can't be emulated locally. This spike
   now also needs to prove the §2 tunnel path works: the deployed container
   can reach the self-hosted Oracle DB through `cloudflared access tcp`, and
   that connection survives an idle HikariCP pool for several minutes (not
   just a single fill-and-forget query).
2. If that passes, wire up `.github/workflows/cd.yml` for automated deploys
   on every push to `main`.
3. If the Worker→Container WebSocket proxy, the persistent-connection
   behavior, or the tunnel-based DB path doesn't work as expected, we fall
   back to the Hybrid architecture (backend on Fly.io/Render instead of
   Cloudflare Containers, Cloudflare kept for the frontend + DNS) —
   documented as the fallback in the CD plan. A self-hosted-DB-behind-tunnel
   approach is compatible with that fallback too, just with the tunnel
   terminating at Fly.io/Render instead of a Cloudflare Container.
