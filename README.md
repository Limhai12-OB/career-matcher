# AI Career Matcher

AI-powered career guidance for university students, fresh graduates and entry-level IT job seekers.
It analyzes CVs, extracts skills, recommends entry-level IT jobs with deterministic compatibility
scores, and explains skill gaps.

> **Status:** Milestones 1-4 are complete. Candidates can register, onboard, manage their profile,
> analyze their CV and confirm skills, and search entry-level IT jobs. Admins manage job listings.
> Job matching arrives in later milestones; see
> [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md).

## Architecture

```
Browser ──▶ frontend (Next.js :3000) ──▶ backend (Spring Boot :8080) ──▶ PostgreSQL
                                                    │                 └─▶ storage (SeaweedFS, S3 API; encrypted CVs)
                                                    └──(internal, shared token)──▶ ai-service (FastAPI :8000) ──▶ Claude API
```

| App | Stack | Responsibility |
|---|---|---|
| `frontend/` | Next.js 16 (App Router), TypeScript, Tailwind CSS 4, shadcn/ui, TanStack Query, React Hook Form + Zod, Vitest, Playwright | UI. Talks to the backend over REST only. |
| `backend/` | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway, PostgreSQL 18, springdoc | Auth, business logic, persistence, deterministic matching. Modular monolith. |
| `ai-service/` | Python 3.13, FastAPI, Pydantic, pypdf, python-docx, Anthropic SDK | Sandboxed document parsing and LLM extraction with grounding checks. Reachable only from the backend. |
| `storage` | SeaweedFS 4.54 (S3-compatible) | Private object storage for CV files; no published ports. |

Docker networks enforce the boundaries:
- `data`: backend ↔ postgres and object storage.
- `internal`: backend ↔ ai-service.
- `public`: frontend ↔ backend.

The frontend container cannot reach Postgres or the AI service.

## Repository layout

```
backend/        Spring Boot app (Maven wrapper; no global Maven needed)
  src/main/java/com/careermatcher/
    common/     cross-cutting: config, security (JWT), rate limiting, errors, validation
    user/       user accounts (entity, repository, read API for other modules)
    auth/       registration, login, refresh-token rotation, logout, password change, deletion
    profile/    candidate profile, career preferences, onboarding
    skill/      canonical skill catalog, normalizer, candidate (confirmed) skills
    cv/         CV upload, async analysis worker, history, confirmation, deletion
    job/        job listings: search/filter/pagination, admin CRUD, demo dataset seeder
    storage/    S3 client with AES-256-GCM client-side encryption
    aiclient/   typed client + health indicator for the AI service
  src/main/resources/db/migration/   Flyway migrations (V{n}__{description}.sql)
ai-service/     FastAPI app
  app/documents/  type sniffing, sandboxed PDF/DOCX text extraction, text cleaning, injection screening
  app/analysis/   schemas, prompt, providers (anthropic, local), grounding checks, orchestration
infra/seaweedfs/  storage start-up script
frontend/       Next.js app
  src/app/        routes: (auth) login/register, (onboarding), (app) dashboard/profile/preferences/settings
  src/components/ ui/ (shadcn), forms/, auth/ guards, shell/
  src/lib/        api/ client + hooks, auth/ session store, validation/ zod schemas
  e2e/            Playwright end-to-end tests
docs/           implementation plan and design notes
docker-compose.yml
.env.example    every configuration variable, documented
```

## Prerequisites

- Docker Desktop with Compose v2+. This is all you need to run the stack.
- For local development outside Docker:
  - JDK 21
  - Node.js 24 with pnpm (`corepack enable`)
  - Python 3.12 or 3.13

## Quick start (Docker)

```bash
cp .env.example .env
# Replace every "change-me" value, e.g.:
#   POSTGRES_PASSWORD=$(openssl rand -hex 16)
#   AI_INTERNAL_TOKEN=$(openssl rand -hex 32)   # min. 32 chars
#   JWT_SECRET=$(openssl rand -hex 32)
#   STORAGE_ACCESS_KEY=$(openssl rand -hex 10); STORAGE_SECRET_KEY=$(openssl rand -hex 24)
#   CV_ENCRYPTION_KEY=$(openssl rand -base64 32)
# For real CV analysis set AI_PROVIDER=anthropic and ANTHROPIC_API_KEY.

docker compose up -d --build
docker compose ps        # all five services should become "healthy"
```

