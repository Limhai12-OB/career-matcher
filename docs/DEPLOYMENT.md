# Deploying AI Career Matcher

This guide covers a single-host production deployment with Docker Compose. The production
configuration was rehearsed end to end on a local machine (see [Verification](#verification)); it
has not yet run on a public server.

## 1. Architecture in production

```
            Internet
               │  80/443 (only published ports)
        ┌──────▼──────┐   network "edge" (internet: clients, ACME)
        │    Caddy    │   automatic HTTPS, HSTS, blocks /actuator and OpenAPI
        └──┬───────┬──┘
           │       │      network "public" (internal: no internet)
   ┌───────▼──┐ ┌──▼────────┐
   │ frontend │ │  backend  │──── network "internal" (no internet) ──── ai-service ── "egress" ──► Anthropic API
   └──────────┘ └──┬─────┬──┘
                   │     │        network "data" (no internet)
            ┌──────▼─┐ ┌─▼───────┐
            │postgres│ │ storage │  S3 gateway only; filer/master/volume bound to loopback
            └────────┘ └─────────┘
```

- `APP_DOMAIN` (e.g. `app.example.org`) serves the frontend; `API_DOMAIN` (e.g.
  `api.example.org`) serves the REST API.
- Only Caddy publishes ports. No database, storage, AI or app port is reachable from outside.
- Only the AI service (to reach Anthropic) and Caddy (clients, certificates) have internet access.
- App containers run with read-only root filesystems, all Linux capabilities dropped,
  `no-new-privileges`, memory limits and rotated logs (`docker-compose.prod.yml`).

## 2. HTTPS requirements

HTTPS is mandatory: refresh cookies are `Secure`, and the backend refuses to start in production
otherwise.

1. **Two DNS names on the same registrable domain.** For example `app.example.org` and
   `api.example.org`. The refresh cookie is `SameSite=Strict`, so the browser only sends it when
   the frontend and API are the *same site*. Separate domains (`example-app.com` +
   `example-api.com`) or hosts under a public-suffix domain (e.g. `*.herokuapp.com`,
   `*.vercel.app`) break sign-in persistence. (The local rehearsal hit exactly this with
   `app.localhost` + `api.localhost`, which browsers treat as different sites.)
2. **DNS A/AAAA records** for both names point at the server before the first start.
3. **Ports 80 and 443 (TCP, plus UDP 443 for HTTP/3)** are open to the internet. Port 80 is
   needed for the ACME HTTP challenge and redirects to HTTPS.
4. **Certificates** are obtained and renewed automatically by Caddy (Let's Encrypt, with ZeroSSL
   as fallback). Set `CADDY_GLOBAL_OPTIONS=email you@example.org` for expiry notices. The
   `caddy-data` volume holds the certificates and ACME account; keep it.
5. **HSTS** (`max-age=31536000; includeSubDomains`) is sent on both names. Only enable this on a
   domain where every subdomain is served over HTTPS.
6. **Using another proxy or load balancer instead of Caddy** (e.g. a cloud load balancer):
   terminate TLS there, forward to `frontend:3000` and `backend:8080`, **replace** (not append)
   `X-Forwarded-For` with the client address, set `X-Forwarded-Proto: https`, block `/actuator`,
   `/v3/api-docs` and `/swagger-ui` on the API host, and limit request bodies to about 8 MB. The
   backend trusts `X-Forwarded-*` only from private-network addresses
   (`FORWARD_HEADERS_STRATEGY=native`).

## 3. Server prerequisites

- Linux host, Docker Engine 27+ and Docker Compose v2.24+ (for `!reset`). 4 GB RAM minimum.
- A non-root deploy user in the `docker` group; SSH key login only; host firewall allowing 22,
  80 and 443 only.
- Off-host backup storage for the database dump, the CV storage volume and the secrets.

## 4. Configuration

```bash
cp .env.production.example .env.production
chmod 600 .env.production
```

Fill in every `CHANGE` value. All variables, their purpose and how to generate secrets are
documented inline in [`.env.production.example`](../.env.production.example). The most important:

| Variable | Notes |
|---|---|
| `APP_ENV=production` | Enables startup checks in the backend and AI service (below). |
| `APP_DOMAIN`, `API_DOMAIN` | Same registrable domain (see HTTPS requirements). |
| `POSTGRES_PASSWORD`, `JWT_SECRET`, `AI_INTERNAL_TOKEN`, `STORAGE_*` | Random; `openssl rand -hex 32`. |
| `CV_ENCRYPTION_KEY` | `openssl rand -base64 32`. **Back it up separately from the data.** Losing it makes every stored CV unreadable; leaking it together with a storage backup exposes them. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | First admin only (≥ 16 characters in production). Remove the password after the first sign-in. |
| `AI_PROVIDER`, `ANTHROPIC_API_KEY` | `anthropic` for full CV analysis. `local` runs a basic, offline mode (skills and education only). |
| `JOBS_DEMO_DATA_ENABLED` | `false` for a real service. Demo listings are fictional (clearly labelled). |

`docker-compose.prod.yml` forces the security-relevant settings regardless of the env file:
`COOKIE_SECURE=true`, `OPENAPI_ENABLED=false`, `FORWARD_HEADERS_STRATEGY=native`,
`CORS_ALLOWED_ORIGINS=https://$APP_DOMAIN`, `AI_DOCS_ENABLED=false`.

### Startup safety checks

With `APP_ENV=production` the services **refuse to start** when:

- **Backend** (`ProductionGuard`): `COOKIE_SECURE` is not true; OpenAPI/Swagger is enabled; a CORS
  origin is not `https://`, contains `*`, or is a localhost address; rate limiting is disabled;
  `FORWARD_HEADERS_STRATEGY` is `none`; `ADMIN_PASSWORD` is set but shorter than 16 characters.
  It logs a warning when demo data is enabled.
- **AI service**: API docs are enabled; log level is `DEBUG`; `AI_PROVIDER=anthropic` without an
  API key. It logs a warning when `AI_PROVIDER=local`.

Configuration errors never echo secret values into logs.

## 5. First deployment

```bash
git clone <repository> career-matcher && cd career-matcher
git checkout <release tag>
cp .env.production.example .env.production   # fill in, chmod 600

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.production"
$COMPOSE config -q                 # validates the files and required variables
$COMPOSE up -d --build
$COMPOSE ps                        # all services healthy; only caddy publishes ports
$COMPOSE logs backend | grep -E "Successfully applied|Production configuration"
```

Then:

1. Open `https://$APP_DOMAIN`, sign in as the admin, change the password in **Settings**.
2. Remove `ADMIN_PASSWORD` from `.env.production` and run `$COMPOSE up -d backend`.
3. Add real job listings in **Manage jobs** (source and application URL required).
4. Run the smoke checks in [section 8](#8-post-deployment-smoke-checks).

## 6. Database migrations

Schema changes are versioned Flyway migrations in `backend/src/main/resources/db/migration`
(`V1__…` to `V8__…`). They run **automatically when the backend starts**, inside a transaction
per migration, with a database lock so that concurrent starts can't apply them twice. Hibernate
only validates the schema (`ddl-auto: validate`); it never changes it.

**Rules**

- Never edit or delete a migration that has been deployed. Flyway checksums every applied file
  (`validate-on-migrate`), and the backend will refuse to start if one changed. Fix forward with a
  new `V<n+1>__…sql`.
- Write migrations that the *previous* release can still run against (add columns as nullable or
  with defaults; drop columns one release after the code stops using them). This keeps rollbacks
  possible.
- Migrations are tested: every backend integration test run applies V1–V8 to an empty
  PostgreSQL 18 database, and `BackendApplicationTests` asserts the applied versions.

**Upgrading a running system**

```bash
# 1. Back up (database + CV storage + .env.production) — see section 7
# 2. Fetch the release
git fetch --tags && git checkout <new release tag>
# 3. Rebuild and restart; the backend migrates on startup
$COMPOSE up -d --build
# 4. Confirm
$COMPOSE logs backend | grep -E "Migrating schema|Successfully applied|Schema .* is up to date"
$COMPOSE ps
```

If a migration fails, the backend does not start (the previous containers are already replaced,
so the API is down) and the failed migration is rolled back. Read the error with
`$COMPOSE logs backend`, then either deploy a fixed release or roll back:

```bash
git checkout <previous release tag>
$COMPOSE up -d --build      # works if the failed migration was rolled back (PostgreSQL DDL is transactional)
# If a migration had committed and the old code can't run against the new schema, restore the backup (section 7).
```

**Inspecting migration state**

```bash
$COMPOSE exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "select version, description, success, installed_on from flyway_schema_history order by installed_rank"'
```

**Network changes:** changing a network's `internal` setting in the compose files requires
`$COMPOSE down` then `$COMPOSE up -d` (volumes are kept). A partial recreate left containers
unable to resolve each other in the rehearsal.

## 7. Backups and restore

Back up all three together, at least daily, to off-host storage:

```bash
TS=$(date +%Y%m%d-%H%M)
# Database (logical dump, consistent)
$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > db-$TS.dump
# CV files (already AES-256-GCM encrypted). Stop storage briefly for a consistent copy.
$COMPOSE stop storage
docker run --rm -v career-matcher_cv-storage:/data:ro -v "$PWD":/backup alpine:3 \
  tar czf /backup/cv-storage-$TS.tgz -C /data .
$COMPOSE start storage
# Secrets: .env.production (store separately, e.g. in a password manager / secret store)
```

Restore (on a stopped stack):

```bash
$COMPOSE stop backend
$COMPOSE exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
$COMPOSE exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < db-<TS>.dump
$COMPOSE stop storage
docker run --rm -v career-matcher_cv-storage:/data -v "$PWD":/backup alpine:3 \
  sh -c 'rm -rf /data/* && tar xzf /backup/cv-storage-<TS>.tgz -C /data'
$COMPOSE up -d
```

The volume prefix (`career-matcher_`) is the compose project name. Test a restore before go-live.

## 8. Post-deployment smoke checks

```bash
APP=https://app.example.org; API=https://api.example.org
curl -sI http://app.example.org | head -1                       # 308 redirect to https
curl -s -o /dev/null -w '%{http_code}\n' $APP/login             # 200
curl -s -o /dev/null -w '%{http_code}\n' "$API/api/v1/jobs"     # 401 (auth required)
curl -s -o /dev/null -w '%{http_code}\n' $API/actuator/health   # 404 (blocked at the edge)
curl -s -o /dev/null -w '%{http_code}\n' $API/v3/api-docs       # 404
curl -sI $APP/login | grep -i strict-transport-security          # present
curl -s -o /dev/null -w '%{http_code}\n' -X OPTIONS $API/api/v1/auth/login \
  -H "Origin: https://evil.example" -H "Access-Control-Request-Method: POST"   # 403
```

Then in a browser: register a candidate, upload a CV, confirm skills, open Recommendations and
Skill gaps, reload the page (session must persist), sign out.

Internal health (not exposed publicly): `$COMPOSE ps` shows each container's health;
`$COMPOSE exec backend curl -s localhost:8080/actuator/health` shows `db`, `aiService` and
`storage` status.

## 9. Operations

- **Logs:** `$COMPOSE logs -f backend ai-service`. Logs contain request ids and metadata only:
  never CV text, passwords, tokens or keys. Docker rotates them (10 MB × 5 per container).
- **Rate limits and sessions are in memory** (single backend instance). Restarting the backend
  resets rate-limit counters; it does not sign users out (refresh tokens are in the database).
  Running more than one backend replica needs a shared rate-limit store first.
- **Secret rotation:** `JWT_SECRET` (signs everyone out at their next refresh… access tokens
  expire within 15 minutes); `AI_INTERNAL_TOKEN` and `STORAGE_*` (restart the services together);
  `CV_ENCRYPTION_KEY` **cannot** be rotated without re-encrypting stored files (not implemented).
- **Updating images:** base images are pinned (PostgreSQL 18, SeaweedFS 4.54, Caddy 2.10.2,
  Temurin 21, Node 24, Python 3.13). Rebuild monthly for security patches: `$COMPOSE build --pull`.

## 10. Final deployment checklist

**Before the first deployment**

- [ ] DNS for `APP_DOMAIN` and `API_DOMAIN` (same registrable domain) points at the host.
- [ ] Firewall allows only 22, 80, 443 (TCP) and 443 (UDP).
- [ ] `.env.production` created from the template, every `CHANGE` replaced, `chmod 600`, not in git.
- [ ] Secrets generated randomly; `CV_ENCRYPTION_KEY` and `.env.production` backed up to a secret
      store separate from data backups.
- [ ] `AI_PROVIDER=anthropic` with a valid `ANTHROPIC_API_KEY` (or `local` deliberately, accepting
      basic mode). Anthropic usage limits / billing alerts configured.
- [ ] `JOBS_DEMO_DATA_ENABLED=false` (unless this is a demo).
- [ ] Privacy notice and terms published; CV retention and deletion policy decided (users can
      delete CVs and their account; deletion removes stored files).
- [ ] `$COMPOSE config -q` passes.

**Deployment**

- [ ] `$COMPOSE up -d --build`; all services healthy; only Caddy publishes ports (`$COMPOSE ps`).
- [ ] Backend log shows all migrations applied and no `Unsafe production configuration` error.
- [ ] Smoke checks in section 8 pass.
- [ ] Admin password changed and `ADMIN_PASSWORD` removed from the env file; backend restarted.
- [ ] Real job listings entered with source and application URLs.

**After go-live**

- [ ] Automated daily backups (database + CV storage volume) to off-host storage; one restore tested.
- [ ] External uptime monitoring on `https://$APP_DOMAIN/login` and certificate expiry alerts.
- [ ] Monthly image rebuild with `--pull`; dependency updates reviewed.

## Verification

The production configuration was rehearsed locally on 2026-11-21 with `docker-compose.prod.yml`
on fresh volumes. Rehearsal-only differences: `*.cm.localhost` domains with Caddy's local CA
(`CADDY_GLOBAL_OPTIONS=local_certs`), `ALLOW_LOCAL_ORIGINS=true`, `AI_PROVIDER=local` (no API key),
demo data enabled, raised per-IP limits for the test runner. Verified results:

- V1–V8 migrated an empty database; all services healthy; only Caddy published ports.
- TLS chain valid against the CA; HTTP redirects to HTTPS; HSTS and security headers present;
  `/actuator/*`, `/v3/api-docs` and Swagger return 404 at the edge; CORS allows only the app
  origin.
- Backend and frontend have no internet access; the AI service does.
- Per-IP rate limits apply behind the proxy and are not bypassed by spoofed `X-Forwarded-For`.
- Unsafe settings (`COOKIE_SECURE=false`, `OPENAPI_ENABLED=true`, `AI_DOCS_ENABLED=true`) stop the
  services at startup.
- The full Playwright suite (21 tests) passed against the HTTPS stack on the final build: twice
  immediately after a fresh install (empty volumes) and twice warm, with no unhandled exceptions
  in the backend log. It covers registration, onboarding, CV upload/analysis (PDF, DOCX, empty,
  injection), recommendations, skill gaps, admin job management and account deletion. An earlier
  intermittent first-run CV failure was traced to a race when creating the storage bucket on a
  fresh installation, fixed, and covered by a regression test.
- The backup and restore commands in section 7 were executed: a CV deleted after the backup was
  restored and downloaded byte-for-byte identical.

Not verified: a real public deployment with Let's Encrypt certificates, and the Claude provider
with a real API key.
