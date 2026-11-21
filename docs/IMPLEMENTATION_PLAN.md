# AI Career Matcher — Implementation Plan

Status: **Milestones 1-6 complete** (Milestone 2 = rows M2+M3; Milestone 3 = rows M5+M6 + skill catalog; Milestone 4 = rest of row M4; Milestone 5 = row M7 + explanations from M8; Milestone 6 = gap dashboard from M7 + roadmap from M8) · Last updated: 2026-11-21

## 0. Repository inspection (findings)

| Item | Finding | Consequence |
|---|---|---|
| Repository | Empty directory, not a git repo | Greenfield; nothing to preserve. `git init` in Milestone 1. |
| Java | OpenJDK 21.0.13 | Matches target. |
| Maven / Gradle | **Not installed** | Use the Maven Wrapper (`./mvnw`) so no global install is needed. |
| Node / pnpm | Node 24.22, pnpm 12.16 | Use pnpm for the frontend. |
| Python | 3.15.2 locally | Too new for reliable wheels (pydantic-core, PDF libs). **Pin the AI service to Python 3.13 in Docker**; local dev runs in a venv created from the Docker image or a pinned interpreter. |
| uv | Not installed | Use `pip` + `requirements*.txt` with pinned versions (or install uv; optional). |
| Docker | Engine 29.9, Compose v5.6 | Compose is the primary dev and deployment runtime. |

## 1. Target architecture

```
                ┌────────────────────┐
 Browser ──────▶│ frontend (Next.js) │  public
                └─────────┬──────────┘
                          │ REST (JSON, cookie + bearer)
                ┌─────────▼──────────┐       ┌──────────────┐
                │ backend (Spring)   │──────▶│ PostgreSQL   │
                │ modular monolith   │──────▶│ MinIO (S3)   │ private bucket
                └─────────┬──────────┘       └──────────────┘
                          │ internal network only, shared-secret header
                ┌─────────▼──────────┐
                │ ai-service (FastAPI)│──────▶ LLM provider (server-side key)
                └────────────────────┘
```

Key decisions:
- **The backend is the only caller of the AI service.** The AI service has no published port in the production compose file, sits on an internal Docker network, and requires an `X-Internal-Token` header that it compares in constant time.
- **The AI service never touches S3 or Postgres.** The backend reads the file from storage and streams the bytes to the AI service, so the AI service holds no storage or database credentials.
- **Matching is computed in the backend, deterministically.** The AI is used for extraction, explanations and roadmaps, and only on facts the backend passes in.
- **CV analysis runs asynchronously, without a broker.** Analysis gets a DB status (`PENDING → PROCESSING → COMPLETED | FAILED`) and runs on a bounded Spring `@Async` executor. The frontend polls. A restart sweep marks stuck `PROCESSING` rows as `FAILED`.

## 2. Monorepo layout

```
/frontend      Next.js (App Router) + TS + Tailwind + shadcn/ui
/backend       Spring Boot, Maven wrapper, package-by-domain
/ai-service    FastAPI + Pydantic
/docs          plan, ADRs, API notes, matching algorithm spec
/infra         compose overrides, MinIO bucket init, Postgres init
docker-compose.yml        dev stack
docker-compose.prod.yml   hardened overrides (no internal ports exposed)
.env.example              every variable documented; real .env gitignored
```

### Backend modules (`com.careermatcher.*`)
`common` (errors, security utils, pagination DTOs) · `auth` · `user` · `profile` · `skill` (catalog + normalization) · `cv` (documents, analyses) · `storage` (S3 adapter) · `aiclient` (typed client to ai-service) · `job` · `bookmark` · `matching` (engine + gap analysis) · `roadmap` · `admin`.

Rules: each module exposes a service interface and DTOs. No module reaches into another's repositories or entities. Boundaries are enforced with an ArchUnit (or Spring Modulith verification) test.

## 3. Data model (initial Flyway migrations)