| Service | URL | Notes |
|---|---|---|
| Frontend | http://localhost:3000 | `/status` shows live health of all services |
| Backend API | http://localhost:8080 | |
| Swagger UI | http://localhost:8080/swagger-ui.html | disable with `OPENAPI_ENABLED=false` |
| AI service | http://127.0.0.1:8000 | loopback only, for debugging; `/docs` when `AI_DOCS_ENABLED=true` |
| PostgreSQL | 127.0.0.1:5432 | loopback only |

To stop: `docker compose down`. To also wipe the database, run `docker compose down -v`.

### Health checks

| Endpoint | Meaning |
|---|---|
| `GET backend /actuator/health/liveness` | Backend process is alive |
| `GET backend /actuator/health/readiness` | Backend can serve traffic (includes database). Used by the Docker healthcheck. |
| `GET backend /actuator/health/dependencies` | Database, object storage **and** AI service (including authentication with the shared internal token) |
| `GET backend /actuator/health` | Aggregate of all components. Reports `DOWN` if the AI service is down. |
| `GET ai-service /health`, `/health/ready` | AI service liveness and readiness (public within the network) |
| `GET ai-service /v1/status` | Token-protected status; requires the `X-Internal-Token` header |
| `GET frontend /api/health` | Frontend liveness (independent of the backend) |

The AI service is intentionally **not** part of backend readiness. If it is down, non-AI features keep
working and `/actuator/health/dependencies` reports the outage.

```bash
curl -s localhost:8080/actuator/health/dependencies
# {"components":{"aiService":{"status":"UP"},"db":{"status":"UP"}},"status":"UP"}
```

## Authentication and security model

| Concern | Implementation |
|---|---|
| Passwords | Argon2id (Spring Security, `{argon2}` delegating format). Policy: 10-128 chars, at least one letter and one number. |
| Access token | JWT (HS256, `JWT_SECRET`), 15 min, claims: `sub` (user id), `role`, `ver` (token version). Held **only in memory** by the frontend and sent as `Authorization: Bearer`. |
| Refresh token | 256-bit random opaque value, stored **hashed** (SHA-256). Sent as `cm_refresh` cookie: `HttpOnly; SameSite=Strict; Path=/api/v1/auth`, `Secure` when `COOKIE_SECURE=true`. 14-day lifetime. |
| Rotation & theft detection | Every refresh rotates the token. Presenting an already-rotated token revokes the whole session family. A 10 s grace window tolerates concurrent refreshes from several tabs. |
| Immediate revocation | Each request re-checks the user in the database: deleted or disabled accounts, and tokens whose `ver` is older than the user's (bumped on password change), are rejected at once. |
| Authorization | Deny by default. Candidate data lives under `/api/v1/me/**` and is always scoped to the user id from the verified token. No endpoint accepts a user id, so cross-user access is impossible by construction (see `OwnershipIsolationTests`). `/api/v1/admin/**` requires `ROLE_ADMIN`. |
| CSRF | API calls authenticate with the Authorization header, not cookies. The refresh/logout cookie endpoints are protected by `SameSite=Strict`, CORS and an `Origin` allow-list check. |
| Rate limiting | Per IP: login 20/min, registration 10/h, refresh 60/min. Per account: 10 login attempts per 15 min. Per user: 5 password changes or deletions per 15 min. In-memory (single instance); returns `429` with `Retry-After`. |
| Account deletion | `DELETE /api/v1/me` (password confirmation) hard-deletes the user; profile, preferences and sessions cascade in the database. |
| Browser hardening | CSP (`connect-src` limited to the API origin), `X-Frame-Options: DENY`, `nosniff`, a strict referrer policy. Post-login redirects accept only same-origin relative paths. |

The frontend and the API must be **same-site** (for example `app.example.com` and
`api.example.com`, or both on `localhost`) so that the `SameSite=Strict` refresh cookie is sent.

### API overview

