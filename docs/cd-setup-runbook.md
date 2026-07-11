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

## 2. Oracle Autonomous Database (real Oracle for prod — the Flyway
   migrations are Oracle-dialect, so this must be real Oracle, not Postgres/MySQL)

1. Sign up / log in at https://cloud.oracle.com (Always Free tier is enough).
2. Create an **Autonomous Database** → workload type **Transaction
   Processing** → check "Always Free". Set a strong ADMIN password (keep it
   — you'll use it once to create the app's own DB user, then never again
   day-to-day).
3. **Connectivity — check which is available on your instance before I write
   the Dockerfile's connection logic**:
   - **One-way TLS** (no wallet file needed) — if the DB's connection panel
     offers a plain `tcps://` connection string, this is simpler: no files to
     ship, just three env vars.
   - **Wallet-based mTLS** (older/default on some ADB instances) — Database
     → DB Connection → Download Wallet (a zip file). If this is the only
     option, tell me and I'll adjust the Dockerfile to bake the wallet in at
     build time from a secret instead.
4. Open the DB's **SQL Worksheet** (or connect with any SQL client using the
   ADMIN credentials) and create a least-privilege app user — don't run the
   app as ADMIN:
   ```sql
   CREATE USER paperdesk IDENTIFIED BY "<a strong password>";
   GRANT CONNECT, RESOURCE TO paperdesk;
   ALTER USER paperdesk QUOTA UNLIMITED ON DATA;
   ```
5. Get the JDBC connection string for the `paperdesk` user from the same
   connection panel (usually the `_high` or `_medium` TNS alias, e.g.
   `jdbc:oracle:thin:@mydb_high?TNS_ADMIN=...` for wallet-based, or
   `jdbc:oracle:thin:@tcps://host:1522/service_name` for one-way TLS).

**Send back to me** (or add directly as GitHub Actions secrets — see §3):
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME` (`paperdesk`, not `ADMIN`)
- `SPRING_DATASOURCE_PASSWORD`
- the wallet zip, base64-encoded, **only if** one-way TLS isn't available

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
| `PAPERDESK_JWT_SECRET` | generate yourself: `openssl rand -base64 48` — a random value, not something I need to see |
| `PAPERDESK_ALLOWED_ORIGIN` | the real public hostname once you've picked a domain, e.g. `https://paperdesk.example.com` |
| `ORACLE_WALLET_B64` | only if wallet-based mTLS is required (§2) |

**Prefer setting secret values yourself directly in the GitHub UI** rather
than pasting them into chat — I don't need to see the raw values to write the
workflow that consumes them (`${{ secrets.NAME }}`).

## 4. What happens after this

Once §1–§3 are done, tell me and I'll:
1. Do a manual `wrangler deploy` (walking you through running it, since it
   needs your logged-in `wrangler` session) to validate the Worker routing
   and Container binding actually work end-to-end — this is the Phase 0
   validation spike from the CD plan, and it has to happen against a real
   account since Cloudflare Containers can't be emulated locally.
2. If that passes, wire up `.github/workflows/cd.yml` for automated deploys
   on every push to `main`.
3. If the Worker→Container WebSocket proxy or persistent-connection behavior
   doesn't work as expected, we fall back to the Hybrid architecture (backend
   on Fly.io/Render instead of Cloudflare Containers, Cloudflare kept for the
   frontend + DNS) — documented as the fallback in the CD plan.