- `users` (id UUID, email unique, password_hash, role CANDIDATE|ADMIN, status, created_at, deleted_at)
- `refresh_tokens` (id, user_id, token_hash, expires_at, revoked_at, replaced_by): rotating, stored hashed
- `candidate_profiles` (user_id PK/FK, full_name, headline, location, country, onboarding_completed)
- `candidate_preferences` (user_id, desired_roles[], locations[], remote_preference, job_types[], min_salary nullable)
- `profile_education`, `profile_experience`, `profile_projects`: user-editable, seeded from confirmed CV analysis
- `skill_categories`, `skills` (canonical name, slug unique), `skill_aliases` (alias_normalized unique → skill_id)
- `candidate_skills` (user_id, skill_id, source CV|MANUAL, cv_analysis_id nullable, proficiency nullable, evidence)
- `cv_documents` (id, user_id, storage_key, original_filename_sanitized, mime, size, sha256, uploaded_at, deleted_at)
- `cv_analyses` (id, cv_document_id, status, model, prompt_version, schema_version, result JSONB, unmapped_skills JSONB, error_code, timings). This table keeps the analysis history.
- `companies`, `jobs` (title, description, seniority, employment_type, location, remote_type, min/max_experience_months, education_level, education_fields[], salary range, status DRAFT|PUBLISHED|ARCHIVED, source, source_url, posted_at)
- `job_skills` (job_id, skill_id, requirement REQUIRED|PREFERRED, weight)
- `bookmarks` (user_id, job_id, created_at, unique pair)
- `job_matches` (user_id, job_id, cv_analysis_id, total_score, breakdown JSONB, missing_skills JSONB, algorithm_version, weights JSONB, computed_at). Each row is a snapshot, so the history can be audited.
- `match_explanations` (job_match_id, text, model, prompt_version, created_at)
- `learning_roadmaps` (user_id, based_on_match_ids, content JSONB, model, created_at)
- `skill_resources` (skill_id, title, url, type): curated links. **The AI never invents URLs.**

Indexes: FKs, `jobs(status, posted_at)`, `job_skills(skill_id)`, `candidate_skills(user_id)`, `job_matches(user_id, total_score desc)`, `skill_aliases(alias_normalized)`, plus trigram/full-text search on job title/description.

## 4. Matching engine (deterministic, versioned)

`algorithm_version = "match-v1"`. Weights come from `application.yml` (`matching.weights.*`), bind to a validated `@ConfigurationProperties` record, and must sum to 1.0 or the app refuses to start.

| Component | Weight | v1 computation (0–1) |
|---|---|---|
| Technical skills | 0.45 | `0.75 × (required matched / required total) + 0.25 × (preferred matched / preferred total)`. A job with no preferred skills uses required only. |
| Experience | 0.20 | Relevant months vs `min_experience_months`. Internships count; ≥ min = 1.0; linear below; small penalty if far above max (overqualified). |
| Projects | 0.15 | Share of job skills evidenced in candidate projects, capped. |
| Education | 0.10 | Degree level meets requirement (1.0 / partial / 0) × field relevance. "Not required" = 1.0. |
| Preferences | 0.10 | Location/remote, employment type, role keyword, salary overlap. Each sub-factor is only counted if the candidate stated it. |

**Missing data is explicit.** Each component returns `{score, status: SCORED|MISSING_CANDIDATE_DATA|NOT_REQUIRED_BY_JOB, factors[]}`.
- If candidate data is missing for experience, projects or education, the component scores 0 and is flagged, so the UI can say "add your experience to improve accuracy".
- If no preferences are set, that component scores neutral (0.5) and is flagged.

The full breakdown, weights snapshot and version are stored. Tests cover each component, boundaries, weight validation and golden-file end-to-end cases.

**Skill gap** = the job's required and preferred skills minus the candidate's skills. Results are aggregated across top-N matches and ranked by `required-ness × frequency`.

