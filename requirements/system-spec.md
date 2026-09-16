# system-spec.md — Source of Truth: AI-Powered Job/Interview Prep Tracker (Phase 1)
Supersedes all prior versions. Interview/demo talking-point language has been moved out of this document entirely into `interview-notes.md` (audit #16) — everything below states requirements and decisions neutrally.

Schema → `database.md`. API → `api-spec.md`. Architecture/async/AI design → `architecture.md`. Threat model → `security.md`. Metrics/alerts → `observability.md`. Test matrix → `testing-strategy.md`. Environments/CI/CD/capacity → `deployment.md`. Setup → `README.md`. Every entity/endpoint/metric/decision is defined in exactly one of these documents; this file references, it does not restate.

## 1. Product Scope & Phase 1 Boundary
**In scope:** Core Job Application Tracker (CRUD + status lifecycle, §7), JD-to-Resume Gap Analyzer (async, AI-backed), Voice Mock Interview (async, AI-backed), plus the infrastructure those three require (auth, resume storage, async job processing, email, observability, security).
**Backlog, explicitly out of scope:** Interview Question Predictor, Application Success Analytics, Salary Negotiation Simulator, Ghosting Detector. None of the Phase 1 infrastructure depends on any of these.

## 2. Locked Stack
Spring Boot (Java 21) · Angular · PostgreSQL/Neon · Redis/Upstash · Groq (LLM+Whisper) · Cloudflare R2 · Render/Railway · Vercel/Netlify · GitHub Actions. No item was found technically infeasible during the adversarial review; all fixes were design corrections within this stack, not stack changes.

## 3. Functional Requirements
| ID | Requirement | Spec reference |
|---|---|---|
| FR1 | Register/verify/login/logout/refresh/reset-password | api-spec.md §2, security.md §2 |
| FR2 | CRUD job applications with status lifecycle | api-spec.md §3, §7 below |
| FR3 | Upload/replace/download/delete one active resume | api-spec.md §4, architecture.md §9 |
| FR4 | Async Gap Analyzer | api-spec.md §5, architecture.md §6-7 |
| FR5 | Async Voice Mock Interview | api-spec.md §6, architecture.md §8 |
| FR6 | History of past Gap Analyzer/Voice runs | api-spec.md §5/§6 list endpoints |
| FR7 | Status visibility for async jobs (polling — architecture.md ADR-002) | api-spec.md §5/§6 |
| FR8 | Admin usage stats | api-spec.md §8, observability.md §3-4 |
| FR9 | Account deletion with grace period and FK-safe cleanup | api-spec.md §7, architecture.md §10, database.md §3 |

## 4. Non-Functional Requirements (each with a defined instrumentation path, observability.md §3)
API p95 <500ms non-AI endpoints; error rate <1%; async job success rate >95%; queue wait <5s, processing <30s (gap)/<60s (voice) — measured as **separate** metrics, not conflated (observability.md, audit #7); backend coverage ≥80% overall/≥90% service layer; WCAG 2.1 AA; zero high/critical dependency CVEs. No uptime SLA is claimed (deployment.md §8 explains why a free-tier stack cannot honestly claim one) — UptimeRobot's public status page is the honest, verifiable substitute.

## 5. Processing & Consistency Guarantees (canonical statement — architecture.md §2 is the full derivation, this is the summary referenced elsewhere)
Async job processing is **at-least-once for external AI calls, effectively-once for persisted final state**. It is not, and is not claimed to be, exactly-once end-to-end — that guarantee is not achievable when an external HTTP call cannot be atomically coupled to a local database commit. Duplicate-call risk from this is bounded (cache-before-commit ordering, architecture.md §6) but not eliminated. Idempotent job creation (database.md §4) prevents client-side retries from creating duplicate jobs; it does not prevent every theoretical server-side crash-window duplicate AI call, which is a distinct, smaller, explicitly accepted risk.

## 6. User Journeys (happy path — full detail in api-spec.md, sequence diagram in architecture.md §4)
1. Onboarding: register→verify→login→empty application list (empty state, §9).
2. Tracking: add application→update status as it progresses→filtered views.
3. Gap analysis: pick an application (requires an active resume on file)→trigger→poll→review result, retry on failure using the same idempotency key.
4. Mock interview: pick a question→record→upload→poll→review transcript/score/feedback.
5. Account lifecycle: request deletion→30-day grace (functional endpoints blocked, `/account/*` remains available, §8)→cancel or full FK-safe cleanup (architecture.md §10).

## 7. Application Status Lifecycle
States: `APPLIED, INTERVIEWING, OFFER, REJECTED, WITHDRAWN`.
```
APPLIED      -> INTERVIEWING, REJECTED, WITHDRAWN
INTERVIEWING -> OFFER, REJECTED, WITHDRAWN
OFFER, REJECTED, WITHDRAWN -> (terminal)
```
Any other transition → `400 invalid-status-transition` (api-spec.md §3, tested exhaustively, testing-strategy.md §2). Every transition writes one `audit_log` row.

## 8. Account Deletion — User-Facing Behavior
Request deletion → `PENDING_DELETION`, immediate logout everywhere (all refresh token families revoked). While `PENDING_DELETION`: login remains possible; every endpoint except `/account/*` returns `403 account-pending-deletion` (security.md §5). 30-day grace window, cancellable. After the window, full cleanup verified FK-safe end-to-end (database.md §3, architecture.md §10) — this was found broken in an earlier version (missing cascade FKs) and is now a tested guarantee (testing-strategy.md §3).

## 9. Frontend Contract (architecture.md §11 has the full mechanism detail)
Standalone components, RxJS-service-based state (NgRx deliberately not used — the state graph doesn't need it), functional guards/interceptors, Reactive Forms mirroring backend validation. Every async view has loading/error/empty states, never a blank screen. Polling has a defined lifecycle: 2s interval, 30-poll cap, teardown on navigation, pause/resume around network loss (fixes audit #17). Token refresh is single-flight per tab, paired with a backend grace window, specifically to prevent the false-lockout race found in this review (audit #1). WCAG 2.1 AA, mobile-first responsive.

## 10. Backend Package Boundaries
See architecture.md §12 — `jobqueue/` and `ai/` packages have no dependency on domain packages, enforced by an ArchUnit test, not just stated.

## 11. Production-Readiness Checklist
- [ ] All migrations tested via `test-migrations` (database.md §7, testing-strategy.md §4).
- [ ] Coverage ≥80% gate passing; distributed-correctness integration tests (testing-strategy.md §3) all green, including the crash-window and multi-instance-race tests.
- [ ] `dependency-check` clean of high/critical CVEs.
- [ ] `cypress-axe` clean on all E2E-visited screens.
- [ ] Health/readiness endpoints verified against a real deploy, not just locally.
- [ ] UptimeRobot configured, status page public.
- [ ] Grafana Cloud dashboard receiving real Micrometer data post-deploy, including the new queue-depth/dependency-latency/DB-pool metrics (observability.md §3).
- [ ] Secrets rotated at least once before public demo.
- [ ] Account-deletion cascade manually verified end-to-end once in staging (R2 objects gone, DB rows gone/anonymized, zero FK errors).
- [ ] Superseded-resume cleanup sweep manually verified to actually delete an old R2 object after replacement (the case the original design's orphan sweep silently could not catch, audit #2).
- [ ] README demo checklist complete.

## 12. Definition of Done (per feature)
Merged to `staging` only after: all CI stages green (deployment.md §5) + OpenAPI diff reviewed for accidental breaking changes + loading/error/empty states implemented + accessibility check passing + no new high/critical vuln + the relevant section of this document set updated in the same PR if behavior changed — the docs and code are versioned together.

## 13. Summary of Corrections from the Adversarial Review
Full severity-ranked detail in `FINAL-AUDIT.md`. In brief: the async job queue's processing guarantee is now stated precisely instead of implying exactly-once; a genuine FK-cascade bug that would have broken account deletion is fixed; resume-replacement cleanup was redesigned because the original mechanism could never have worked for that case; idempotency was simplified and made genuinely crash-safe; a real concurrency bug in refresh-token rotation (false breach lockout under normal concurrent requests) was found and fixed on both frontend and backend; file parsing was hardened against decompression-bomb-class attacks; metrics that were either unmeasurable, racy, or missing entirely (queue health, dependency latency, DB pool) were fixed or added; free-tier capacity claims were reframed as configurable, worst-case-modeled, and honestly scoped to a portfolio deployment rather than implying production-at-scale readiness.
