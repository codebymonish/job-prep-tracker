# Authentication Feature — Production Implementation Plan

> **Review status:** AWAITING USER APPROVAL — no files have been modified.
> Every finding below is based on live inspection of all specification documents and the actual repository. No assumptions were made about unchecked files.

---

## 1. Current Repository Assessment

### 1.1 Backend — Verified Against Disk

| Item | Verified State | Evidence |
|---|---|---|
| Spring Boot | 4.1.1 | `pom.xml` |
| Java | 21 | `pom.xml` |
| Web stack | `spring-boot-starter-webmvc` (blocking/servlet, Tomcat) | `pom.xml` |
| Redis client | `spring-boot-starter-data-redis-reactive` — **MISMATCH with blocking stack** | `pom.xml` §5 below |
| Spring Security | `spring-boot-starter-security` present, **zero configuration** — no `SecurityFilterChain` bean exists anywhere | Confirmed: only `JobTrackerApplication.java` in `src/main/java/` |
| JWT library | **ABSENT** — no jjwt, nimbus-jose-jwt, or spring-security-oauth2-jose | `pom.xml` — gap documented §6 |
| Mail | `spring-boot-starter-mail` present; `spring.mail.host` absent from `application.yaml` ✓ | `pom.xml`, `application.yaml` |
| Flyway | Enabled; `ddl-auto: validate`; **zero migrations exist** | `application.yaml`; `src/main/resources/db/` directory does not exist |
| Testcontainers | `postgres:17.11`, `redis:7.2.16` pinned; raw `PostgreSQLContainer` type (no `<?>`) | `TestcontainersConfiguration.java` ✓ |
| Business source | **Only** `JobTrackerApplication.java` | `src/main/java/com/jobtracker/` |
| `application.yaml` | No `spring.mail.host` ✓; `spring.data.redis.url: ${SPRING_DATA_REDIS_URL}` present ✓ | Verified |
| Active branch | `feature/authentication` | `implementation_history.md` §1 |

**Drift from `implementation_history.md`:** None. The document is accurate. Note: it lives at `backend/implementation_history.md`, not inside `requirements/` — confirmed on disk.

### 1.2 Frontend — Verified Against Disk

| Item | State |
|---|---|
| Angular version | 20.3.x (`@angular/core ^20.3.0`) |
| Architecture | Standalone components — `bootstrapApplication(App, appConfig)` in `main.ts`; no `NgModule` |
| Routing | `app.routes.ts` — `routes = []` (empty) |
| `HttpClient` | `provideHttpClient()` **NOT registered** in `app.config.ts` |
| Services / guards / interceptors | **None** |
| Auth components | **None** |
| State management | None; NgRx **not installed** (consistent with spec deliberate exclusion) |
| Tests | Only default scaffold `app.spec.ts` |
| TypeScript | `strict: true`, `noImplicitOverride`, `strictTemplates` — all enabled |

**Assessment:** The frontend is a completely blank Angular 20 scaffold. The entire auth layer must be built from scratch, which is an advantage — there is no conflicting code.

---

## 2. Architectural Decisions (DECISION GATE)

### Decision 2.1 — JWT Access Tokens [SPEC-MANDATED]

**Specification finding:**
- `architecture.md ADR-003`: *"JWT access token + rotating, DB-backed opaque refresh token"*
- `api-spec.md §0`: *"Bearer JWT (`Authorization: Bearer <token>`)"*
- `api-spec.md §2 /login` response: `{"accessToken":string,"expiresIn":900}`
- `security.md §6`: *"JWT signing keypair (RS256): rotation process is dual-key overlap"*
- `deployment.md §3`: `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` listed as required env vars

**Verdict:** Self-issued RS256 JWT access tokens are **[SPEC-MANDATED]**. 15-minute lifetime is **[SPEC-MANDATED]** (`expiresIn:900`).

**Required JWT library — gap identified:**