## 5. AI service design

Endpoints (internal only):
- `POST /v1/cv/extract`: multipart file → text plus structured `CvAnalysis`
- `POST /v1/match/explain`: facts → explanation
- `POST /v1/roadmap`: missing skills + curated resources → roadmap
- `GET /health`

Document processing:
- Verify magic bytes (`%PDF`; ZIP + `word/document.xml` for DOCX).
- Enforce size and page caps (e.g. 5 MB, 10 pages).
- Defuse zip bombs: check the uncompressed size limit and reject macros and embedded objects.
- Extract text with `pypdf`/`pdfplumber` and `python-docx`.
- Scanned (image-only) PDFs return `NO_EXTRACTABLE_TEXT` rather than guessed content. OCR is out of MVP scope.

Structured output:
- The LLM is called with a JSON-schema-constrained output.
- The output is parsed into strict Pydantic models (`extra="forbid"`, length limits, enums), with one repair retry and then a typed failure.
- **Grounding check:** every extracted skill, employer, degree and project must carry an `evidence` snippet that actually appears in the CV text. Items that fail are dropped and counted, never kept.
- Unknown values are `null`; the schema never forces a guess.

Prompt-injection defenses:
- CV text is placed inside clearly delimited data tags with a system instruction that it is untrusted data.
- The model has no tools.
- Output is schema-only.
- Explanation and roadmap prompts receive only backend-supplied structured facts. Their outputs are post-validated so they mention only skills present in the input set.

Provider abstraction: an `LLMProvider` protocol with one concrete implementation plus a `FakeProvider` for tests and offline dev. Model, temperature, timeouts and prompt versions come from env. Logs contain request IDs, sizes and timings, **never CV text**.

## 6. Security and privacy

- **Passwords:** Argon2id (or BCrypt cost 12) with password policy checks.
- **Auth:** a short-lived access JWT (15 min) is held in memory by the frontend. A rotating refresh token sits in an `HttpOnly; Secure; SameSite=Strict` cookie, stored hashed, with reuse detection that revokes the token family. CORS is restricted to the frontend origin.
- **Authorization:** role-based (`ADMIN` for `/api/admin/**`) plus ownership checks in services. Every CV, analysis, match, bookmark and roadmap query is scoped by the authenticated user ID. Cross-user access returns 404.
- **Uploads:** validation by size, extension, Apache Tika content sniffing and SHA-256. Files are stored under random UUID keys in a private bucket with server-side encryption. There is no public URL; downloads are streamed through the backend after the ownership check.
- **Rate limiting** (Bucket4j, in-memory; single instance in MVP): login/register, upload, analysis and AI-backed endpoints.
- **Deletion:** a user can delete a CV (object + analyses + derived CV skills) and their account (hard delete of personal data; matches cascade).
- **Logging:** structured JSON with request IDs. A redaction filter covers tokens, passwords and CV content.
- **Headers:** security headers on the backend and a Next.js CSP.
- **Secrets:** env-only, documented in `.env.example`, never shipped to the browser.

## 7. Frontend

Next.js App Router with Tailwind, shadcn/ui and a navy/blue theme defined as tokens.
- TanStack Query for server state, typed API client generated from the backend OpenAPI spec (`openapi-typescript`), React Hook Form + Zod.
- Route groups: `(marketing)` landing · `(auth)` login/register · `(app)` onboarding, dashboard, cv, jobs, jobs/[id], matches, skills-gap, roadmap, bookmarks, settings · `admin/jobs`.
- Every page gets loading, error and empty states, and every view must work responsively. **No hardcoded results:** all data comes from the API.

## 8. Testing strategy

