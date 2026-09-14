# FINAL-AUDIT.md — Adversarial Review Findings

## CRITICAL

**1. Concurrent token refresh race triggers false breach-detection lockout.**
*Doc:* architecture/security (frontend interceptor, refresh rotation). *Problem:* if two API calls both receive `401` at nearly the same moment, both interceptors independently call `/auth/refresh`. The first rotates the token; the second request, already in flight, presents the now-superseded refresh token cookie value. Reuse-detection (as previously specified) treats this as theft and revokes the entire token family — logging out a legitimate user for normal concurrent request behavior, not an attack.
*Fix:* (a) frontend must single-flight refresh — the first `401` starts one refresh call; all other concurrent `401`s wait on that same in-flight result instead of calling refresh themselves (§ frontend rewrite, architecture.md §9). (b) backend adds a 10-second grace window: a refresh token used within 10s of being superseded is accepted as a benign race, not treated as reuse; only reuse **after** the grace window, or reuse with a mismatched IP/User-Agent fingerprint, triggers family revocation (security.md §2 rewrite).

**2. Resume-replacement R2 objects are never actually cleaned up.**
*Doc:* architecture (orphan cleanup), database (resumes table). *Problem:* orphan cleanup was defined as "R2 object with no matching DB row." When a resume is replaced, the old row is **not deleted**, only flagged `is_active=false` — so its R2 object always has a matching row and is never treated as orphaned. The old file leaks forever.
*Fix:* two distinct cleanup mechanisms, not one: (a) orphan-by-absence sweep (catches failed uploads where R2 succeeded but the DB insert never committed), and (b) a superseded-resume sweep that finds `is_active=false AND pending_deletion_at < now()-24h AND deleted_at IS NULL`, deletes the R2 object, then sets `deleted_at`. Requires new columns `pending_deletion_at`, `deleted_at` on `resumes` (database.md rewrite).

**3. Missing `ON DELETE CASCADE` on `gap_analysis_results.application_id` / `.resume_id` breaks account deletion.**
*Doc:* database.md. *Problem:* these FKs were left to default to `NO ACTION`. Deleting an application or resume that has an associated Gap Analyzer result would throw a foreign-key violation — which means the account-deletion cascade (architecture.md §10, as previously written) would **fail outright**, not silently succeed, the exact opposite of what was claimed.
*Fix:* explicit `ON DELETE CASCADE` on both FKs (database.md rewrite). Verified against every deletion flow, not just asserted.

**4. False "exactly-once" implication for async job processing.**
*Doc:* architecture.md (as previously written, implied by "no job is ever double-processed"). *Problem:* claiming/implying exactly-once is not true. If an instance crashes after a successful external AI call but before the DB commit that marks the job DONE, the lease expires, the job is reclaimed, and the AI call **is made again**. That is at-least-once for the external side effect. This was not an achievable guarantee to begin with (an HTTP call to Groq cannot be atomically coupled to a local DB commit), and claiming otherwise would not survive scrutiny.
*Fix:* explicitly documented as: **at-least-once for external AI calls, effectively-once for persisted state** (because the finalize step is an idempotent `UPDATE` to a pre-existing placeholder row, not an `INSERT`). Duplicate-cost risk from a crash-window retry is bounded and accepted, not hidden (architecture.md rewrite §3, cross-referenced in system-spec.md).

**5. DOCX/PDF parsing has no defense against decompression bombs or hangs.**
*Doc:* architecture (file processing), security. *Problem:* Apache POI (DOCX is a zip) is a known vector for zip-bomb-style resource exhaustion (a few KB expanding to gigabytes) if parsed with default settings; PDFBox can hang on malformed/malicious PDF structure. Neither was bounded.
*Fix:* `ZipSecureFile.setMinInflateRatio()` + max-entry-size ceiling before POI parses anything; both PDFBox and POI extraction run inside a bounded executor with a hard 10s wall-clock timeout, killed and rejected (`400 invalid-file-type`) if exceeded; extracted text capped at 20,000 characters even if parsing succeeds, to bound downstream memory/LLM cost regardless of upload size (security.md + architecture.md rewrite).

## HIGH

**6. Generic `idempotency_keys` table has its own crash-survival gap and doesn't handle "same key, different payload."**
*Problem:* the check-then-insert-then-store-response pattern has a window between checking and storing where a crash loses the record, and nothing compared the replayed payload to the original.
*Fix:* generic table removed. Job-creation endpoints now rely on the existing `uq_async_jobs_idempotency` unique constraint directly as the crash-safe backstop — a single `INSERT` either succeeds or hits the constraint; on conflict, the new request's payload fingerprint (`request_fingerprint`, new column, sha256 of the normalized request body) is compared to the existing row's; a mismatch returns `409 idempotency-key-conflict`, a match returns the existing job's current state. Account deletion is made naturally idempotent by state-checking instead of needing a key at all (api-spec.md, database.md, architecture.md rewrite).

**7. `async_job.avg_processing_seconds` conflated queue wait with processing time.**
*Problem:* `completed_at - created_at` includes time spent sitting `QUEUED`, not just execution time — makes the metric useless for diagnosing whether AI calls or queue backlog are the bottleneck, and the audit specifically requires separating these.
*Fix:* new column `processing_started_at` (set atomically in the claim `UPDATE`). Three distinct metrics: `queue_wait_seconds` (`processing_started_at - created_at`), `processing_seconds` (`completed_at - processing_started_at`), `total_seconds` (sum, for user-facing "how long did this take" only) (observability.md rewrite).