All endpoints are documented in Swagger UI (`/swagger-ui.html`). Errors are
`application/problem+json` with a stable `code` (e.g. `VALIDATION_FAILED` with an `errors[]`
array, `INVALID_CREDENTIALS`, `EMAIL_ALREADY_REGISTERED`, `ONBOARDING_INCOMPLETE`, `RATE_LIMITED`).

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | – | Create a candidate account; returns an access token and sets the refresh cookie |
| POST | `/api/v1/auth/login` | – | Sign in |
| POST | `/api/v1/auth/refresh` | cookie | Rotate the refresh cookie and get a new access token |
| POST | `/api/v1/auth/logout` | cookie | Revoke the current session |
| GET | `/api/v1/auth/me` | bearer | Current user |
| GET/PUT | `/api/v1/me/profile` | bearer | Profile (with completeness) |
| GET/PUT | `/api/v1/me/preferences` | bearer | Career preferences |
| GET | `/api/v1/me/onboarding` | bearer | Onboarding status and missing required fields |
| POST | `/api/v1/me/onboarding/complete` | bearer | Finish onboarding (`422` if required fields are missing) |
| POST | `/api/v1/me/password` | bearer | Change password (signs out other sessions) |
| DELETE | `/api/v1/me` | bearer | Delete account and all data (including stored CV files) |
| POST | `/api/v1/me/cvs` | bearer | Upload a CV (multipart `file`); returns `202` and a pending analysis |
| GET | `/api/v1/me/cvs` | bearer | My CVs with their analysis history |
| GET | `/api/v1/me/cvs/{id}/file` | bearer | Download my original file |
| POST | `/api/v1/me/cvs/{id}/analyses` | bearer | Analyze an existing CV again (history kept) |
| DELETE | `/api/v1/me/cvs/{id}` | bearer | Delete a CV, its analyses and stored file |
| GET | `/api/v1/me/cv-analyses` | bearer | Paged analysis history |
| GET | `/api/v1/me/cv-analyses/{id}` | bearer | Analysis status and evidence-backed results (poll while `PENDING`/`PROCESSING`) |
| POST | `/api/v1/me/cv-analyses/{id}/apply` | bearer | Confirm selected recognized skills into my profile |
| GET | `/api/v1/me/skills` | bearer | My confirmed skills |
| GET | `/api/v1/skills?q=` | bearer | Search the canonical skill catalog (names and aliases) |
| GET | `/api/v1/jobs` | bearer | Search published jobs: `q`, `country`, `remote`, `type`, `seniority`, `skill` (repeatable slug), `origin`, `page`, `size` (≤ 50) |
| GET | `/api/v1/jobs/{id}` | bearer | Published job detail (404 for drafts, closed or expired jobs) |
| GET/POST | `/api/v1/admin/jobs` | admin | List all jobs (`q`, `status`, `origin`) / create a draft |
| GET/PUT/DELETE | `/api/v1/admin/jobs/{id}` | admin | Read / replace / delete a job |
| PATCH | `/api/v1/admin/jobs/{id}/status` | admin | `PUBLISHED`, `CLOSED`, or `DRAFT` (only if never published) |
| DELETE | `/api/v1/me/skills/{skillId}` | bearer | Remove a skill from my profile |

## Jobs

Candidates search published, unexpired listings by keyword (title, company, description), country,
work arrangement, employment type, seniority, skill and listing type, newest first, 12 per page.
Filters live in the URL. Each job lists required and preferred catalog skills; the detail page marks
the skills already confirmed in the candidate's profile.

### Real vs demo listings

Every listing has a mandatory `dataOrigin`, enforced at every layer:

| | `REAL` | `DEMO` |
|---|---|---|
| Meaning | A genuine, currently open position, verified and entered by an admin | Fictional sample data for development and demos |
| Application/source URL | Must be the employer's or job board's real page (https; reserved example domains rejected) | Must be on reserved `example.com/.org/.net` domains (API **and** DB constraint), so it can never send anyone to a real site |
| UI | "Real opportunity" badge; "Apply on employer site" opens the link in a new tab (`noopener noreferrer`) | "Demo listing" badge, a banner on the list and detail pages, and a disabled "no real application" button |

Every listing also shows its **source** (name and optional URL) and its application link.