- **Backend:** JUnit 5 unit tests (matching, normalization, validators) and `@SpringBootTest` + Testcontainers (Postgres, MinIO) integration tests. MockMvc security tests check 401/403/404 on ownership. A WireMock stub stands in for the AI service.
- **AI service:** pytest with `FakeProvider`, fixture PDFs and DOCX files (including malicious ones: zip bomb, injected instructions, image-only PDF), schema validation and grounding tests.
- **Frontend:** Vitest + Testing Library for components, Playwright for the main end-to-end journey against the compose stack.

## 9. Milestones

Each milestone ends with: run the stack → run the tests → exercise the API (Swagger / curl) → security review → fix → update docs.

| # | Milestone | Scope | Done when |
|---|---|---|---|
| **M1** ✅ | Foundations | git init, monorepo skeletons, **dependency version verification**, compose (postgres, backend, ai-service, frontend; MinIO deferred to M6), health endpoints, centralized error format (RFC 7807), OpenAPI, logging, `.env.example`, README, conventions doc | `docker compose up` brings all services healthy; all three test suites run green |
| **M2** ✅ | Auth | register/login/refresh/logout/me, roles, rate limiting, security tests; landing + login/register pages | A user can register and log in via the UI; token rotation tests pass |
| **M3** ✅ | Profile & onboarding | profile, preferences (education/experience/projects tables moved to M6, where CV analysis populates them); onboarding wizard; settings | Data persists and ownership tests pass |
| **M4** ✅ | Skills & jobs | canonical skill catalog + aliases (seeded via migration), normalization service; admin job CRUD with required/preferred skills; paginated, filterable job list + detail; bookmarks | Admin creates a job, candidate finds and bookmarks it |
| **M5** ✅ | AI service | extraction pipeline, schemas, provider + fake, grounding, injection defenses, internal auth | pytest suite passes, including adversarial fixtures |
| **M6** ✅ | CV pipeline | SeaweedFS (MinIO no longer publishes images) in compose, client-side AES-GCM; upload → storage → async analysis → normalization → review/confirm → candidate skills; history; delete | Real PDF/DOCX end-to-end produces a validated analysis |
| **M7** ✅ | Matching & gaps | engine v1, config weights, snapshots, recommendations page, gap dashboard | Golden tests pass; scores and breakdown visible |
| **M8** ✅ | Explanations & roadmap | grounded explanation per match (cached), learning roadmap using curated resources | Outputs reference only real data; validation tests pass |
| **M9** | Dashboard & polish | personalized dashboard from real data, animations, accessibility pass, empty/error states everywhere | Playwright journey covers all 8 MVP outcomes |
| **M10** | Hardening & release | prod compose, security review, dependency audit, backup notes, final docs | Clean deploy from scratch using README only |

### Resolved versions (Milestone 1)

| Component | Version | Note |
|---|---|---|
| Spring Boot | 4.1.3 | 4.2.0 was days old; springdoc 3.1.x is built against Boot 4.1 |
| springdoc-openapi | 3.1.1 | |
| PostgreSQL | 18 (alpine) | Same image in compose and Testcontainers |
| Next.js / React | 16.4.6 / 19.3 | Cache Components enabled (create-next-app default) |
| Tailwind CSS | 4.3 | |
| Python (container) | 3.13 | Local venv verified on 3.12 |
| FastAPI / Pydantic | 0.165.0 / 2.14.4 | |

### Milestone 3 decisions

- **Object storage:** SeaweedFS 4.54 in S3 mode, because MinIO stopped publishing community images in 2025. Files are encrypted by the backend (AES-256-GCM), so confidentiality does not depend on the storage product.
- **LLM:** Claude Opus 5.5 via `client.beta.messages.parse` with a Pydantic `output_format`, `effort: medium`, and server-side refusal fallbacks (`fallbacks: "default"`). A deterministic `local` provider keeps the pipeline usable without a key.
- **Education, experience and projects** are stored per analysis (validated JSONB), not as editable profile tables. Matching (M7) reads them from the candidate's latest completed analysis; manual editing is deferred.
- **Skill confirmation** is required before skills are used (answering open decision 3).

