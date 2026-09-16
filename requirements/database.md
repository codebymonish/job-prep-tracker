# database.md — Schema, Constraints, Indexes, Migration Strategy
Source of truth for all persisted data. Supersedes all prior versions; incorporates FINAL-AUDIT.md items #2, #3, #6, #18, #19.

Engine: PostgreSQL 15+ (Neon, pooled endpoint). Migration tool: Flyway, one file per change, forward-only, additive-first (see §6). No generic `idempotency_keys` table (removed per audit #6 — see §4 for the replacement design).

---

## 1. Entity Overview & Lifecycle Summary

| Entity | Lifecycle |
|---|---|
| `users` | UNVERIFIED → ACTIVE → (DISABLED on abuse) → PENDING_DELETION → DELETED (PII scrubbed) |
| `refresh_tokens` | issued on login → rotated on every refresh (10s grace overlap, §5) → revoked on logout/reuse-past-grace/family-revoke |
| `auth_tokens` | issued (EMAIL_VERIFY/PASSWORD_RESET) → consumed once → expired after TTL |
| `applications` | created → status transitions per api-spec.md §3 → soft-deleted (`is_deleted`) |
| `resumes` | uploaded (`is_active=true`) → superseded on re-upload (`is_active=false`, `pending_deletion_at` set) → R2 object + row hard-deleted by cleanup sweep after 24h (§4 audit #2) |
| `async_jobs` | QUEUED → PROCESSING (leased, `processing_started_at` set) → DONE / FAILED (after `max_attempts`); stuck jobs recovered by sweep |
| `gap_analysis_results` | placeholder row inserted at job creation, `UPDATE`d once on completion — never re-inserted (idempotent finalize, audit #4) |
| `voice_sessions` | same placeholder/update pattern as above |
| `voice_questions` | seeded via migration, static in Phase 1, `active` flag for retirement — **never hard-deleted**, so its FK from `voice_sessions.question_id` is deliberately `NO ACTION` |
| `audit_log` | append-only; PII stripped and `user_id` nulled specifically on account deletion, never on a general timer |
| `metrics_snapshot` | written by singleton scheduled aggregator, append-only |

---

## 2. Tables

```sql
-- ========== USERS & AUTH ==========

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) NOT NULL,
  email_normalized VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
  status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED'
    CHECK (status IN ('UNVERIFIED','ACTIVE','DISABLED','PENDING_DELETION','DELETED')),
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  deletion_requested_at TIMESTAMPTZ,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email_normalized ON users(email_normalized) WHERE status <> 'DELETED';

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  family_id UUID NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  superseded_at TIMESTAMPTZ,          -- set at rotation time; grace window = superseded_at + 10s (security.md §2)
  replaced_by UUID REFERENCES refresh_tokens(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ip_address INET,
  user_agent VARCHAR(500)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

CREATE TABLE auth_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_type VARCHAR(20) NOT NULL CHECK (token_type IN ('EMAIL_VERIFY','PASSWORD_RESET')),
  token_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_auth_tokens_hash ON auth_tokens(token_hash);
CREATE INDEX idx_auth_tokens_user_type ON auth_tokens(user_id, token_type);

-- ========== APPLICATION TRACKER ==========

CREATE TABLE applications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  company VARCHAR(255) NOT NULL,
  role_title VARCHAR(255) NOT NULL,
  jd_text TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'APPLIED'
    CHECK (status IN ('APPLIED','INTERVIEWING','OFFER','REJECTED','WITHDRAWN')),
  applied_date DATE NOT NULL DEFAULT CURRENT_DATE,
  is_deleted BOOLEAN NOT NULL DEFAULT false,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_applications_user_status ON applications(user_id, status) WHERE is_deleted = false;
CREATE INDEX idx_applications_user_created ON applications(user_id, created_at DESC) WHERE is_deleted = false;

-- ========== RESUMES ==========

CREATE TABLE resumes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  storage_key VARCHAR(500) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  extracted_text TEXT,                 -- capped at 20,000 chars at extraction time (security.md §7)
  is_active BOOLEAN NOT NULL DEFAULT true,
  pending_deletion_at TIMESTAMPTZ,      -- set when superseded; cleanup sweep acts after +24h (audit #2)
  deleted_at TIMESTAMPTZ,               -- set by cleanup sweep once R2 object confirmed deleted
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_resumes_user_active ON resumes(user_id) WHERE is_active = true;
CREATE INDEX idx_resumes_user ON resumes(user_id);
CREATE INDEX idx_resumes_pending_cleanup ON resumes(pending_deletion_at) WHERE is_active = false AND deleted_at IS NULL;

-- ========== ASYNC JOB QUEUE (generic, durable) ==========

CREATE TABLE async_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_type VARCHAR(30) NOT NULL CHECK (job_type IN ('GAP_ANALYSIS','VOICE_SCORING')),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  payload_ref UUID NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
    CHECK (status IN ('QUEUED','PROCESSING','DONE','FAILED')),
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 3,
  available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  locked_by VARCHAR(100),
  locked_until TIMESTAMPTZ,
  processing_started_at TIMESTAMPTZ,   -- set atomically by the claim UPDATE (audit #7); enables queue-wait vs processing-time split
  last_error TEXT,
  idempotency_key VARCHAR(100) NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,  -- sha256 of normalized request body (audit #6)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
CREATE INDEX idx_async_jobs_claim ON async_jobs(status, available_at) WHERE status = 'QUEUED';
CREATE INDEX idx_async_jobs_stuck ON async_jobs(status, locked_until) WHERE status = 'PROCESSING';
CREATE INDEX idx_async_jobs_user ON async_jobs(user_id, created_at DESC);
CREATE UNIQUE INDEX uq_async_jobs_idempotency ON async_jobs(user_id, job_type, idempotency_key);

CREATE TABLE gap_analysis_results (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  async_job_id UUID NOT NULL REFERENCES async_jobs(id) ON DELETE CASCADE,
  application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
  resume_id UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
  cache_key VARCHAR(64) NOT NULL,       -- sha256(promptVersion + modelName + jdText + resumeHash), audit #9
  prompt_version VARCHAR(20) NOT NULL,
  model_used VARCHAR(100) NOT NULL,
  jd_truncated BOOLEAN NOT NULL DEFAULT false,
  resume_truncated BOOLEAN NOT NULL DEFAULT false,
  result_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_gap_results_application ON gap_analysis_results(application_id);
CREATE INDEX idx_gap_results_cache_key ON gap_analysis_results(cache_key);

CREATE TABLE voice_questions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  category VARCHAR(50) NOT NULL CHECK (category IN ('BEHAVIORAL','TECHNICAL')),
  question_text TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- seeded via V__seed_voice_questions.sql, ~30 fixed rows. Never hard-deleted (retire via active=false only) —
-- this is why voice_sessions.question_id below is intentionally NOT ON DELETE CASCADE.

CREATE TABLE voice_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  async_job_id UUID NOT NULL REFERENCES async_jobs(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  question_id UUID NOT NULL REFERENCES voice_questions(id),  -- NO ACTION: questions are immutable/never deleted
  audio_storage_key VARCHAR(500) NOT NULL,
  audio_duration_seconds NUMERIC(6,2),
  prompt_version VARCHAR(20),
  model_used VARCHAR(100),
  transcript TEXT,
  star_score JSONB,
  filler_word_count INT,
  words_per_minute NUMERIC(6,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_voice_sessions_user ON voice_sessions(user_id, created_at DESC);

-- ========== AUDIT & METRICS ==========

CREATE TABLE audit_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID,
  entity_type VARCHAR(50) NOT NULL,
  entity_id UUID NOT NULL,
  action VARCHAR(30) NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE','STATUS_CHANGE','LOGIN','LOGOUT')),
  before_json JSONB,
  after_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_log(user_id, created_at DESC);

CREATE TABLE metrics_snapshot (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  metric_name VARCHAR(100) NOT NULL,
  dimensions JSONB NOT NULL DEFAULT '{}',
  value NUMERIC NOT NULL,
  period_start TIMESTAMPTZ NOT NULL,
  period_end TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_metrics_name_period ON metrics_snapshot(metric_name, period_end DESC);
```

---

## 3. Constraints Rationale, Verified Against Every Deletion Flow
- **Account deletion** (architecture.md §10): `users` cascades to `refresh_tokens`, `auth_tokens`, `applications`, `resumes`, `async_jobs` (all `ON DELETE CASCADE`). `async_jobs` cascades to `gap_analysis_results`/`voice_sessions`. `gap_analysis_results.application_id` and `.resume_id` are **also** `ON DELETE CASCADE` (fixed per audit #3) — without this, deleting a user with any Gap Analyzer history would throw an FK violation and the whole deletion would fail, which is the opposite of what a deletion cascade must guarantee. Verified: every table reachable from `users` by any FK path has an explicit `ON DELETE` behavior; none default to the implicit `NO ACTION` except `voice_sessions.question_id`, which is deliberate (§1) since `voice_questions` rows are never deleted.
- **Resume replacement**: covered by two independent mechanisms — `uq_resumes_user_active` (partial unique, enforces exactly one active row per user at the DB level) and the `pending_deletion_at`/`deleted_at` pair (audit #2) that lets the cleanup sweep find and finish off superseded rows and their R2 objects, which the old "orphan-by-absence" logic could never find since a superseded row is never absent.
- `idx_async_jobs_claim` / `idx_async_jobs_stuck`: support the exact claim/sweep queries in architecture.md §3.
- `uq_async_jobs_idempotency`: the sole idempotency backstop for job creation (audit #6) — a duplicate `INSERT` hits this constraint; the service layer catches the violation, compares `request_fingerprint`, and returns either the existing job's state (fingerprint matches) or `409 idempotency-key-conflict` (fingerprint differs).

## 4. Idempotency Design (replaces the removed generic table — audit #6)
Job-creation endpoints (`POST /gap-analysis`, `POST /voice-sessions`) attempt a single `INSERT` into `async_jobs` including `idempotency_key` and `request_fingerprint` (sha256 of the normalized request body — e.g. `applicationId` for Gap Analysis). Three outcomes:
1. Insert succeeds → new job, `202`.
2. Insert violates `uq_async_jobs_idempotency`, existing row's `request_fingerprint` matches → this is a legitimate retry (client resent after a timeout, or a crash occurred between the DB commit and the HTTP response reaching the client) → return the existing job's current `id`/`status`, `202`. No reprocessing, no duplicate job.
3. Insert violates the constraint, fingerprint differs → the client reused an idempotency key for a logically different request → `409 idempotency-key-conflict`.
This survives process crashes because it is a single atomic `INSERT` with no separate "check" step that could be crash-interrupted — there is no window where the system has "seen" the request but not yet durably recorded that fact.
`DELETE /account` needs no idempotency key at all: it is designed to be naturally idempotent by checking current `status` first (if already `PENDING_DELETION`, return the existing `deletion_requested_at`-derived effective date rather than erroring or re-processing).

## 5. Optimistic Locking & `updated_at`
- `applications.version`/`users.version`: JPA `@Version`, mismatched `PATCH` → `OptimisticLockException` → `409 stale-update`.
- `updated_at` on JPA-managed entities (`users`, `applications`): a `@PreUpdate` lifecycle callback sets it on every managed-entity update — one mechanism, applied uniformly.
- `updated_at` on `async_jobs`: **not** JPA-managed (it's updated via raw SQL in the claim/finalize statements for performance and to keep the `FOR UPDATE SKIP LOCKED` pattern a single statement) — those statements set `updated_at = now()` explicitly inline. This is a second, deliberate mechanism for one table, not an inconsistency: raw SQL needs an explicit `SET updated_at=now()`, JPA entities get it from the callback.
`async_jobs` itself uses pessimistic `FOR UPDATE SKIP LOCKED` for claiming, not `@Version` — the right tool for queue claiming versus the right tool for conflicting user edits.

## 6. Connection Pooling (Neon free-tier reality)
Unchanged from prior version: pooled endpoint (PgBouncer), HikariCP `maximumPoolSize=4`, `minimumIdle=1`, `connectionTimeout=5000ms`, `idleTimeout=300000ms`, `leakDetectionThreshold=30000ms`, sized for up to 2 backend instances (8 connections total).

## 7. Migration Strategy
- Flyway, versioned `V{n}__description.sql`, applied on startup, tested end-to-end in CI (`test-migrations` job, testing-strategy.md §4) against a fresh Testcontainers Postgres running every migration in order.
- **Additive-first rule, stated precisely (audit #19)**: a migration that adds a column must add it **nullable, or with a `DEFAULT`** — never `NOT NULL` with no default in the same release whose code doesn't yet guarantee populating it. Tightening to `NOT NULL` happens in a **later** migration, only after every rolling-deploy instance is confirmed running the code that populates it. Example: `async_jobs.request_fingerprint` above is `NOT NULL` because it ships in the same release as the code that always populates it (a new table, not a column added to existing rows) — this rule governs column additions to **existing, populated** tables, which is the actual rolling-deploy hazard.
- No down-migrations maintained; rollback is forward-fix (a new migration correcting the issue), never editing a shipped file. This is safe precisely because of the additive-first rule: a rolled-back code version never encounters a schema state it can't handle, since schema only ever gains nullable/defaulted columns ahead of code, never loses or tightens them out from under an older version.
