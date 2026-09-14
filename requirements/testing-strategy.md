# testing-strategy.md — Complete Testing Strategy & Test Matrix
Supersedes all prior versions. Incorporates FINAL-AUDIT.md items #10, #14, #15. Every architectural guarantee in architecture.md has at least one automated test named below — not just claimed here, but pointed at the specific test that backs it.

## 1. Test Matrix

| Layer | Scope | Tooling | Runs |
|---|---|---|---|
| Unit — backend | Business logic, calculators, validators | JUnit5 + Mockito | every PR |
| Property-based — backend (new, audit #14) | Pure functions: backoff, filler-word count, WPM | jqwik | every PR |
| Repository/integration | Real Postgres queries, incl. concurrent claim races | Testcontainers (Postgres) | every PR |
| API/integration | Full HTTP request→DB, incl. crash-window and race scenarios (§3) | Testcontainers (Postgres+Redis) + `MockMvc`/`WebTestClient` | every PR |
| External-dependency mocking | Groq/Whisper HTTP, incl. malformed/timeout/5xx | WireMock | every PR |
| AI quality eval (new, audit #10) | Real Groq API, golden fixtures, human-reviewed | Scheduled GitHub Action (weekly) | weekly, not per-PR |
| Frontend unit | Components/services/guards/interceptors | Jasmine + Karma | every PR |
| E2E | Full user journeys, real browser | Cypress | every PR |
| Accessibility | WCAG 2.1 AA on every E2E-visited screen | `cypress-axe` | every PR |
| Load baseline (new, audit #15) | Modest concurrency baseline against NFR targets | k6 | manual/scheduled, not per-PR |
| Security/dependency scan | Known-CVE detection | `dependency-check` | every PR |
| Static analysis | Style + banned patterns (string-concat JPQL, PII log accessors, raw `Thread`/rogue `@Async`, `bypassSecurityTrustHtml`) | Checkstyle, ESLint | every PR |
| Coverage | Line coverage gate | JaCoCo | every PR |
| Migration testing | Flyway history applies cleanly, then full suite runs against it | Testcontainers, dedicated CI job | every PR touching `db/migration/**` |

## 2. Unit & Property-Based Test Scope (concrete)
- `ApplicationStatusTransitionValidator`: table-driven, every legal transition passes, every illegal one throws — full matrix from api-spec.md §3.
- `AsyncJobBackoffCalculator`: example tests for `backoff(1..3)`; **property test (jqwik)**: for any `attemptCount` from 1 to 10,000, `backoff(attemptCount) ≤ 300`, closing the "what if attempt_count is somehow huge" edge case an example test wouldn't think to check.
- `FillerWordCounter`: example tests for known transcripts; **property test**: for any generated string, `0 ≤ fillerWordCount ≤ totalWordCount` always holds.
- `WordsPerMinuteCalculator`: zero-duration edge case returns `null`, not a division-by-zero exception.
- `GapAnalysisCacheKeyService`: same JD+resume+promptVersion+model → same key; changing **either** `promptVersion` **or** `modelName` → different key (regression test for audit #9's fix — a model change alone must invalidate cache even without a prompt bump).
- `RequestFingerprintService`: same logical payload → same fingerprint regardless of JSON key ordering (normalization correctness — this is what database.md §4's idempotency-conflict detection depends on being correct).

## 3. Integration Test Scope — Distributed Correctness (the tests that back architecture.md's guarantees)
- **Claim race** (backs architecture.md §3): two concurrent transactions run the claim query against a single `QUEUED` row; assert exactly one succeeds via `FOR UPDATE SKIP LOCKED`'s real Postgres behavior — run against the actual Testcontainers Postgres, not mocked, since this guarantee lives entirely in real database locking semantics.
- **Stuck-job sweep** (backs architecture.md §3): manually insert a `PROCESSING` row with `locked_until` in the past, run the sweep, assert it returns to `QUEUED` with correct `available_at`.
- **Crash-window duplicate-cost bound** (backs architecture.md §2/§6): simulate a crash between a successful WireMock-stubbed AI response and the DB commit (throw after the cache write, before the transaction commits); assert the job's inevitable retry hits the Redis cache and does **not** call WireMock a second time — this is the actual test proving the cache-before-commit ordering fix works, not just documenting the intent.
- **Idempotent job creation, same key/same payload** (backs database.md §4): two identical `POST /gap-analysis` requests with the same `idempotencyKey` — assert the second returns the **same** `jobId`, no second `async_jobs` row created.
- **Idempotency conflict, same key/different payload** (backs database.md §4, audit #6): same `idempotencyKey`, different `applicationId` — assert `409 idempotency-key-conflict`.
- **Concurrent duplicate submission** (true race, not sequential): fire two identical requests genuinely concurrently (parallel threads hitting the same transaction boundary) — assert the DB unique constraint, not application logic, is what prevents two rows, and that exactly one caller gets the "created" path while the other gets the "existing job" path.
- **Refresh-token grace-window race** (backs security.md §2, audit #1): simulate two concurrent `/auth/refresh` calls with the same original token — assert the second succeeds (grace window, matching fingerprint) rather than revoking the family; separately, assert a reuse **after** the grace window, or with a mismatched fingerprint, **does** revoke the family — both branches of the fix are tested, not just the happy path.
- **Account deletion FK-safety** (backs database.md §3, audit #3): create a user with at least one application, resume, and completed Gap Analysis result, then run the deletion cascade — assert it completes with zero FK violations and zero orphaned rows in any table.
- **Account deletion during in-flight processing**: request deletion while a `PROCESSING` job for that user exists — assert the job either completes and writes its result (allowed, harmless) or is later cleaned up by the cascade without error; no code path assumes a user row still exists mid-job without checking.
- **R2/DB inconsistency simulation** (backs architecture.md §9): stub the R2 client to fail after a "successful" call is already recorded is not possible to fully simulate without a real R2 dependency, so this is tested at the ordering-logic level instead — assert that if the R2 client call throws, **no** DB row referencing that (non-existent) object is ever committed; the R2-write-then-DB-write ordering is unit-testable by asserting the DB write method is never invoked when the R2 client mock throws.
- **Redis-down fail-closed for quota/rate-limit** (backs security.md §8): simulate Redis unreachable, assert `503 quota-service-unavailable`/login rejection, **not** silent unlimited access.
- **Redis-down fail-open for cache**: simulate Redis unreachable during a cache-check, assert the flow proceeds to call the (WireMock-stubbed) AI provider rather than erroring out.
- **Redis counter bucket correctness** (backs observability.md §3/§4, audit #8): increment a cache-hit counter across a simulated hour boundary, assert the aggregation job reads exactly the completed hour's bucket and a concurrent increment during the read is never lost (since there is no read-then-reset step to race).
- **Malformed AI response retry** (backs architecture.md §6): WireMock returns invalid JSON once, then valid JSON — assert exactly one retry occurs and the job completes `DONE`; WireMock returns invalid JSON twice — assert the job is `FAILED` with `last_error='malformed-ai-response'` and the raw invalid text is not persisted.
- **Circuit breaker opens under sustained failure**: WireMock returns 5xx repeatedly — assert the circuit opens after the configured failure-rate threshold and subsequent job attempts fail fast (`ai-service-unavailable`) without any further WireMock calls, verifiable by asserting WireMock's request count stops increasing.
- **Resume replacement + cleanup sweep interaction** (backs architecture.md §9, audit #2): upload a resume, replace it, assert the old row is `is_active=false` with `pending_deletion_at` set; manually advance time past 24h (or inject a fixed clock), run the superseded-resume sweep, assert the old R2 object is deleted and `deleted_at` is set — proving the fix actually works, not just that the old (broken) orphan-by-absence sweep runs without error.
- **Multi-instance worker simulation**: two separate `ApplicationContext`s (or two threads with distinct simulated `instanceId`s) polling the same Testcontainers DB concurrently against a batch of `QUEUED` jobs — assert every job is claimed exactly once across both, no job is claimed twice, no job is left unclaimed when capacity exists.

## 4. Migration Testing
Fresh Testcontainers Postgres, `flyway migrate` through every versioned file in order from empty, then the full API/integration suite runs against that schema — the only way migration order/correctness is actually exercised, not merely asserted.

## 5. E2E Scope (Cypress, docker-compose: backend+Postgres+Redis+WireMock+real frontend build)
1. Register→verify(stubbed email link)→login.
2. Create application→upload resume→Gap Analyzer (WireMock canned success)→poll to DONE→result rendered.
3. Voice Mock: upload test audio→WireMock canned Whisper+STAR responses→poll to DONE→transcript/score rendered.
4. Failure path: WireMock 500 once→FAILED state with working retry→retry against a now-succeeding stub→DONE.
5. Concurrency: two browser contexts update the same application's status→second gets a visible conflict message, not a silent overwrite.
6. **Polling lifecycle** (backs architecture.md §11, audit #17): start a Gap Analyzer job, navigate away mid-poll, assert (via a network-call spy) that polling stops firing after navigation; separately, simulate offline/online browser events mid-poll and assert polling pauses and resumes correctly.
7. **Concurrent refresh** (backs audit #1): force two simultaneous API calls with an expired access token, assert only one `/auth/refresh` network call fires (single-flight) and both original calls eventually succeed.
Every spec runs `cy.checkA11y()` after significant renders.

## 6. AI Quality Evaluation (separate from correctness testing — audit #10)
Correctness (schema/retry/failure-path) is tested deterministically every PR via WireMock, above. **Quality** cannot be a deterministic per-PR gate, since real LLM output varies run to run. Instead: a GitHub Action scheduled weekly (and manually dispatchable) runs ~10 fixed golden fixtures (JD+resume pairs with known expected gaps; question+transcript pairs with known expected STAR characteristics) against the **real** Groq API, records the results, and opens a review artifact (not a failing build) for a human to check for drift — e.g. "this fixture's `missingKeywords` no longer includes the expected skill." `prompt_version`/`model_used` on every stored production result make it possible to correlate a real-world quality complaint to a specific prompt or model change after the fact.

## 7. Load/Performance Baseline (audit #15)
A k6 script simulating 20 concurrent virtual users performing register→login→create application→list applications, run manually or on a schedule (not per-PR — too heavy for routine CI), asserting p95 <500ms and 0% error rate at that concurrency against a staging deployment (deployment.md §1). This is a real, modest, honestly-scoped baseline appropriate to the app's actual expected load — not a claim of load-testing at production internet scale, which this stack was never designed for.

## 8. Static Analysis & Coverage Gates
Checkstyle (`maven-checkstyle-plugin`, bound to `validate`, `failOnViolation=true`) bans: string-concatenated JPQL/native queries; PII accessors inside `log.*` calls; direct `new Thread`/unmanaged `@Async` outside the sanctioned worker loop. ESLint bans: `any` type, `bypassSecurityTrustHtml`. Coverage gate: `jacoco-maven-plugin`'s `check` goal bound to the `verify` phase, ≥80% overall, ≥90% on `service` packages — `mvn verify` fails the build below threshold, not just a reported number in a dashboard.