### Milestone 4 decisions

- **No bundled real listings.** Openings found online can't be verified as current or licensed for republication, so the initial dataset is demo-only and real listings are entered by admins. `REAL`/`DEMO` is enforced in the API, a database CHECK on demo URLs, and the UI.
- **Companies are inline fields** on the job (name and website), not a separate table; a companies entity can come with employer accounts.
- **Search uses `ILIKE` with trigram indexes** on title and company (plus description). That's adequate at MVP scale; switch to full-text search if the catalogue grows large.
- **Admins are bootstrapped from env** (no self-service admin signup, no promotion of existing accounts).

### Milestone 5 decisions

- **Scoring is a pure function** (`MatchingEngine`, `match-v1`) with hand-computed unit tests. The section 4 rules were refined: projects need 3 matching job skills (or all, if fewer) for full credit; experience merges overlapping roles; education treats ongoing degrees as 0.8; salary is compared only in the same currency.
- **Results are computed on request** for all published jobs (cheap at current catalogue size) and stored only when they change (content hash). At thousands of jobs, move to precomputation with pre-filtering.
- **Explanations are generated on demand, not per listing**, to control cost. Both services enforce the grounding guard.

### Milestone 6 decisions

- **Gap analysis targets the 10 best matches**, not all jobs, so gaps reflect jobs the candidate is close to. Priority uses only required/preferred counts; the average score gain is computed by re-running the matching engine, so "why it matters" is a measured number.
- **Reports are immutable snapshots** (`gap-v1`) for progress tracking; "closed" and "done" are evaluated against current confirmed skills when a report is read.
- **Learning resources are curated in a migration** (official, free, URL-checked). Roadmaps reference them by id; the AI cannot add links. Dead-link checking over time is not automated yet.
- **Roadmap validation** is a grounding and claims guard in both services: steps only for gaps, numbers only from facts, and no promises about jobs, interviews, offers, hiring or qualification.

## 10. Risks

| Risk | Mitigation |
|---|---|
| LLM extraction hallucination | Evidence-grounding check, nullable fields, user review/confirm step before skills are used |
| Prompt injection inside CVs | Data delimiting, no tools, strict schema, output validation |
| Job data source | No fabricated jobs: real listings come only from admins; the demo dataset loads only with `JOBS_DEMO_DATA_ENABLED=true`, is labelled everywhere, and can only link to example.com domains. |
| Library version drift (Spring Boot, Next.js, Python 3.15) | Verify current stable versions and compatibility in M0; pin everything; Python 3.13 in containers |
| Long-running analysis | Async status + polling, timeouts, stuck-job sweeper |
| Async without broker loses work on restart | Accepted for MVP; documented; sweeper marks stuck jobs FAILED and the user can retry |
| Skill catalog coverage | Unmapped skills stored and shown; admin can add aliases later |
| Cost / abuse of AI endpoints | Per-user rate limits and per-day analysis quota |

## 11. Conventions

- **Branches/commits:** Conventional Commits; one milestone per feature branch.
- **Java:** records for DTOs, constructor injection, no Lombok on entities, MapStruct or hand-written mappers, `@Transactional` at the service layer, Spotless (google-java-format).
- **API:** `/api/v1/...`, kebab-case paths, camelCase JSON, `Page` responses `{items, page, size, totalItems, totalPages}`, RFC 7807 errors with stable `code`.
- **Python:** ruff + mypy (strict on schemas), pytest.
- **TypeScript:** strict mode, ESLint + Prettier, no `any` in API types.
- **DB:** Flyway `V{n}__{description}.sql`; migrations are never edited after merge.

## 12. Open decisions (need product owner input)

1. LLM provider and model for the AI service.
2. Source of real job listings for launch (admin entry only vs. a licensed job API/import).
3. Whether a manual skill-review/confirm step after CV analysis is acceptable UX (recommended).