**8. Redis metrics counters used a read-then-reset pattern — a race drops increments.**
*Problem:* `GET` then `SET 0` (or similar) between two Redis calls is not atomic; an `INCR` landing between them is silently lost.
*Fix:* switch to hour-bucketed keys (`metrics:cache:gap:hit:{yyyyMMddHH}`) with a 2-hour TTL, read without ever resetting — the aggregation job reads and sums the last completed hour's bucket, and buckets expire on their own. No read-modify-write race is possible (observability.md rewrite).

**9. Cache key omitted the model identifier, only versioned the prompt.**
*Problem:* if the model is swapped without bumping `prompt_version` (e.g. a Groq model deprecation forcing a substitution), stale results from the old model would be served from cache under the new model's identity, silently.
*Fix:* cache key = `sha256(promptVersion + modelName + jdTextNormalized + resumeContentHash)` (architecture.md rewrite).

**10. AI "regression tests" as originally scoped were just JSON-parsing tests, not quality tests.**
*Problem:* LLM output is non-deterministic; you cannot assert exact output in a per-PR CI gate without either being trivial (schema-only) or flaky (content match).
*Fix:* two separate test types, not one: (a) deterministic WireMock-based tests for schema validation/retry/failure paths (unchanged, run every PR); (b) a **separate, scheduled (weekly, not per-PR), human-reviewed** eval suite against the real Groq API using ~10 fixed golden JD/resume and question/transcript fixtures, asserting loose, reviewable properties (e.g. "missingKeywords contains at least one of {Kubernetes, K8s, container orchestration}" for a fixture known to be missing that skill) — failures are flagged for human review, not auto-blocking merges (testing-strategy.md rewrite).

**11. No behavior defined for accessing the app while `PENDING_DELETION`.**
*Problem:* refresh tokens are revoked on delete request (forcing logout), but nothing said whether a subsequent login should be allowed, or what happens to functional endpoints if it is.
*Fix:* login is allowed while `PENDING_DELETION` (so the user can reach the cancel-deletion screen); every functional endpoint except `/account/*` returns `403 account-pending-deletion` with the effective deletion date in `detail`, enforced by a single base-controller check, not per-endpoint (api-spec.md, security.md rewrite).

**12. Audio duration/format validation relied on Tika, which has inconsistent audio codec support.**
*Fix:* bundle `ffprobe` in the Docker image, shell out to it for duration + codec/container validation; a file `ffprobe` cannot parse is rejected as `invalid-audio` outright rather than silently trusting an unreliable duration read (architecture.md rewrite).

**13. Missing queue-health and dependency-latency metrics entirely.**
*Fix:* added `queue_depth` (`count(status='QUEUED')`), `oldest_queued_job_age_seconds`, `avg_attempt_count` (retry pressure), `ai_provider_failure_rate` (from circuit-breaker transition logs), `ai_dependency_latency` (Micrometer timer wrapped specifically around the Groq/Whisper HTTP client, separate from overall API latency), and HikariCP pool metrics (`hikaricp.connections.active/pending`, exported automatically once Micrometer+Hikari are both present — no custom code needed) (observability.md rewrite).

## MEDIUM

**14. No property-based tests for pure calculator functions** (backoff, WPM, filler-word count) — only example-based. Added `jqwik` property tests: backoff never exceeds the cap regardless of attempt count magnitude; filler-word count is always `≥0` and `≤` total word count (testing-strategy.md).
**15. No load/performance baseline existed despite NFR targets.** Added a minimal k6 script (20 concurrent virtual users, register→login→create→list) run manually/on a schedule, asserting the stated p95 target at that modest concurrency — a real, if small, baseline instead of an unbacked number (testing-strategy.md).
**16. Interview-oriented language embedded inside engineering specs** (ADR prose written as talking points, "the actual payoff" framing) undermines the documents' credibility as engineering artifacts. Moved to a new `interview-notes.md`; `system-spec.md`/`architecture.md` now state decisions and tradeoffs neutrally.
**17. Frontend polling had no defined cancellation-on-navigation or offline-pause behavior.** Fixed: poll subscription torn down via `takeUntilDestroyed()`; polling pauses on a fetch failure attributable to network loss and resumes on the browser `online` event rather than continuing to fail every 2s (architecture.md §9 rewrite).
**18. `updated_at` maintenance mechanism was unstated for JPA-managed entities.** Fixed: `@PreUpdate` lifecycle callback on every JPA entity; the raw-SQL `async_jobs` claim/finalize statements set it explicitly inline (already correct there, now stated as the reason two different mechanisms coexist) (database.md).
**19. Migration authoring rule for NOT NULL columns was implied, not stated.** Fixed: explicit rule — a new column is always added nullable (or with a `DEFAULT`) in the release that introduces it; a `NOT NULL` constraint is only added in a later release once all rolling-deploy instances are guaranteed to be writing it (database.md §6).
**20. PII redaction relied solely on "don't call PII getters in log statements," which doesn't catch indirect leaks** (e.g. an exception's message or `toString()` echoing a field). Fixed: a `SafeLogRedactor` regex-strips email-like and JWT-like patterns from any string before it reaches the log appender, as defense-in-depth beyond the Checkstyle rule (security.md).

## LOW
**21. R2/DB write ordering was implicit, not stated as a general rule.** Now explicit: for any flow writing to both R2 and Postgres, the R2 write always happens first and must succeed before the referencing DB row is committed — never the reverse, which could leave a DB row pointing at a nonexistent object (architecture.md).
**22. No explicit statement that DOCX/PDF parsing never executes embedded content** (macros, JavaScript, OLE auto-links) — true by construction (text-extraction-only APIs used, `PDFTextStripper`, not a renderer), but stated explicitly rather than left implicit (security.md).
**23. "Effectively-once" and "at-least-once" terminology was previously absent** from every document, leaving ambiguous language ("no job is ever double-processed") that reads as a stronger guarantee than the design provides. Terminology now used consistently across architecture.md and system-spec.md.