`pom.xml` has **no JWT library**. The correct library is `org.springframework.security:spring-security-oauth2-jose`. This is part of the Spring Security 7.x release train (managed by Spring Boot 4.1.1's BOM), provides `NimbusJwtEncoder` and `NimbusJwtDecoder` for RS256, and introduces zero version-conflict risk. No explicit version is needed — the BOM manages it.

> **[REQUIRED DEPENDENCY — JUSTIFIED]** `spring-security-oauth2-jose` must be added to `pom.xml`. This is not speculative: the spec mandates RS256 and this is the only non-redundant path to implement it. The alternative (`nimbus-jose-jwt` standalone) would duplicate a library Spring Security already manages internally.

**Required JWT claims:**

| Claim | Value |
|---|---|
| `sub` | User UUID (string) |
| `email` | User email — for display only; auth lookups always use `sub` |
| `role` | `USER` or `ADMIN` |
| `iat` | Issue time (UTC, Unix epoch seconds) |
| `exp` | `iat + 900` (UTC) |
| `jti` | Random UUID (supports future revocation auditing) |

> **[CAUTION]** `SafeLogRedactor` (security.md §6) strips `eyJ...` JWT patterns from logs. The `email` claim value must never be directly logged — the Checkstyle PII rule also catches `getEmail()` calls in `log.*` statements.

---

### Decision 2.2 — Redis: Reactive vs. Blocking [REQUIRES-USER-DECISION]

**The mismatch, precisely:**

The web stack is `spring-boot-starter-webmvc` (blocking/servlet — Tomcat thread-per-request). The Redis starter is `spring-boot-starter-data-redis-reactive`, which provides `ReactiveRedisTemplate` backed by Lettuce's reactive pipeline. Auth rate-limiting (login: 5/15 min per IP+email; password-reset: 3/hour per email — both fail-closed per spec) requires synchronous Redis calls from within blocking service methods.

**Option A — [RECOMMENDED]: Replace reactive starter with blocking starter**

Replace `spring-boot-starter-data-redis-reactive` → `spring-boot-starter-data-redis` (and the corresponding test starter). This provides `StringRedisTemplate` / `RedisTemplate`, which are fully idiomatic for a blocking MVC stack.

- No `.block()` anywhere in service code.
- Same Lettuce driver underneath — no behavioural change for existing Redis connectivity.
- `@ServiceConnection(name="redis")` in `TestcontainersConfiguration.java` works identically with either starter.
- `properties = "spring.data.redis.url="` override in `JobTrackerApplicationIT.java` remains required with either starter.
- **Justification for pom.xml change:** This is an actual architectural mismatch producing an anti-pattern in all Redis-using service code. Hard Operating Rule 1 permits a pom.xml change when an actual runtime/architectural problem requires it — this qualifies.

**Option B: Keep reactive, use `.block()` at the boundary**

- No pom.xml change.
- All Redis calls in `RateLimitService` use `.block(Duration.ofSeconds(2))` on `ReactiveRedisTemplate` operations.
- Safe on Tomcat worker threads (not Netty event loops), but architecturally inconsistent and misleading.
- Every `.block()` site requires a comment explaining why it is safe here.

**Option C: Use both starters simultaneously** — Not recommended (redundant, risk of dual connection pools).

> **[REQUIRES-USER-DECISION]:** Confirm **Option A** (replace, recommended) or **Option B** (keep reactive, use `.block()`). Plan execution is blocked until this is resolved.

---

### Decision 2.3 — Email Sending: Async vs. Sync [REQUIRES-USER-DECISION]

The spec does not prescribe synchronous or asynchronous email. However, Hard Operating Rule 4 requires mail sending to tolerate being unconfigured. Sending email synchronously within the registration transaction would couple `201` success to SMTP availability.

**[RECOMMENDED]: Asynchronous email** via Spring `@Async` with a bounded `ThreadPoolTaskExecutor` declared in `AsyncConfig.java`. Registration commits user + token to DB, returns `201`, fires async email task. If SMTP is down or unconfigured, the token record persists and the email can be re-sent; registration is not rolled back.

> **[REQUIRES-USER-DECISION]:** Confirm **async** (recommended) or **synchronous** email.

---

### Decision 2.4 — Refresh Token Expiry [REQUIRES-USER-DECISION]

The spec does not prescribe a specific refresh token lifetime. **Recommended: 7 days.** After 7 days without activity, re-login is required.

---

### Decision 2.5 — Auth Token TTLs [REQUIRES-USER-DECISION]

Spec does not prescribe specific TTLs. **Recommended:** Email-verify token = 24 hours; Password-reset token = 1 hour. These are reasonable security defaults and can be adjusted during implementation.

---

## 3. Authentication Architecture Overview

```
Browser
  │
  ├─ POST /api/v1/auth/register       ──► AuthController ──► AuthService
  │                                                    ├──► UserRepository (Postgres)
  │                                                    ├──► AuthTokenRepository (Postgres)
  │                                                    └──► EmailService (async, optional SMTP)
  │
  ├─ GET  /api/v1/auth/verify?token=  ──► AuthController ──► AuthService
  │                                                    ├──► AuthTokenRepository
  │                                                    └──► UserRepository (status → ACTIVE)
  │
  ├─ POST /api/v1/auth/login          ──► AuthController ──► RateLimitService (Redis, fail-closed)
  │                                                    ├──► AuthService
  │                                                    ├──► UserRepository
  │                                                    ├──► RefreshTokenRepository (insert)
  │                                                    └──► JwtService (RS256 sign)
  │
  ├─ POST /api/v1/auth/refresh        ──► AuthController ──► AuthService
  │  Cookie: refreshToken (HttpOnly)                   ├──► RefreshTokenRepository (SELECT FOR UPDATE)
  │  X-Requested-With: XMLHttpRequest                  ├──► RefreshTokenRepository (rotate)
  │                                                    └──► JwtService (sign new JWT)
  │
  ├─ Any authenticated request
  │    Authorization: Bearer <JWT>
  │         └──► BearerTokenAuthenticationFilter ──► NimbusJwtDecoder (RS256 verify, stateless)
  │
  ├─ POST /api/v1/auth/logout         ──► AuthController ──► AuthService
  │                                                    └──► RefreshTokenRepository (revoke family)
  │
  ├─ POST /api/v1/auth/password-reset-request  ──► RateLimitService (Redis)
  │                                             ──► AuthService (always 202)
  │
  └─ POST /api/v1/auth/password-reset  ──► AuthService
                                             ├──► AuthTokenRepository (consume token)
                                             ├──► UserRepository (new password hash)
                                             └──► RefreshTokenRepository (revoke all families)
```

**Filter chain order (Spring Security):**

1. `CorrelationIdFilter` — inject/propagate `X-Correlation-Id`
2. Spring Security's security filter chain
3. `BearerTokenAuthenticationFilter` (native OAuth2 Resource Server) — extract Bearer token, populate `SecurityContext`
4. `PendingDeletionCheckFilter` — reject non-account/auth paths for `PENDING_DELETION` users
5. `DispatcherServlet` → `AuthController` / other controllers

---

## 4. Backend Package / Class Structure

All new code under `com.jobtracker`, following `architecture.md §12` package boundaries exactly.

```
com.jobtracker/
├── auth/
│   ├── controller/
│   │   └── AuthController.java              [NEW]
│   ├── service/
│   │   ├── AuthService.java                 [NEW]
│   │   ├── JwtService.java                  [NEW]
│   │   ├── RateLimitService.java            [NEW]
│   │   └── EmailService.java                [NEW]
│   ├── repository/
│   │   ├── UserRepository.java              [NEW]
│   │   ├── RefreshTokenRepository.java      [NEW]
│   │   └── AuthTokenRepository.java         [NEW]
│   ├── entity/
│   │   ├── User.java                        [NEW] @Entity, @Version, @PreUpdate
│   │   ├── RefreshToken.java                [NEW] @Entity
│   │   └── AuthToken.java                   [NEW] @Entity, token_type enum
│   ├── dto/
│   │   ├── RegisterRequest.java             [NEW] Bean Validation mirrors spec
│   │   ├── RegisterResponse.java            [NEW] {id, email, status}
│   │   ├── LoginRequest.java                [NEW]
│   │   ├── LoginResponse.java               [NEW] {accessToken, expiresIn:900}
│   │   ├── RefreshResponse.java             [NEW]
│   │   ├── PasswordResetRequestDto.java     [NEW]
│   │   └── PasswordResetDto.java            [NEW] {token, newPassword}
│   ├── mapper/
│   │   └── UserMapper.java                  [NEW] MapStruct
│   └── config/
│       └── JwtProperties.java               [NEW] @ConfigurationProperties("app.jwt")
│
└── common/
    ├── config/
    │   ├── SecurityConfig.java              [NEW] SecurityFilterChain
    │   ├── PasswordConfig.java              [NEW] BCryptPasswordEncoder(12) bean
    │   ├── AsyncConfig.java                 [NEW] @EnableAsync, bounded executor
    │   └── ProblemDetailConfig.java         [NEW] @ControllerAdvice, RFC 7807
    ├── filter/
    │   ├── CorrelationIdFilter.java         [NEW] OncePerRequestFilter
    │   └── PendingDeletionCheckFilter.java  [NEW] security.md §5
    └── logging/
        └── SafeLogRedactor.java             [NEW] Logback TurboFilter
```

**MapStruct** is mandated by `architecture.md §12` ("MapStruct mappers sit at each controller boundary"). It is **not in pom.xml**.

> **[REQUIRED DEPENDENCY — JUSTIFIED]** `org.mapstruct:mapstruct` (runtime) + `org.mapstruct:mapstruct-processor` (annotation processor, `provided` scope or `annotationProcessorPaths`). Version `1.6.3` — latest stable, Java 21 compatible, Spring Boot 4.x compatible. Explicitly prescribed by architecture spec.

---

## 5. Database / Flyway Changes

### 5.1 New Migration: `V1__create_auth_schema.sql`

**Path:** `src/main/resources/db/migration/V1__create_auth_schema.sql`

Zero migrations currently exist. The `src/main/resources/db/` directory does not exist. This migration creates it. Auth tables are scoped to V1; remaining domain tables (`applications`, `resumes`, `async_jobs`, etc.) go in later migrations as those features are built.

**Tables (exact DDL from `database.md §2`):**

```sql
-- V1__create_auth_schema.sql
-- All TIMESTAMPTZ values are UTC by definition.
-- Java code must use Instant.now() or ZonedDateTime.now(ZoneOffset.UTC), never LocalDateTime.now().

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
CREATE UNIQUE INDEX uq_users_email_normalized
  ON users(email_normalized) WHERE status <> 'DELETED';

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  family_id UUID NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  superseded_at TIMESTAMPTZ,    -- set at rotation; grace window = superseded_at + 10s
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
```

### 5.2 New Migration: `V2__create_audit_schema.sql`

Auth writes `LOGIN` / `LOGOUT` events to `audit_log` (per `database.md §2` CHECK constraint). The table must exist before auth integration tests can write audit rows. For auth purposes, `AuthService` writes directly to `audit_log` — the AOP-based `audit/` package in `architecture.md §12` is a separate, later feature.

```sql
-- V2__create_audit_schema.sql
CREATE TABLE audit_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID,   -- nullable: nulled on account deletion
  entity_type VARCHAR(50) NOT NULL,
  entity_id UUID NOT NULL,
  action VARCHAR(30) NOT NULL
    CHECK (action IN ('CREATE','UPDATE','DELETE','STATUS_CHANGE','LOGIN','LOGOUT')),
  before_json JSONB,
  after_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_log(user_id, created_at DESC);
```

### 5.3 UTC Enforcement (Hard Operating Rule 7)

All `Instant` / `OffsetDateTime` values in Java must use `Instant.now()` (UTC by construction) or `ZonedDateTime.now(ZoneOffset.UTC)`. Never `LocalDateTime.now()`. The Failsafe `user.timezone=UTC` property covers tests. Production code must not depend on JVM default timezone.

### 5.4 Token Cleanup Strategy

`auth_tokens` and expired/revoked `refresh_tokens` accumulate over time. A lightweight scheduled cleanup job — following the `architecture.md §5` singleton-guard pattern (Postgres advisory lock) — should run periodically. This is low priority and out of scope for the initial auth implementation, but the row counts should be monitored.

---

## 6. Security Model and Token Lifecycle

### 6.1 Spring Security Configuration (`SecurityConfig.java`)

```
Public (no JWT required):
  POST  /api/v1/auth/register
  GET   /api/v1/auth/verify
  POST  /api/v1/auth/login
  POST  /api/v1/auth/refresh       (CSRF-mitigated by SameSite=Strict + X-Requested-With)
  POST  /api/v1/auth/password-reset-request
  POST  /api/v1/auth/password-reset
  GET   /actuator/health/**

Requires valid JWT:
  /api/v1/**  (catch-all)

Requires ROLE_ADMIN:
  /api/v1/admin/**  (@PreAuthorize on controller method)

CSRF:
  Disabled globally (JWT-based, stateless).
  Refresh/logout CSRF mitigated by: SameSite=Strict cookie + X-Requested-With: XMLHttpRequest header.

CORS:
  Exact allowlist: ${CORS_ALLOWED_ORIGIN} (single origin from env).
  allowCredentials = true (required for refresh cookie).
  No wildcard.

Security response headers (every response):
  Strict-Transport-Security: max-age=31536000; includeSubDomains
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Content-Security-Policy: default-src 'self'; connect-src 'self' <api-domain>; img-src 'self' data:; script-src 'self'
  Referrer-Policy: strict-origin-when-cross-origin
```

### 6.2 JWT Signing Key Configuration

`application.yaml` additions (these are required app configuration, not mail — Hard Operating Rule 4 does not restrict them):

```yaml
app:
  jwt:
    private-key: ${JWT_PRIVATE_KEY}
    public-key: ${JWT_PUBLIC_KEY}
    expiry-seconds: 900
```

`JwtProperties.java` — `@ConfigurationProperties("app.jwt")` record. Binds these at startup. If env vars are missing, the app fails fast with a clear message (desired behavior in production; integration tests override with test-generated keys).

`JwtService.java` — creates `NimbusJwtEncoder` from PKCS#8 RSA private key and `NimbusJwtDecoder` from X.509 public key. Both from `spring-security-oauth2-jose`.

**Key rotation (dual-key overlap, security.md §6):** During rotation, the `JwtDecoder` must accept both the old and new public keys for one deploy cycle. Implementation: configure `NimbusJwtDecoder` with a `JWKSet` containing both public keys, or a composite decoder that tries each in sequence. The encoder always uses only the new private key.

**Test key strategy:** Integration tests must not need production RSA keys. `@SpringBootTest` for auth ITs overrides `app.jwt.private-key` and `app.jwt.public-key` with a test-generated in-memory RSA keypair via `@TestConfiguration` or `properties = {"app.jwt.private-key=...", "app.jwt.public-key=..."}`.

### 6.3 Password Hashing [SPEC-MANDATED]

BCrypt strength 12 — `security.md §1` explicitly specifies `BCrypt(12)`. BCrypt's 72-byte input maximum aligns exactly with the spec's `≤72 char` password constraint. `PasswordConfig.java` defines the `BCryptPasswordEncoder(12)` bean.

### 6.4 Refresh Token Generation and Storage

- **Raw token:** `SecureRandom` → 32 bytes → URL-safe Base64 (no padding) → 43 characters
- **Stored value:** `SHA-256(raw token UTF-8 bytes)` → hex string → stored in `refresh_tokens.token_hash`
- **Cookie:** `Set-Cookie: refreshToken=<rawToken>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800`
- **family_id:** Random UUID generated at login; inherited through all rotations; used for family revocation

### 6.5 Refresh Rotation and 10-Second Grace Window (ADR-006)

On `POST /api/v1/auth/refresh`:

1. Extract raw token from cookie.
2. Hash it. `SELECT ... FROM refresh_tokens WHERE token_hash = ? FOR UPDATE` (row-level lock prevents concurrent rotation races).
3. Not found → `401 refresh-invalid`.
4. `revoked_at IS NOT NULL` → family already revoked → `401 refresh-invalid`.
5. `superseded_at IS NOT NULL`:
   - `now() - superseded_at > 10s` → revoke entire family → `401 refresh-invalid`.
   - `now() - superseded_at ≤ 10s` AND IP + User-Agent match → grace window → return the replacement token's JWT (the `replaced_by` row), no new rotation.
   - `now() - superseded_at ≤ 10s` BUT fingerprint mismatch → revoke entire family → `401 refresh-invalid`.
6. `expires_at < now()` → `401 refresh-invalid`.
7. Valid: INSERT new row (`family_id` inherited, new `token_hash`, new `expires_at`). UPDATE old row: `superseded_at = now()`, `replaced_by = <new id>`. Issue new RS256 JWT. Return 200 with new cookie.

The `SELECT FOR UPDATE` in step 2 is the correct concurrency guard: the second concurrent caller blocks until the first completes, then sees `superseded_at IS NOT NULL` and takes the grace-window path.

### 6.6 Login Account Lockout

`users.failed_login_count` / `users.locked_until`: After 5 consecutive credential failures, `locked_until = now() + 15 minutes`. While locked, return `401 invalid-credentials` without revealing the lockout state (prevents enumeration). On successful login, reset both fields.

This is a second, independent layer from the Redis rate limit. Redis rate-limits at the network/IP level; `locked_until` locks a specific account regardless of which IP the attempt comes from.

---

## 7. Email / Verification / Reset Flow

### 7.1 Registration and Verification

1. `POST /auth/register` → validate DTO → normalize email (`toLowerCase().trim()`) → check `uq_users_email_normalized` (→ `409 email-exists` if hit) → BCrypt(12) password → INSERT `users` (status=UNVERIFIED) → generate 32-byte random token → SHA-256 hash → INSERT `auth_tokens` (type=EMAIL_VERIFY, expires_at = now() + 24h) → async `EmailService.sendVerificationEmail(userId, rawToken)`.
2. Email link: `GET /api/v1/auth/verify?token=<rawToken>`.
3. `GET /auth/verify?token=<t>` → SHA-256 hash → find `auth_tokens` by hash → validate (type=EMAIL_VERIFY, consumed_at IS NULL, expires_at > now()) → transaction: `consumed_at = now()`, `users.status = ACTIVE`.
4. Any validation failure → `400 invalid-token` (single error code for all failure modes — no enumeration of which condition failed).

### 7.2 Password Reset

1. `POST /auth/password-reset-request` → always return `202` (no enumeration). Redis rate-check: 3/hour per normalized email (fail-closed → `503`). If user found AND status ∈ {ACTIVE, PENDING_DELETION}: generate 32-byte token → hash → INSERT `auth_tokens` (type=PASSWORD_RESET, expires_at = now() + 1h) → async email.
2. `POST /auth/password-reset` → SHA-256 hash token → find `auth_tokens` (same 3 validity checks) → transaction: BCrypt(12) new password → UPDATE `users.password_hash`, reset `failed_login_count = 0`, `locked_until = null` → `consumed_at = now()` → `UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL` (revoke all active families).

### 7.3 Mail Configuration Tolerance (Hard Operating Rule 4)

`EmailService` injects `Optional<JavaMailSender>` (or `@Autowired(required=false)`). If absent: log WARN — `"Mail sender not configured — skipping email for userId={userId}"` — and return silently. The async task catches all `MailException`s internally. `spring.mail.host` must never appear in `application.yaml`; it belongs only in `application-local.yaml.example` (already documented there, commented out).

---

## 8. Angular Architecture and Required Frontend Changes

### 8.1 State Assessment

Pristine Angular 20 scaffold. Zero conflicts. `HttpClient` not configured. Routes empty. This is a clean greenfield implementation within an existing project skeleton.

### 8.2 Required Changes to Existing Files

| File | Change |
|---|---|
| `src/app/app.config.ts` | Add `provideHttpClient(withInterceptors([authInterceptor, correlationIdInterceptor, errorInterceptor]))` |
| `src/app/app.routes.ts` | Replace `[]` with full route tree |
| `src/app/app.ts` | Remove Angular scaffold placeholder; keep only `<router-outlet />` and `RouterOutlet` import |
| `src/app/app.html` | Replace scaffold HTML with `<router-outlet />` only |

### 8.3 New Frontend Files

```
src/app/
├── core/
│   ├── services/
│   │   └── auth.service.ts              login, register, refresh, logout, password-reset flows
│   ├── state/
│   │   └── auth-state.service.ts        BehaviorSubject<AuthState> — single auth truth
│   ├── interceptors/
│   │   ├── auth.interceptor.ts          Bearer header + 401→refresh→retry (single-flight)
│   │   ├── correlation-id.interceptor.ts  X-Correlation-Id header
│   │   └── error.interceptor.ts         RFC 7807 ProblemDetail → in-app error
│   ├── guards/
│   │   ├── auth.guard.ts                Unauthenticated → /auth/login
│   │   ├── guest.guard.ts               Authenticated → /applications
│   │   ├── verified.guard.ts            UNVERIFIED → pending-verification state
│   │   └── admin.guard.ts               Non-ADMIN → /applications
│   └── models/
│       ├── auth.models.ts               AuthState, User, LoginRequest, RegisterRequest interfaces
│       └── api-error.models.ts          ProblemDetail interface
│
├── features/
│   └── auth/
│       ├── auth.routes.ts               Lazy-loaded /auth/** child routes
│       ├── login/
│       │   ├── login.component.ts
│       │   └── login.component.html
│       ├── register/
│       │   ├── register.component.ts
│       │   └── register.component.html
│       ├── verify/
│       │   ├── verify.component.ts      Reads ?token=, calls verify, shows result
│       │   └── verify.component.html
│       ├── password-reset-request/
│       │   ├── password-reset-request.component.ts
│       │   └── password-reset-request.component.html
│       └── password-reset/
│           ├── password-reset.component.ts  Reads ?token=, shows form
│           └── password-reset.component.html
│
└── environments/
    ├── environment.ts                   { apiBaseUrl: 'http://localhost:8080' }
    └── environment.prod.ts              { apiBaseUrl: '' } (same-origin in prod)
```

### 8.4 Route Structure

```typescript
// app.routes.ts
export const routes: Routes = [
  { path: '', redirectTo: '/applications', pathMatch: 'full' },
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then(m => m.AUTH_ROUTES),
    canActivate: [guestGuard]
  },
  {
    path: 'applications',
    loadChildren: () => import('./features/applications/applications.routes').then(m => m.APP_ROUTES),
    canActivate: [authGuard, verifiedGuard]
  },
  { path: '**', redirectTo: '/applications' }
];

// features/auth/auth.routes.ts
export const AUTH_ROUTES: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'verify', component: VerifyComponent },
  { path: 'password-reset-request', component: PasswordResetRequestComponent },
  { path: 'password-reset', component: PasswordResetComponent },
  { path: '', redirectTo: 'login', pathMatch: 'full' }
];
```

### 8.5 Auth State Model

```typescript
// auth-state.service.ts
export interface AuthState {
  accessToken: string | null;
  tokenExpiresAt: number | null;  // Unix ms
  user: { id: string; email: string; role: 'USER' | 'ADMIN'; status: string } | null;
  isRefreshing: boolean;
}
```

Access token stored **in memory only** (BehaviorSubject). Never `localStorage`, never `sessionStorage`. Refresh token lives in an HttpOnly cookie — never accessible to JavaScript.

**Page-reload implication:** In-memory token is lost. On startup the app calls `/auth/refresh` to restore session silently using the HttpOnly cookie. This must complete before any guarded route activates (use `APP_INITIALIZER`).

### 8.6 Complete Browser Auth Lifecycle

| Step | Action |
|---|---|
| 1. App startup | `APP_INITIALIZER` runs `AuthService.tryRestoreSession()` |
| 2. Determine token availability | `AuthStateService.state$.value.accessToken === null` on cold start |
| 3. Session restore | `POST /auth/refresh` with `withCredentials: true` + `X-Requested-With: XMLHttpRequest` |
| 3a. 200 | Store new JWT in `AuthStateService`; navigate to originally requested route |
| 3b. 401 | No valid session; if route is protected, redirect to `/auth/login?returnUrl=...` |
| 4. Authenticated request | `authInterceptor` reads token from `AuthStateService`, adds `Authorization: Bearer` header |
| 5. Token near expiry | Optional proactive refresh if `tokenExpiresAt - now() < 30s` |
| 6. 401 response | Interceptor catches it; checks `isRefreshing` |
| 7. Single-flight refresh | If not already refreshing: set `isRefreshing=true`, create `refreshObservable$` with `shareReplay(1)`. Concurrent 401s subscribe to the same observable — no second HTTP call |
| 8. Retry original request | On refresh success: `switchMap` retries original request exactly once with new token |
| 9. Refresh failure | Set `isRefreshing=false`; proceed to step 10 |
| 10. Session cleanup | `AuthStateService.clearSession()`; best-effort `POST /auth/logout` to revoke server-side family |
| 11. Navigate to login | `Router.navigate(['/auth/login'], { queryParams: { returnUrl: <current path> } })` |

**Infinite-loop guards:**
- Do not apply 401→retry logic to requests going to `/auth/refresh` itself.
- Do not apply 401→retry logic more than once per original request (use a `hasRetried` flag on the cloned request).
- Do not attach `Authorization: Bearer` headers to public auth endpoints.

### 8.7 Token Storage Security Rationale

| Location | Auth use | Reason |
|---|---|---|
| In-memory `BehaviorSubject` | Access token (JWT) | XSS cannot read process memory; loss on reload mitigated by session restore |
| HttpOnly cookie (server-set) | Refresh token | Invisible to JS; CSRF prevented by SameSite=Strict + X-Requested-With |
| `localStorage` | **Never** | Vulnerable to XSS |
| `sessionStorage` | **Never** | Tab-scoped but still JS-readable |

### 8.8 Form Validation

All auth forms use Angular Reactive Forms. Client validators mirror backend Bean Validation constraints exactly:
- email: valid RFC5322 format, ≤255 chars
- password: ≥8 chars, ≤72 chars, ≥1 uppercase, ≥1 digit
- name: 1–255 chars

Server-side `fieldErrors` from `400 validation-error` responses are mapped back to form controls. The server is always the authoritative validator.

### 8.9 Accessibility

All forms: semantic `<label>` elements bound to inputs, ARIA roles, `aria-live="polite"` on error regions, keyboard-navigable, WCAG 2.1 AA color contrast. `cy.checkA11y()` in E2E tests per `testing-strategy.md §5`.

---

## 9. API Request / Response / Error Contracts

### 9.1 Registration

```
POST /api/v1/auth/register
Content-Type: application/json
{ "email": "user@example.com", "password": "Str0ngPass!", "name": "Alice" }

201 Created
{ "id": "uuid", "email": "user@example.com", "status": "UNVERIFIED" }

409 Conflict
{ "type": "https://api.jobtracker.app/errors/email-exists", "title": "Email already registered",
  "status": 409, "detail": "...", "instance": "/api/v1/auth/register", "correlationId": "uuid" }

400 Bad Request
{ "type": ".../validation-error", "status": 400, ...,
  "fieldErrors": [{"field": "password", "message": "must be at least 8 characters"}] }
```

### 9.2 Email Verification

```
GET /api/v1/auth/verify?token=<rawToken>

200 OK    { "status": "ACTIVE" }

400 Bad Request    (covers: not found, already consumed, expired — same code for all)
{ "type": ".../invalid-token", "title": "Invalid or expired token", "status": 400, ... }
```

### 9.3 Login

```
POST /api/v1/auth/login
{ "email": "user@example.com", "password": "Str0ngPass!" }

200 OK
Set-Cookie: refreshToken=<rawToken>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800
{ "accessToken": "<RS256 JWT>", "expiresIn": 900 }

401  { "type": ".../invalid-credentials", "status": 401, ... }     (unknown email OR wrong password — identical)
403  { "type": ".../account-not-verified", "status": 403, ... }
403  { "type": ".../account-disabled", "status": 403, ... }
429  Retry-After: <seconds>   { "type": ".../rate-limited", "status": 429, ... }
503  { "type": ".../quota-service-unavailable", "status": 503, ... }   (Redis down — fail-closed)
```

### 9.4 Refresh

```
POST /api/v1/auth/refresh
Cookie: refreshToken=<rawToken>
X-Requested-With: XMLHttpRequest

200 OK
Set-Cookie: refreshToken=<newRawToken>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800
{ "accessToken": "<new RS256 JWT>", "expiresIn": 900 }

401
Set-Cookie: refreshToken=; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=0
{ "type": ".../refresh-invalid", "status": 401, ... }
```

### 9.5 Logout

```
POST /api/v1/auth/logout
Authorization: Bearer <JWT>

204 No Content
Set-Cookie: refreshToken=; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=0
```

### 9.6 Password Reset Request

```
POST /api/v1/auth/password-reset-request
{ "email": "user@example.com" }

202 Accepted    — ALWAYS, regardless of whether the email exists (no enumeration)
{ "message": "If an account with that email exists, a reset link has been sent." }

503  (Redis down during rate-check — fail-closed)
```

### 9.7 Password Reset

```
POST /api/v1/auth/password-reset
{ "token": "<rawToken>", "newPassword": "NewStr0ng!1" }

200 OK    { "message": "Password reset successful." }

400    invalid-token or validation-error
```

### 9.8 Error Response Structure (RFC 7807 — all errors)

Content-Type: `application/problem+json`

```json
{
  "type": "https://api.jobtracker.app/errors/{error-code}",
  "title": "Human-readable summary",
  "status": 400,
  "detail": "Safe client-facing detail",
  "instance": "/api/v1/auth/login",
  "correlationId": "uuid"
}
```

Validation errors additionally include `"fieldErrors": [{"field": "...", "message": "..."}]`.

No stack traces in any response from any profile. `ProblemDetailConfig` `@ControllerAdvice` catches all exceptions and maps them to `ProblemDetail` — exception `getMessage()` is sanitized through `SafeLogRedactor` before being included in `detail`.

---

## 10. Redis Usage

### 10.1 Auth-Scope Redis Requirements (per spec)

| Use case | Key pattern | TTL / limit | Fail mode |
|---|---|---|---|
| Login rate limit | `ratelimit:login:{ip}:{email_normalized}` | 15 min window, 5 attempts | fail-closed → 503 |
| Password-reset rate limit | `ratelimit:pwreset:{email_normalized}` | 1 hour window, 3 attempts | fail-closed → 503 |

AI quota counters (`quota:{userId}:...`) are **out of scope** for this auth plan — they are gap-analysis / voice-session concerns.

**Implementation pattern:** `INCR <key>` then `EXPIRE <key> <seconds> NX` (set TTL only if not already set — idempotent, no crash-window key-leak risk per `security.md §8`).

### 10.2 Reactive/Blocking Implementation (Decision 2.2)

**If Option A (recommended):** `RateLimitService` uses `StringRedisTemplate.execute(RedisCallback<Long>)` — one blocking call per check. `RedisConnectionFailureException` → `ServiceUnavailableException` → `503`.

**If Option B:** `RateLimitService` uses `reactiveRedisTemplate.execute(...).block(Duration.ofSeconds(2))`. Each `.block()` site carries a mandatory code comment: `// Safe: Tomcat worker thread (not a Netty event loop). Timeout bounded at 2s.`

### 10.3 Redis Unavailability Behavior (fail-closed, SPEC-MANDATED)

Any `RedisConnectionFailureException` (or `.block()` timeout) in `RateLimitService` → throw `QuotaServiceUnavailableException` → `ProblemDetailConfig` maps it to `503 quota-service-unavailable`. The check must never silently allow the request through.

---

## 11. Observability / Audit Requirements

### 11.1 Structured Log Events (observability.md §1)

All events logged as structured JSON via existing Logback configuration. Levels per spec.

| Event | Level | Safe fields (no PII) |
|---|---|---|
| Registration | INFO | `userId`, `correlationId`, `event=USER_REGISTERED` |
| Email verified | INFO | `userId`, `event=EMAIL_VERIFIED` |
| Login success | INFO | `userId`, `event=LOGIN_SUCCESS`, `ip` (hash or partial) |
| Login failure | WARN | `correlationId`, `event=LOGIN_FAILED`, `reason`, `ip` |
| Rate limit hit | WARN | `correlationId`, `event=RATE_LIMIT_HIT`, `endpoint`, `ip` |
| Account locked | WARN | `userId`, `event=ACCOUNT_LOCKED`, `lockedUntil` |
| Refresh reuse detected → family revoked | WARN | `userId`, `familyId`, `event=REFRESH_REUSE_DETECTED` |
| Refresh grace-window accepted | INFO | `userId`, `familyId`, `event=REFRESH_GRACE_WINDOW_ACCEPTED` |
| Logout | INFO | `userId`, `event=LOGOUT` |
| Password reset requested | INFO | `event=PASSWORD_RESET_REQUESTED` (no userId — prevents confirming existence) |
| Password reset completed | INFO | `userId`, `event=PASSWORD_RESET_COMPLETED` |

Checkstyle rule enforced: no `getEmail()`, `getName()`, `getTranscript()` calls inside `log.*` statements. `SafeLogRedactor` as second layer strips `eyJ...` (JWT) and email-pattern strings from all log output.

### 11.2 Database Audit Log

`AuthService` writes directly to `audit_log`:
- Login success: `action=LOGIN`, `entity_type=users`, `entity_id={userId}`
- Logout: `action=LOGOUT`, `entity_type=users`, `entity_id={userId}`

### 11.3 Micrometer Counters (auto-exported via Actuator)

| Metric | Tags |
|---|---|
| `auth.registration.total` | `outcome: success\|duplicate_email\|validation_error` |
| `auth.login.total` | `outcome: success\|invalid_credentials\|not_verified\|disabled\|rate_limited\|redis_error` |
| `auth.token.refresh.total` | `outcome: success\|invalid\|reuse_detected\|grace_window` |
| `auth.password_reset.requests.total` | |
| `auth.password_reset.completions.total` | `outcome: success\|invalid_token` |

---

## 12. Dependency Review

### 12.1 New Dependencies Required for Auth

| Dependency | Artifact | Justification | Version |
|---|---|---|---|
| JWT encode/decode | `org.springframework.security:spring-security-oauth2-jose` | RS256 JWT via `NimbusJwtEncoder`/`NimbusJwtDecoder`. **Absent from pom.xml.** Spec mandates RS256 with `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`. BOM-managed — no explicit version needed. | BOM (Spring Boot 4.1.1) |
| DTO mapping | `org.mapstruct:mapstruct` | Mandated by `architecture.md §12`. Absent from pom.xml. | `1.6.3` |
| MapStruct processor | `org.mapstruct:mapstruct-processor` | Annotation processor for MapStruct (compile/provided scope). | `1.6.3` |
| Blocking Redis (if Decision 2.2 = A) | Replace `spring-boot-starter-data-redis-reactive` → `spring-boot-starter-data-redis` | Architectural mismatch — blocking MVC stack with reactive Redis client. Same Lettuce driver. | BOM-managed |

### 12.2 What Is Intentionally NOT Added

| Library | Why excluded |
|---|---|
| `io.jsonwebtoken:jjwt-*` | Redundant — `spring-security-oauth2-jose` uses Nimbus internally; adding jjwt would introduce a second JWT stack |
| `com.nimbusds:nimbus-jose-jwt` standalone | Already bundled transitively through `spring-security-oauth2-jose` |
| `spring-boot-starter-oauth2-resource-server` | Its auto-configuration conflicts with the custom login flow; direct use of `spring-security-oauth2-jose` is correct |
| NgRx | Spec explicitly excludes it; `architecture.md §11` states NgRx deliberately not introduced |

### 12.3 Compatibility

- `spring-security-oauth2-jose`: Spring Security 7.x — same version train as Spring Boot 4.1.1. Zero conflict risk.
- MapStruct `1.6.3`: Java 21 certified. Spring Boot 4.x compatible (annotation processing works with `maven-compiler-plugin` configured with `annotationProcessorPaths`).
- `spring-boot-starter-data-redis` (blocking): Identical Lettuce driver, BOM-managed. `@ServiceConnection(name="redis")` and the `spring.data.redis.url=` override pattern in IT work identically.

---

## 13. Test Plan

All backend integration tests: named `*IT.java`, Failsafe plugin, `@SpringBootTest(properties = "spring.data.redis.url=")`, `@Import(TestcontainersConfiguration.class)`, raw `PostgreSQLContainer` type, pinned images `postgres:17.11` / `redis:7.2.16`.

### 13.1 Backend Unit Tests (Surefire — no Docker, no Spring context)

| Class | Key scenarios |
|---|---|
| `AuthServiceTest` | Register: success, duplicate email (409), password hashed (BCrypt verify); Login: valid, wrong password, UNVERIFIED account, DISABLED account, locked account; Refresh: valid rotation, grace-window same fingerprint (succeeds), grace-window different fingerprint (family revoked), post-grace-window (family revoked), expired token; Logout: family revoked; Password reset: valid, token expired, token consumed |
| `JwtServiceTest` | Sign+verify RS256 round-trip; claims present (sub, email, role, exp); expired token throws; wrong key throws; dual-key rotation: old key still verifies during overlap |
| `RateLimitServiceTest` | Under limit → allow; at limit → block (429); Redis throws → fail-closed (503); INCR+EXPIRE-NX atomicity (mock template) |
| `EmailServiceTest` | Sender present → sends; sender absent (Optional empty) → logs WARN, no exception; async task catches MailException internally |
| `ProblemDetailConfigTest` | Each exception type maps to correct HTTP status and error code; no stack trace in body; `correlationId` present |

### 13.2 Backend Integration Tests (Failsafe — Testcontainers)

| Class | Key scenarios |
|---|---|
| `AuthControllerIT` | Full HTTP round-trips for all 7 endpoints; exact status codes and response bodies from §9 |
| `RefreshTokenRotationIT` | Two concurrent `/auth/refresh` calls same token → assert only one rotation (SELECT FOR UPDATE); second within grace window same fingerprint → succeeds; same token within grace window different fingerprint → family revoked, 401; reuse after grace window → family revoked, 401; asserts exactly correct DB row counts |
| `RateLimitIT` | 5 failed logins → 6th → 429 with Retry-After; after TTL → attempt succeeds (correct credentials); Redis stopped mid-test → 503 fail-closed |
| `FlywayMigrationIT` | V1+V2 apply from empty DB; Flyway log shows `Successfully validated 2 migrations`; Hibernate `ddl-auto: validate` passes |
| `SecurityConfigIT` | Public endpoints accessible with no token; protected endpoint without token → 401; protected endpoint with valid JWT → proceeds; ADMIN endpoint with USER role → 403; CORS preflight from allowed origin → 200 with correct headers; CORS request from disallowed origin → no CORS headers |
| `PendingDeletionGateIT` | PENDING_DELETION user → `/applications` → 403 `account-pending-deletion`; same user → `/account` → 200; `/auth/refresh` → allowed |
| `PasswordResetRevokesSessionsIT` | Login → get refresh token; password reset; attempt refresh with old token → 401 (all families revoked) |
| `EmailVerificationIT` | Register → token in DB; verify with correct token → 200, status ACTIVE; verify again → 400 (consumed); verify with expired token → 400 |

### 13.3 Frontend Unit Tests (Karma/Jasmine)

| Target | Scenarios |
|---|---|
| `AuthStateService` | State transitions: setAuthenticated, clearSession, isAuthenticated$ derived signal |
| `AuthService` | login() → correct URL and payload; refresh() → withCredentials: true, X-Requested-With header; logout() → clears state |
| `authInterceptor` | No token → no Authorization header; token present → header attached; 401 → triggers refresh; 401 on /auth/refresh URL → does NOT loop; concurrent 401s → refresh called exactly once (spy count asserted); refresh success → original request retried; refresh failure → logout triggered |
| `authGuard` | Unauthenticated → UrlTree to /auth/login; authenticated → true |
| `guestGuard` | Authenticated → UrlTree to /applications; unauthenticated → true |
| `adminGuard` | USER role → false; ADMIN role → true |
| `verifiedGuard` | UNVERIFIED → false; ACTIVE → true |
| `LoginComponent` | Form validation fires; submits correct payload; shows server 401 error; loading state during request; no loading state after |
| `RegisterComponent` | Password complexity validator; server 409 → displays "email already registered" |
| `VerifyComponent` | Token in URL → calls verify; 200 → success message; 400 → error message |
| `PasswordResetRequestComponent` | Always shows "if account exists..." regardless of response |
| `PasswordResetComponent` | Token from URL; password mismatch validator; 400 invalid-token → specific error |

### 13.4 E2E (Cypress — `testing-strategy.md §5`)

1. Register → see "check your email" screen. Query token from Testcontainers DB directly.
2. Visit verify URL with token → `{ "status": "ACTIVE" }` → redirected to login.
3. Login → arrive at `/applications` (empty state, not blank).
4. Simulate expired token → concurrent API calls → assert exactly **one** `/auth/refresh` network call (not two). Both API calls succeed.
5. Logout → redirect to `/auth/login`.
6. Login with wrong password → `401 invalid-credentials` message shown; no stack trace visible in UI.
7. 5 failed logins → 429 → UI shows Retry-After message.
8. `cy.checkA11y()` on all five auth screens.
9. Password reset flow: request → check email (stubbed) → reset form → new password accepted → old refresh token rejected.

---

## 14. Implementation Order

Phases are sequenced to prevent rework. Each phase is independently verifiable.

### Phase A — Repository Prerequisites (block on Decisions 2.2 and 2.3)

| Step | Action | Verifiable by |
|---|---|---|
| A1 | Resolve Decision 2.2 (Redis client) | User decision |
| A2 | Resolve Decision 2.3 (async vs. sync email) | User decision |
| A3 | Update `pom.xml`: add `spring-security-oauth2-jose`, add MapStruct + processor, replace Redis starter (if A1=Option A) | `./mvnw test-compile` exits 0 |

### Phase B — Database Foundation

| Step | Action | Verifiable by |
|---|---|---|
| B1 | Create `src/main/resources/db/migration/V1__create_auth_schema.sql` (users, refresh_tokens, auth_tokens) | `./mvnw verify` → Flyway log |
| B2 | Create `V2__create_audit_schema.sql` (audit_log) | `./mvnw verify` → Flyway `validated 2 migrations` |
| B3 | Verify `ddl-auto: validate` passes with new entities | IT: context loads without `SchemaManagementException` |

### Phase C — Backend Core (in dependency order, no controller yet)

| Step | Action |
|---|---|
| C1 | `JwtProperties.java`, `PasswordConfig.java`, `AsyncConfig.java` |
| C2 | `User.java`, `RefreshToken.java`, `AuthToken.java` entities |
| C3 | `UserRepository.java`, `RefreshTokenRepository.java`, `AuthTokenRepository.java` |
| C4 | `JwtService.java` — RS256 sign/verify |
| C5 | `RateLimitService.java` — Redis INCR+EXPIRE-NX, fail-closed |
| C6 | `EmailService.java` — async, Optional<JavaMailSender> |
| C7 | `AuthService.java` — full business logic |
| C8 | Unit tests for C4–C7 (Surefire) |

### Phase D — Security Configuration

| Step | Action | Verifiable by |
|---|---|---|
| D1 | `SecurityConfig.java` — `SecurityFilterChain` with all rules from §6.1 | `JobTrackerApplicationIT` still passes |
| D2 | `JwtAuthenticationFilter.java` | |
| D3 | `CorrelationIdFilter.java` | |
| D4 | `PendingDeletionCheckFilter.java` | |
| D5 | `ProblemDetailConfig.java` | Unit test: `ProblemDetailConfigTest` |
| D6 | `SafeLogRedactor.java` | |
| D7 | `SecurityConfigIT` | All security rules verified end-to-end |

### Phase E — Controller and Mappers

| Step | Action | Verifiable by |
|---|---|---|
| E1 | `UserMapper.java` (MapStruct) | Compiles |
| E2 | `AuthController.java` | |
| E3 | `AuthControllerIT` | All 7 endpoints return correct status/body |
| E4 | `RefreshTokenRotationIT` | Grace window and reuse detection both branches tested |
| E5 | `RateLimitIT` | Fail-closed confirmed |
| E6 | `EmailVerificationIT`, `PasswordResetRevokesSessionsIT` | |

### Phase F — Frontend

| Step | Action |
|---|---|
| F1 | Update `app.config.ts`: add `provideHttpClient`, interceptors |
| F2 | `auth-state.service.ts`, `auth.service.ts` |
| F3 | All three interceptors |
| F4 | All four guards |
| F5 | Update `app.routes.ts` |
| F6 | All five auth feature components |
| F7 | All frontend unit tests (Karma) |
| F8 | `APP_INITIALIZER` session-restore logic |

### Phase G — Integration, E2E, and Final Verification

| Step | Action | Verifiable by |
|---|---|---|
| G1 | Cypress E2E: register → verify → login → refresh → logout | 9 scenarios in §13.4 |
| G2 | `./mvnw verify` — all Failsafe ITs green | EXIT 0 |
| G3 | `npm run test -- --watch=false` — all Karma tests green | EXIT 0 |
| G4 | `./mvnw checkstyle:check` | No PII-accessor-in-log violations |
| G5 | Update `application-local.yaml.example` with JWT key and CORS placeholders | |
| G6 | Update `implementation_history.md` with new verified file states | |

---

## 15. Risks and Security Edge Cases

| Risk | Severity | Mitigation |
|---|---|---|
| Redis down → fail-closed blocks all logins | HIGH (availability) | Spec-mandated tradeoff. Runbook: check `GET /actuator/health` → Redis indicator → operator restores Redis. |
| `.block()` timeout hangs Tomcat thread (Option B only) | MEDIUM | Bounded timeout `.block(Duration.ofSeconds(2))` on every call site. |
| JWT private key leaks through exception log | HIGH | `SafeLogRedactor` strips PEM headers. Never log `JwtProperties` toString(). MapStruct-generated DTOs must not be logged. |
| Concurrent refresh: second call sees token in grace window, issues second new token | HIGH | `SELECT FOR UPDATE` in step 2 of refresh flow serializes concurrent calls. The second call blocks, then sees `superseded_at IS NOT NULL` and takes the grace-window path (no new rotation). |
| Replay of an expired refresh token | HIGH | `expires_at < now()` check before any rotation. |
| Password-reset email enumeration | MEDIUM | Always `202`. Async email ensures consistent response time regardless of user lookup result. |
| Registration `409 email-exists` enumerates accounts | LOW-MEDIUM | Spec defines this response. Rate-limiting registration is a recommended addition not currently in spec. |
| CSRF on cookie-using endpoints | MEDIUM | SameSite=Strict + X-Requested-With header. CORS allow-list + preflight blocks cross-site attempts. |
| Token fixation after password reset | MEDIUM | All refresh token families for the user are revoked on password reset completion. |
| Email verification token brute-force | LOW | 32-byte token = 256-bit entropy. Computationally infeasible. Consider per-IP rate limiting on `GET /auth/verify` as a defensive addition. |
| Timezone assumption in token expiry | MEDIUM | All Java code uses `Instant.now()` or `ZonedDateTime.now(ZoneOffset.UTC)`. Hard Operating Rule 7 explicitly enforced. |
| pom.xml change breaks Maven resolution | MEDIUM | Any pom.xml change must be followed immediately by `./mvnw test-compile` to confirm resolution through `.mvn/settings.xml` / `.mvn/maven.config`. |
| MapStruct processor + Lombok conflict (if Lombok ever added) | LOW | No Lombok in project currently. If added later, MapStruct processor must appear after Lombok in `annotationProcessorPaths`. |

---

## 16. Acceptance Criteria

### Backend

- [ ] `./mvnw test-compile` passes after all pom.xml changes.
- [ ] `./mvnw verify` passes: `FlywayMigrationIT`, `AuthControllerIT`, `RefreshTokenRotationIT`, `RateLimitIT`, `SecurityConfigIT`, `PendingDeletionGateIT`, `PasswordResetRevokesSessionsIT`, `EmailVerificationIT`, `JobTrackerApplicationIT` all PASSED.
- [ ] Surefire unit tests pass with ≥90% line coverage on all `auth/service/**` classes (JaCoCo).
- [ ] `./mvnw checkstyle:check` passes — no PII accessor in log statements.
- [ ] No stack trace in any error response, any profile.
- [ ] All 7 auth endpoints return exact status codes, error codes, and shapes from §9.
- [ ] Refresh reuse >10s after supersession → family revoked, 401.
- [ ] Refresh reuse ≤10s after supersession, matching fingerprint → succeeds (grace window).
- [ ] Redis unavailable during login or password-reset-request → 503 (not 200, not 500).
- [ ] `application.yaml` contains no `spring.mail.host`.
- [ ] App fails to start without `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` in production profile.
- [ ] Password reset revokes all existing refresh token families for the user.
- [ ] Email verification token cannot be consumed twice.

### Frontend

- [ ] `npm run test -- --watch=false` passes — all Karma unit tests green.
- [ ] Access token stored in memory only (BehaviorSubject). No `localStorage` / `sessionStorage` writes.
- [ ] Page refresh → session silently restored via `/auth/refresh` if cookie valid.
- [ ] Concurrent 401s → exactly one `/auth/refresh` HTTP call (asserted by spy count in test and Cypress).
- [ ] Expired/invalid refresh → redirect to `/auth/login?returnUrl=<original path>`.
- [ ] All five auth forms: loading, error, and success states. No blank screens.
- [ ] `cy.checkA11y()` passes on all five auth screens.
- [ ] Built JS bundle contains no `*_KEY` or `*_SECRET` patterns (CI grep per `deployment.md §3`).

---

## 17. Open Decisions Required Before Execution

| # | Decision | Default if not answered | Impact |
|---|---|---|---|
| D2.2 | Replace reactive Redis starter with blocking? (Option A recommended) | Cannot execute Phase A | `pom.xml` change scope |
| D2.3 | Async email (recommended) vs. synchronous? | Cannot execute Phase C6 | `AsyncConfig.java` required or not |
| D6.4 | Refresh token lifetime: 7 days (recommended)? | Can proceed with 7 days as default | Cookie Max-Age value |
| D5.3 | Email-verify token TTL: 24h? Password-reset token TTL: 1h? | Can proceed with recommended values | `auth_tokens.expires_at` logic |

Items D6.4 and D5.3 can be decided during implementation without blocking Phase A. Items D2.2 and D2.3 must be resolved before any code is written.
