# security.md — Threat Model & Security Controls
Supersedes all prior versions. Incorporates FINAL-AUDIT.md items #1, #5, #11, #20, #22.

## 1. OWASP Top 10 Mapping

| Risk | Control | Enforced where |
|---|---|---|
| Broken access control | Ownership check in service layer; RBAC `@PreAuthorize`; PENDING_DELETION gate (§5) | Service layer, base controller |
| Cryptographic failures | BCrypt(12) passwords; TLS to Neon/Redis/R2; refresh tokens stored as sha256 hash only | Password service, `refresh_tokens.token_hash` |
| Injection | JPA parameterized queries only; Checkstyle rule bans string-concatenated JPQL/native SQL | Repository layer, CI static analysis |
| Insecure design | This threat model reviewed before implementation; idempotent job creation prevents duplicate-charge-class bugs by construction (database.md §4) | Architecture review |
| Security misconfiguration | Explicit CORS allow-list; security headers (§3); no stack traces in prod responses | Spring Security config |
| Vulnerable components | `dependency-check` every PR, fails on high/critical CVE | CI |
| Auth failures | Rate-limited login (fail-closed); refresh rotation + reuse detection with 10s grace window (§2); lockout after 5 failed attempts/15min | Auth service |
| Data integrity failures | RFC 7807 structured errors; AI output schema-validated before persistence (never trust external response shape) | DTO validation layer |
| Logging/monitoring failures | Structured JSON logs, correlation IDs, `SafeLogRedactor` (§6) | Logging filter |
| SSRF | No endpoint fetches a user-supplied URL; parsed documents are text-extracted only (PDFBox `PDFTextStripper`, POI text APIs), never rendered or executed — no path exists for an embedded reference in a resume to trigger a server-side fetch (audit #22) | By design, verified by API surface review |

## 2. Refresh Token Rotation, Reuse Detection, and the Concurrency Fix (audit #1)
- Opaque 256-bit refresh token, httpOnly+Secure+`SameSite=Strict` cookie, `Path=/api/v1/auth`, stored server-side as sha256 hash, rotated on every `/auth/refresh` call, `family_id` shared across a rotation chain.
- **The naive version of this design is broken under concurrent requests**: two API calls both receiving `401` near-simultaneously would each try to refresh; the second presents an already-superseded token and would trigger false reuse-detection, revoking the whole family and forcing an unwanted logout. This was found and fixed, not designed in from the start — see FINAL-AUDIT.md #1.
- **Fix, two layers**:
  1. **Frontend single-flight** (architecture.md §11): only one `/auth/refresh` call is ever in flight per browser tab; concurrent 401s share its result. This resolves the common single-tab case entirely.
  2. **Backend 10-second grace window** (defense-in-depth for the case the frontend fix can't cover — two separate tabs/devices): a refresh token presented within 10 seconds of being superseded is accepted as a benign race and the request succeeds normally, **provided** the request's IP/User-Agent matches the original rotation's recorded fingerprint (`refresh_tokens.ip_address`/`user_agent`, database.md §2). A mismatched fingerprint within the grace window, or any reuse after the grace window regardless of fingerprint, revokes the entire `family_id` immediately.
- RBAC: `USER`/`ADMIN`, `@PreAuthorize("hasRole('ADMIN')")` on `/admin/**`.

## 3. CSRF, CORS, Security Headers
- CSRF surface limited to `/auth/refresh` and `/auth/logout` (everything else uses `Authorization: Bearer`, immune to CSRF). Mitigated by `SameSite=Strict` + required custom header `X-Requested-With: XMLHttpRequest` (blocked on genuine cross-site requests by CORS preflight).
- CORS: exact frontend origin allow-list, `allowCredentials=true`, no wildcard.
- Headers: `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'self'; connect-src 'self' https://<api-domain>; img-src 'self' data:; script-src 'self'`, `Referrer-Policy: strict-origin-when-cross-origin`.

## 4. Input/Output Sanitization
- Bean Validation on every DTO. File uploads validated by magic bytes independent of client claims (api-spec.md §4/§6).
- AI output is treated as untrusted external input at every stage: schema-validated before persistence (architecture.md §6); any AI-derived free text rendered in Angular via default interpolation (`{{ }}`, auto-escaping) — never `innerHTML`/`bypassSecurityTrustHtml` (banned by an ESLint rule, testing-strategy.md §7). A successful prompt injection's worst realistic outcome is a nonsensical Gap Analyzer result, not script execution or data corruption, because AI output is never used to trigger further privileged action.

## 5. PENDING_DELETION Access Rules (audit #11)
While `users.status='PENDING_DELETION'`: login succeeds (needed to reach the cancel-deletion screen); every endpoint outside `/api/v1/auth/**` and `/api/v1/account/**` returns `403 account-pending-deletion` with the effective deletion date, enforced once at a base-controller level (api-spec.md §0) rather than duplicated per endpoint, which would risk a missed spot.

## 6. Secrets, Key Management, Logging Redaction
- Secrets via env vars only, injected by the hosting platforms, never committed.
- JWT signing keypair (RS256): rotation process is dual-key overlap — deploy accepting both old and new public keys for one deploy cycle before removing the old one, so in-flight access tokens don't fail mid-rotation.
- **PII redaction, two independent layers** (audit #20 — a single layer was insufficient): (1) a Checkstyle rule fails the build if a `log.*` call references a known PII accessor (`getEmail()`, `getName()`, `getTranscript()`, etc.) — catches direct leaks at compile time; (2) a `SafeLogRedactor` utility, applied to every string reaching the log appender (a Logback `TurboFilter` or encoder-level hook), regex-strips email-like and JWT-like (`eyJ...`) patterns as defense-in-depth against **indirect** leaks — e.g. an exception's message or a DTO's `toString()` accidentally echoing a field the Checkstyle rule wouldn't catch, since it only scans direct accessor calls in log statements, not everything that could end up in a string passed to `log.error(exception)`.

## 7. File Processing Safety (audit #5 — architecture.md §9 is the canonical design, summarized here for the threat model)
DOCX (zip-based) parsing is a known decompression-bomb vector; PDF parsing can hang on malformed input. Both are bounded: `ZipSecureFile` inflate-ratio/size limits before POI reads any entry, a 10-second wall-clock timeout on all extraction (killed and rejected past that), and a 20,000-character extraction ceiling regardless of upload size — bounding worst-case resource use independent of the 5MB upload cap, which alone would not have prevented a crafted small file from expanding catastrophically.

## 8. Abuse Prevention & Per-User AI Quotas
- Redis counters `quota:{userId}:{yyyy-mm-dd}:gap`/`:voice`, `INCR` + `EXPIRE ... NX` (idempotent-safe to call on every request, no crash-window key-leak risk — a prior version's "set TTL only on first increment" had exactly such a window, closed here by using the `NX` flag unconditionally).
- Limits: 20 Gap Analyzer/day, 10 Voice sessions/day per user — sized against Groq's actual free-tier rate limits, which are **configurable values re-verified at implementation time**, not hard-coded architectural constants (deployment.md §6). Per-user limits alone do not protect an organization-level provider cap if the user base grows past what one free-tier account supports in aggregate — this is called out explicitly in deployment.md §6 rather than assumed away.
- Fail-**closed**: Redis unreachable → `503 quota-service-unavailable`, never silently unlimited.
- Login/password-reset-request rate limits: same fail-closed policy (api-spec.md §2).

## 9. Explicitly Out of Scope for Phase 1
MFA, a dedicated WAF beyond platform defaults, field-level encryption of `jd_text`/`transcript` beyond storage-provider at-rest encryption — documented as deliberate scope decisions appropriate to this data's sensitivity level and the free-tier stack, not omissions.
