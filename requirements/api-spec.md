# api-spec.md — Complete API Contracts
Base path: `/api/v1`. All entities/columns referenced here are canonically defined in `database.md`. Supersedes all prior versions; incorporates FINAL-AUDIT.md items #6, #11. Mechanically convertible to OpenAPI — every field below has an explicit type, nullability, and format.

## 0. Cross-Cutting Rules
- **Auth**: Bearer JWT (`Authorization: Bearer <token>`) unless marked `public`. Refresh token: httpOnly cookie, `Path=/api/v1/auth`, never read by JS.
- **Ownership**: checked in the service layer; mismatch → `404 not-found` (never `403`, to avoid confirming existence to a non-owner).
- **PENDING_DELETION gate** (audit #11): a single base-controller check rejects every request to any path outside `/api/v1/auth/**` and `/api/v1/account/**` with `403 account-pending-deletion` (`detail` includes the effective deletion date) whenever `users.status='PENDING_DELETION'`.
- **Pagination**: `?page=0&size=20&sort=field,dir`. `page` ≥0 (default 0). `size` 1–100, clamped (not rejected) if outside range. `sort` format `field,asc|desc`, default `createdAt,desc`. Response envelope: `{"content":[...],"page":0,"size":20,"totalElements":42,"totalPages":3}`.
- **Dates/times**: all timestamps ISO-8601 UTC (`2026-09-12T10:00:00Z`), all dates `YYYY-MM-DD`. No other format is ever used.
- **Errors**: RFC 7807:
```json
{"type":"https://api.jobtracker.app/errors/{code}","title":"...","status":400,"detail":"...","instance":"/api/v1/...","correlationId":"uuid"}
```
Validation errors add `"fieldErrors":[{"field":"email","message":"must be a valid email"}]`.
- **Rate-limited (`429`)** responses always include `Retry-After: <seconds>`.
- **Idempotency** (rewritten per audit #6 — no generic header/table mechanism): `POST /gap-analysis` and `POST /voice-sessions` require `Idempotency-Key: <client-generated-uuid-v4>` in the request body (not a header — see §5/§6, it's part of the JSON payload so it can be included in the `request_fingerprint`). Missing → `400 idempotency-key-required`. Same key + same logical payload → returns the existing job, no reprocessing. Same key + different payload → `409 idempotency-key-conflict`. `DELETE /account` needs no key (naturally idempotent by state, database.md §4).

## 1. Error Code Catalog (exhaustive)
`validation-error`(400), `idempotency-key-required`(400), `invalid-file-type`(400), `invalid-audio`(400), `invalid-status-transition`(400), `invalid-token`(400), `invalid-credentials`(401), `refresh-invalid`(401), `account-not-verified`(403), `account-disabled`(403), `account-pending-deletion`(403), `forbidden`(403), `not-found`(404), `email-exists`(409), `stale-update`(409), `idempotency-key-conflict`(409), `rate-limited`(429), `quota-exceeded`(429), `quota-service-unavailable`(503), `ai-service-unavailable`(503), `internal-error`(500).

---

## 2. Auth

### POST /auth/register `public`
Req: `{"email":string(≤255,RFC5322),"password":string(≥8,≥1 upper,≥1 digit,≤72),"name":string(1–255)}`
201: `{"id":uuid,"email":string,"status":"UNVERIFIED"}`. 409 `email-exists`.

### GET /auth/verify?token=`string` `public`
200: `{"status":"ACTIVE"}`. 400 `invalid-token`.

### POST /auth/login `public`
Req: `{"email":string,"password":string}`
200: `{"accessToken":string,"expiresIn":900}` + `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
401 `invalid-credentials` (unknown email or wrong password — identical response). 403 `account-not-verified` / `account-disabled`.
Rate limit: 5/15min per (IP+email), Redis fail-**closed** (`503` on Redis outage).

### POST /auth/refresh `public` — requires refresh cookie + header `X-Requested-With: XMLHttpRequest`
200: new `accessToken` + rotated `Set-Cookie`.
401 `refresh-invalid` if missing/expired/revoked. **Reuse of a token superseded more than 10 seconds ago** (or superseded ≤10s ago but from a mismatched IP/User-Agent) revokes the entire `family_id` (architecture.md §11 ADR-006) — forces re-login. Reuse within the 10-second grace window from the same client fingerprint is treated as a benign concurrent-request race and succeeds normally.

### POST /auth/logout `auth`
204. Revokes the current refresh token family.

### POST /auth/password-reset-request `public`
Req: `{"email":string}` → always `202` (no enumeration). Rate limit: 3/hour per email, fail-closed.

### POST /auth/password-reset `public`
Req: `{"token":string,"newPassword":string}` → 200, invalidates all refresh token families for the user. 400 `invalid-token`/`validation-error`.

---

## 3. Applications

### GET /applications `auth`
Query: `page,size,sort,status` (status optional, one of `APPLIED|INTERVIEWING|OFFER|REJECTED|WITHDRAWN`).
200: paginated envelope, each item: `{"id":uuid,"company":string,"roleTitle":string,"status":enum,"appliedDate":"YYYY-MM-DD","version":int,"createdAt":ISO8601}`. `jdText` excluded from list items.

### GET /applications/{id} `auth,owner`
200: full record incl. `"jdText":string|null`. 404.

### POST /applications `auth`
Req: `{"company":string(1–255),"roleTitle":string(1–255),"jdText":string(≤20000)|null,"appliedDate":"YYYY-MM-DD"|null}` (`appliedDate` null → defaults server-side to today; must not be in the future if provided).
201: full record, `status="APPLIED"`, `version":0`.

### PATCH /applications/{id}/status `auth,owner`
Req: `{"status":enum,"version":int}`. Valid transitions: `APPLIED→{INTERVIEWING,REJECTED,WITHDRAWN}`, `INTERVIEWING→{OFFER,REJECTED,WITHDRAWN}`; all others `400 invalid-status-transition`.
200: updated record. 409 `stale-update` on version mismatch. Writes `audit_log`(`STATUS_CHANGE`).

### DELETE /applications/{id} `auth,owner`
204. Soft delete (`is_deleted=true`).

---

## 4. Resumes

### POST /resumes `auth,multipart`
Field `file`: PDF or DOCX, ≤5MB. Server validation independent of client: magic-byte detection (Tika), then bounded parsing (§ architecture.md §9 — 10s timeout, decompression-bomb guards).
Order: parse+validate → **upload to R2 first** → on R2 success, single DB transaction marks old active resume `is_active=false, pending_deletion_at=now()` and inserts the new `is_active=true` row.
201: `{"id":uuid,"originalFilename":string,"createdAt":ISO8601}`. 400 `invalid-file-type` (bad magic bytes, >5MB, failed parse/timeout).

### GET /resumes/active `auth`
200: metadata only, `{"id":uuid,"originalFilename":string,"createdAt":ISO8601}` — no `extractedText`, no raw URL. 404 if none uploaded.

### GET /resumes/active/download `auth`
200: `{"signedUrl":string,"expiresIn":300}` — presigned R2 GET, 5-minute TTL.

### DELETE /resumes/active `auth`
204. Marks `is_active=false, pending_deletion_at=now()` — actual deletion via the superseded-resume sweep (architecture.md §9).

---

## 5. Gap Analyzer (async)

### POST /gap-analysis `auth`
Req: `{"applicationId":uuid,"idempotencyKey":string(uuid-v4)}`.
400 `idempotency-key-required` if missing. 400 `validation-error` if the user has no active resume.
Behavior (database.md §4): single `INSERT` into `async_jobs`. Success → new job. Constraint hit + fingerprint match → existing job returned. Constraint hit + fingerprint mismatch → `409 idempotency-key-conflict`.
202: `{"jobId":uuid,"status":"QUEUED"}`.
429 `quota-exceeded` (limit: 20/day/user, Redis fail-closed → `503 quota-service-unavailable` if Redis unreachable), `Retry-After` set to seconds until UTC midnight.

### GET /gap-analysis/{jobId} `auth,owner`
200 (`QUEUED`/`PROCESSING`): `{"jobId":uuid,"status":string}`.
200 (`DONE`):
```json
{"jobId":"uuid","status":"DONE","result":{
  "matchScore":72,
  "matchedKeywords":["Java","Spring Boot"],
  "missingKeywords":["Kubernetes","GraphQL"],
  "suggestedBullets":[{"originalBullet":null,"suggestedBullet":"...","reasonTag":"missing-keyword:Kubernetes"}],
  "summary":"..."
},"jdTruncated":false,"resumeTruncated":false}
```
200 (`FAILED`): `{"jobId":"uuid","status":"FAILED","reason":"ai-service-unavailable"|"malformed-ai-response"}` — retry re-submits `POST /gap-analysis` with the **same** `idempotencyKey`, which (per §0/database.md §4) matches the existing row's fingerprint and is treated as a legitimate retry, re-queuing the same logical job rather than creating a duplicate.
Client polls every 2s, max 30 polls (60s), then switches to manual refresh (architecture.md §11).

---

## 6. Voice Mock Interview (async)

### GET /voice-questions `auth`
200: `{"content":[{"id":uuid,"category":"BEHAVIORAL"|"TECHNICAL","questionText":string}]}` — random 5 of `active=true` rows, no pagination (small fixed bank).

### POST /voice-sessions `auth,multipart`
Fields: `questionId`(uuid), `audioFile`(webm/mp3/wav, ≤10MB), `idempotencyKey`(uuid-v4).
Server validation: magic-byte/container check + `ffprobe`-based duration/codec check (architecture.md §8) — reject `400 invalid-audio` if `ffprobe` cannot parse the file or duration >180s, regardless of client-declared duration.
Quota: 10/day/user, same fail-closed rule.
Order: **upload to R2 first**, on success insert `async_jobs`+placeholder (same idempotency mechanics as §5).
202: `{"jobId":uuid,"status":"QUEUED"}`.

### GET /voice-sessions/{jobId} `auth,owner`
Same status shape as §5.
200 (`DONE`):
```json
{"jobId":"uuid","status":"DONE","result":{
  "transcript":"So, um, in my last role...",
  "starScore":{"situation":4,"task":3,"action":4,"result":3,"overall":3.5,"feedback":"..."},
  "fillerWordCount":6,
  "wordsPerMinute":142.5
}}
```

### GET /voice-sessions `auth`
Paginated history (§0 rules), summary fields only (no full transcript in list view): `{"id":uuid,"questionText":string,"overallScore":number|null,"createdAt":ISO8601}`.

---

## 7. Account

### DELETE /account `auth` — **no idempotency key required** (database.md §4)
Req: `{"confirmEmail":string}` (must match logged-in user's email).
202: `{"status":"PENDING_DELETION","effectiveAt":ISO8601}`. Calling this again while already `PENDING_DELETION` returns the same `202` with the same `effectiveAt`, unchanged — not an error, not a new deletion timer.
Side effect: `users.status='PENDING_DELETION'`, all refresh token families revoked.

### POST /account/cancel-deletion `auth`
200: `{"status":"ACTIVE"}`. Valid only while `PENDING_DELETION` and within the grace window; else `400 invalid-status-transition`.

### GET /account
200: `{"id":uuid,"email":string,"name":string,"status":string,"effectiveDeletionAt":ISO8601|null}` — always accessible even during `PENDING_DELETION` (§0 gate exempts `/account/**`).

---

## 8. Admin

### GET /admin/stats `auth,role=ADMIN`
200:
```json
{"activeUsers24h":12,"gapAnalysisSuccessRate":0.97,"voiceSessionSuccessRate":0.94,
 "avgGapAnalysisQueueWaitSeconds":1.1,"avgGapAnalysisProcessingSeconds":8.2,
 "cacheHitRate":0.24,"queueDepth":2,"oldestQueuedJobAgeSeconds":14,
 "aiProviderFailureRate":0.01,"generatedAt":"2026-09-12T10:00:00Z"}
```
Reads only the latest `metrics_snapshot` row per metric (observability.md §3-4) — never computed live.

## 9. Health (public)
`GET /actuator/health` → `{"status":"UP"|"DOWN"}` (DB+Redis reachability, 2s timeout each).
`GET /actuator/health/liveness` → process-alive only.
`GET /actuator/health/readiness` → includes dependency checks, used by the deploy platform before routing traffic.