**Initial dataset:** `backend/src/main/resources/demo/demo-jobs.json` holds 20 curated, clearly
fictional entry-level IT listings (internships, junior/graduate roles in backend, frontend, mobile,
QA, data, DevOps, IT support) across Cambodia, Singapore, Thailand, Vietnam, Malaysia, the
Philippines and Germany. Companies are suffixed "(Demo)", descriptions state they are not real
openings, and links use `careers.example.com`. The dataset loads only when
`JOBS_DEMO_DATA_ENABLED=true` and is idempotent. **No real openings are bundled**: they must be
verified and entered by an admin, because the platform must never present unverified or invented
opportunities as real.

### Administration

- The first admin is created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` if that e-mail doesn't exist yet. Existing accounts are never promoted; the password needs 12+ characters with a letter and a digit. Change it after the first sign-in and remove it from `.env`.
- Admins see **Manage jobs** (`/admin/jobs`): search and filter by status and origin, create (saved as `DRAFT`), edit, publish, close, delete. A published job can't go back to draft. Admins have no candidate profile and are kept out of candidate pages; candidates are kept out of `/admin`, and every `/api/v1/admin/**` call requires `ROLE_ADMIN`.
- Validation: https URLs only; source required; ISO country and currency; salary min ≤ max with currency and period; experience min ≤ max; posting date not in the future; expiry not before posting; 1-30 unique catalog skills with at least one required; plain-text description (50-20,000 chars, never rendered as HTML).

## CV analysis pipeline

```
upload ─▶ backend: size/extension/magic-byte checks, DOCX package checks ─▶ AES-256-GCM encrypt ─▶ storage
       ─▶ 202 + analysis PENDING ─▶ (after commit) bounded worker pool:
          decrypt ─▶ ai-service: sniff type ─▶ parse in a child process (timeout, memory cap)
                   ─▶ clean text ─▶ drop instruction-like lines ─▶ LLM (structured output, strict Pydantic schema)
                   ─▶ grounding: every item must quote text actually present in the CV, else dropped
          ─▶ backend re-validates the response (strict JSON + bean validation) ─▶ normalize skills to the catalog
          ─▶ COMPLETED (or FAILED with a stable error code; nothing partial is saved)
user reviews skills + evidence ─▶ confirms selected skills ─▶ candidate_skills (used by matching)
```

| Concern | Implementation |
|---|---|
| Accepted files | PDF and DOCX, max 5 MB. The type is detected from content, not the client's name or MIME type; extension and content must agree. |
| Malicious documents | DOCX: ZIP entry/size/ratio limits (zip bombs), macro rejection, DTD/entity rejection (XXE, billion laughs). PDF: encrypted files rejected, page limit (20). Parsing runs in a separate process with a wall-clock timeout and a memory limit, so a crafted file can't take down the service. |
| Empty or scanned CVs | Fewer than 80 readable characters gives `NO_EXTRACTABLE_TEXT`, with guidance to export a text-based file. Over-long documents are rejected (`DOCUMENT_TOO_LONG`), never silently truncated. |
| Prompt injection | Zero-width and control characters are removed. Lines that address an AI system are removed from both the model input and the grounding corpus. The CV is wrapped in `<cv_document>` delimiters (closing tags neutralized), and the system prompt marks it as untrusted data. The model gets no tools and must return a fixed schema. The user sees a warning when anything is removed. |
| No fabrication | Strict Pydantic schema (`extra="forbid"`). Every skill, degree, job, project and certification needs a verbatim evidence quote that must appear in the CV, with names that appear as whole terms ("Node.js" never yields "JS"); dates are kept only if their year appears in the CV. Unverifiable items are dropped and counted, never repaired. No contact details are extracted. |
| Validation before persistence | The backend parses the AI response with unknown fields forbidden, validates enums, lengths, list sizes and date patterns, and maps skills to the catalog deterministically. Unrecognized skills are kept but marked "not in catalog" and are never used for matching. |
| Human confirmation | Extracted skills are only suggestions. The candidate selects which go into their profile; only skill ids that the analysis contains are accepted. |
| Private storage | Files are AES-256-GCM encrypted by the backend (`CV_ENCRYPTION_KEY`) before reaching storage, with the object key bound as AAD. Object keys are random (`cv/{userId}/{uuid}`). Storage is on a private network with no published port and credentialed access only; downloads stream through the backend after an ownership check (`Content-Disposition: attachment`, `nosniff`). |
| Ownership | Every CV, analysis, download and confirmation is scoped to the authenticated user; other users' ids return 404. |
| Deletion | Deleting a CV removes its analyses and stored file (confirmed skills are kept, unlinked). Deleting the account removes all stored files after the database deletion commits. |
| Failure handling | Typed error codes from document checks and the provider (`AI_UNAVAILABLE`, `AI_RATE_LIMITED`, `AI_TIMEOUT`, `AI_REFUSED`, `AI_INVALID_OUTPUT` with one retry, …). One analysis per user at a time, upload rate limit (10/hour), a bounded worker queue (`SERVICE_BUSY`), and a sweeper that fails analyses interrupted by restarts (`INTERRUPTED`) or hung (`STALE`). |
| History | Every run is a new analysis row with provider, model, prompt and schema versions; re-analysis never overwrites earlier results. |
| Logging | CV text, evidence and model output are never logged, only sizes, counts, codes and request ids. |

### AI provider

| `AI_PROVIDER` | Behaviour |
|---|---|
| `anthropic` (production) | Claude (`AI_MODEL`, default `claude-opus-5-5`) via the official Anthropic SDK, with structured outputs (`output_format`) and server-side refusal fallbacks enabled (`AI_FALLBACKS_ENABLED`). Requires `ANTHROPIC_API_KEY`; the key exists only in the ai-service container. |
| `local` (default for development) | Deterministic, offline rule-based extractor: skills from a fixed lexicon and education lines, each with the line it came from as evidence. Experience, projects and certifications are not extracted, and the UI says so ("Basic analysis"). No key, no network. |

Both providers go through the same grounding and validation path.

## Local development (without Docker for the apps)

Run Postgres and storage in Docker, and each app natively with hot reload. Load the variables from `.env`
first, in each terminal:

```bash
docker compose up -d postgres storage
set -a; . ./.env; set +a
# Storage has no host port by default. For a natively running backend, create a git-ignored
# docker-compose.override.yml that publishes it on loopback only:
#   services:
#     storage:
#       ports: ["127.0.0.1:8333:8333"]
```

**AI service** (terminal 1):

```bash
cd ai-service
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
AI_DOCS_ENABLED=true .venv/bin/uvicorn app.main:create_app --factory --reload --port 8000
```

If the Docker `ai-service` is also running, stop it first (`docker compose stop ai-service`) or use
another port and set `AI_SERVICE_URL` accordingly for the backend.

**Backend** (terminal 2):

```bash
cd backend
DB_PASSWORD=$POSTGRES_PASSWORD COOKIE_SECURE=false ./mvnw spring-boot:run
# Defaults: DB jdbc:postgresql://localhost:5432/careermatcher, AI service http://localhost:8000
```

**Frontend** (terminal 3):

```bash
cd frontend
pnpm install
cp .env.example .env.local     # BACKEND_INTERNAL_URL / NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
pnpm dev
```

## Tests and quality checks

| App | Command | What it covers |
|---|---|---|
| Backend | `cd backend && ./mvnw test` | Against real Postgres and SeaweedFS (Testcontainers, **needs Docker running**), AI service mocked: CV upload validation (empty, unsupported, disguised, macro, oversized), encryption at rest, download, normalization, AI failure codes, strict AI-response validation, history, concurrency limit, stale sweeper, skill confirmation, CV/account deletion, CV ownership isolation; migrations, health, security defaults, registration/login/refresh rotation and reuse detection, logout, forged/expired/tampered tokens, password change revocation, account deletion, profile/preferences validation, onboarding rules, **cross-user isolation**, rate limits |
| AI service | `cd ai-service && .venv/bin/pytest` | Valid PDF/DOCX; malformed, truncated, unsupported, empty, encrypted, macro, XXE, zip-bomb, too-many-pages documents; injection screening (with false-positive checks); grounding (hallucinations dropped); provider failures and retry; Anthropic request shape and error mapping (mocked SDK, no network); local extractor; internal auth |
| AI service lint | `.venv/bin/ruff check . && .venv/bin/ruff format --check .` | |
| Frontend | `cd frontend && pnpm test` | Session store (refresh single-flight, retry, exit handling), API errors, Zod schemas, open-redirect guard, form helpers, components |
| Frontend E2E | `cd frontend && E2E_BASE_URL=http://localhost:3000 E2E_ADMIN_EMAIL=… E2E_ADMIN_PASSWORD=… pnpm test:e2e` (the job tests expect `JOBS_DEMO_DATA_ENABLED=true`) | Playwright against the running stack: auth and profile flows, plus CV upload (PDF/DOCX) → evidence-backed results → confirm skills → manage skills, history/re-analysis/download/delete, injection warning, empty CV failure, unsupported/disguised files, cross-user access. First run: `pnpm exec playwright install chromium`. Raise the per-IP rate limits in `.env` for repeated runs. |
| Frontend lint and types | `pnpm lint && pnpm typecheck` | |
| Frontend build | `pnpm build` | Production build (standalone output) |

## Configuration

All configuration comes from environment variables, documented in [`.env.example`](.env.example).
Secrets (`POSTGRES_PASSWORD`, `AI_INTERNAL_TOKEN`, and later the LLM provider key) live only in
`.env`, which is git-ignored, and are never sent to the browser. Compose refuses to start if a
required secret is missing.

| Variable | Used by | Purpose |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | postgres, backend | Database credentials |
| `AI_INTERNAL_TOKEN` | backend, ai-service | Shared secret for internal calls (≥ 32 chars) |
| `JWT_SECRET` | backend | Access-token signing key (≥ 32 bytes) |
| `COOKIE_SECURE` | backend | `true` in any HTTPS deployment; `false` only for http://localhost |
| `FORWARD_HEADERS_STRATEGY` | backend | `native` only behind a trusted reverse proxy (client IP for rate limits) |
| `RATE_LIMIT_REGISTER_PER_IP_PER_HOUR`, `RATE_LIMIT_LOGIN_PER_IP_PER_MINUTE` | backend | Per-IP limits (defaults 10 and 20) |
| `PUBLIC_API_BASE_URL` | frontend build | API URL used by the browser (inlined at build time) |
| `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET` | storage, backend | Object storage credentials and bucket |
| `CV_ENCRYPTION_KEY` | backend | Base64 AES-256 key for CV files (`openssl rand -base64 32`). **Back it up**: without it stored CVs can't be read. |
| `RATE_LIMIT_CV_UPLOADS_PER_HOUR` | backend | Per-user upload/re-analysis limit (default 10) |
| `AI_PROVIDER`, `ANTHROPIC_API_KEY`, `AI_MODEL`, `AI_EFFORT` | ai-service | LLM provider configuration (see "AI provider") |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | backend | Bootstrap the first administrator (see "Administration") |
| `JOBS_DEMO_DATA_ENABLED` | backend | Load the labelled demo job dataset (default `false`) |
| `CORS_ALLOWED_ORIGINS` | backend | Comma-separated browser origins |
| `OPENAPI_ENABLED` | backend | Toggle Swagger UI / OpenAPI |
| `AI_DOCS_ENABLED` | ai-service | Toggle FastAPI docs |
| `*_HOST_PORT` | compose | Host port mappings |
| `BACKEND_INTERNAL_URL` | frontend (server side) | How the Next.js server reaches the backend |

## Conventions

- **API:** `/api/v1/...`, JSON camelCase, errors as RFC 7807 `application/problem+json` with a stable
  `code` field. Every response carries an `X-Request-Id` header, which is also present in the logs.
- **Security defaults:** every backend endpoint requires authentication except health, API docs and
  the public auth endpoints.
  Only `health` and `info` actuator endpoints are exposed, without details. All containers run as non-root.
- **Database:** schema changes only through new Flyway migrations, never by editing applied ones.
  Hibernate runs with `ddl-auto=validate`.
- **Logging:** request metadata only. Never log CV contents, passwords, tokens or API keys.
- See [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) §11 for code style conventions.

## Troubleshooting

- **`port is already allocated` / `address already in use`:** another process uses the port. Change
  the matching `*_HOST_PORT` in `.env`. If you change `FRONTEND_HOST_PORT`, update
  `CORS_ALLOWED_ORIGINS` too.
- **Backend tests fail with "Could not find a valid Docker environment":** start Docker Desktop.
  Testcontainers needs it.
- **Python 3.14+ locally:** some dependencies may not have wheels yet. Create the venv with Python
  3.12 or 3.13.
