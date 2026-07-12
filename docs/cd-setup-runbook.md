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

## 2. Postgres database (Supabase free tier — no card required)

The app originally targeted Oracle specifically (the Flyway migrations were
Oracle-dialect). That's been dropped: both Oracle Cloud's Always Free tier
*and* Cloudflare Tunnel/Zero Trust (the self-hosting workaround considered
in between) turned out to require a credit card for identity verification,
even on their free tiers. The schema is now plain PostgreSQL
(`backend/src/main/resources/db/migration`), which opens up **Supabase** —
a managed Postgres host with a free tier confirmed to need no card at all,
and no tunnel/self-hosting gymnastics since it's reachable over the public
internet with TLS, same as Oracle ADB was originally meant to be.

**Real trade-off to know going in**: Supabase's free tier pauses a project
after 7 days with no database traffic (auto-resumes on the next connection
attempt, with a startup delay). Fine for active use; if the app might sit
idle for a week+, either accept the occasional cold-start delay or add a
low-frequency scheduled health-check ping later to keep it warm.

1. Sign up / log in at https://supabase.com — no payment method needed for
   the free tier.
2. **New Project** → pick a name and region, set a strong database
   password (this is the `postgres` superuser's password — keep it).
3. Wait for provisioning (~2 minutes), then **Project Settings → Database**
   for the connection info: host, port, database name (`postgres` by
   default), username.
4. **Use the direct connection (port 5432), not the pooled/"Transaction
   mode" connection (port 6543)** for `SPRING_DATASOURCE_URL` — Flyway runs
   its migrations through the same datasource the app uses, and Flyway's
   schema-history locking doesn't play well with transaction-mode
   connection pooling. The app's own HikariCP pool is capped small
   (`DB_POOL_MAX:5` by default in `application-prod.yml`), well under the
   free tier's direct-connection limit, so there's no need for the pooler.
5. Optional, but good practice before this goes anywhere near real
   students: in the Supabase **SQL Editor**, create a least-privilege app
   role instead of running as the `postgres` superuser day-to-day:
   ```sql
   CREATE ROLE paperdesk LOGIN PASSWORD '<a strong password>';
   GRANT ALL ON SCHEMA public TO paperdesk;
   ```

**Send back to me** (or add directly as GitHub Actions secrets — see §3):
- `SPRING_DATASOURCE_URL` (`jdbc:postgresql://<host>:5432/postgres` — the
  *direct* connection host from step 3)
- `SPRING_DATASOURCE_USERNAME` (`paperdesk` if you did step 5, else `postgres`)
- `SPRING_DATASOURCE_PASSWORD`

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
| `ANTHROPIC_API_KEY` | optional — only needed to turn on the AI trading coach ("explain this trade" on the Blotter); everything else runs fine without it, the coach just reports itself as not configured |

**Prefer setting secret values yourself directly in the GitHub UI** rather
than pasting them into chat — I don't need to see the raw values to write the
workflow that consumes them (`${{ secrets.NAME }}`).

## 4. What happens after this

Once §1–§3 are done, tell me and I'll:
1. Do a manual `wrangler deploy` (walking you through running it, since it
   needs your logged-in `wrangler` session) to validate the Worker routing
   and Container binding actually work end-to-end — this is the Phase 0
   validation spike from the CD plan, and it has to happen against a real
   account since Cloudflare Containers can't be emulated locally. This
   includes confirming the deployed container can actually reach Supabase
   (a straightforward outbound TLS connection, unlike the Oracle-Tunnel path
   this replaced — much less to go wrong here).
2. If that passes, wire up `.github/workflows/cd.yml` for automated deploys
   on every push to `main`.
3. If the Worker→Container WebSocket proxy or persistent-connection
   behavior doesn't work as expected, we fall back to the Hybrid
   architecture (backend on Fly.io/Render instead of Cloudflare Containers,
   Cloudflare kept for the frontend + DNS) — documented as the fallback in
   the CD plan. Supabase is unaffected by that fallback either way, since
   it's reached over the public internet regardless of where the backend
   container runs.
