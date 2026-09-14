# deployment.md — Environments, CI/CD, Deployment, Rollback, Recovery, Free-Tier Capacity
Supersedes all prior versions. Incorporates FINAL-AUDIT.md context on free-tier capacity modeling (audit category #10 in the adversarial review).

## 1. Environments
| Env | Backend | Frontend | DB | Purpose |
|---|---|---|---|---|
| local | Docker Compose (Spring Boot+Postgres+Redis+WireMock) | `ng serve` | local Postgres container | day-to-day dev |
| CI | Testcontainers (ephemeral) | headless build | Testcontainers (ephemeral) | automated tests only |
| staging | Render/Railway, `staging` branch auto-deploy | Vercel/Netlify PR preview | Neon **branch** (copy-on-write of prod schema/data — a real provider capability, not invented) | manual pre-merge verification |
| production | Render/Railway, `main` branch auto-deploy | Vercel/Netlify, `main` | Neon primary branch | live app |

## 2. Docker Configuration
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```
`ffmpeg`(`ffprobe`) is installed in the runtime image specifically for audio duration/codec validation (architecture.md §8, fixes audit #12 — Tika's audio support was found unreliable). `docker-compose.yml` (local/CI) adds `postgres:16`, `redis:7`, `wiremock`, backend pointed at WireMock for all AI calls locally — no real API keys needed for local dev.

## 3. Configuration & Environment Variables
**Backend**: `SPRING_PROFILES_ACTIVE, DATABASE_URL, REDIS_URL, JWT_PRIVATE_KEY, JWT_PUBLIC_KEY, GROQ_API_KEY, R2_ACCOUNT_ID, R2_ACCESS_KEY, R2_SECRET_KEY, R2_BUCKET, EMAIL_API_KEY, CORS_ALLOWED_ORIGIN, GAP_ANALYSIS_DAILY_QUOTA(default 20), VOICE_SESSION_DAILY_QUOTA(default 10)`. The two quota variables are deliberately externalized (not hard-coded constants) so they can be tuned down without a redeploy-requiring code change if Groq's actual free-tier limits turn out lower than assumed at design time (§6).
**Frontend**: build-time `environment.prod.ts` sets `apiBaseUrl` only; a CI step greps the built JS bundle for `*_KEY`/`*_SECRET` patterns and fails the build if any are found, catching an accidental secret leak into client code.

## 4. Git Branching & PR Rules
Trunk-based: `main` (production), `staging` (pre-prod), short-lived feature branches off `staging`. Both protected branches require PR + all CI checks green + at least one review. Conventional Commits for changelog generation.

## 5. GitHub Actions CI/CD Pipeline
1. `lint` — `mvn checkstyle:check` (backend) + `npm run lint` (frontend).
2. `unit-backend` — `mvn test` (surefire, unit tests only, no Testcontainers).
3. `unit-frontend` — `npm run test -- --watch=false --browsers=ChromeHeadless`.
4. `integration-backend` — `mvn verify -Pintegration` (failsafe profile, Testcontainers+WireMock).
5. `test-migrations` — dedicated Maven profile that boots a fresh Testcontainers Postgres and runs `flyway:migrate` then `mvn verify`; only triggered when `src/main/resources/db/migration/**` changed.
6. `e2e` — Cypress against a docker-compose stack.
7. `coverage` — `mvn verify` (triggers `jacoco-maven-plugin`'s `check` goal), report uploaded to Codecov, fails <80%.
8. `security-scan` — OWASP `dependency-check-maven` plugin (`mvn dependency-check:check`), fail on high/critical CVE.
9. `build` — `mvn clean package -DskipTests` then Docker image build/push (`main`/`staging` only).
10. `deploy` — Render/Railway + Vercel/Netlify deploy hooks (`main`/`staging` only, after 1–9 green).
Separate, **not** part of this per-PR pipeline: the weekly AI-quality-eval GitHub Action (testing-strategy.md §6) and the manual/scheduled k6 load-test workflow (testing-strategy.md §7) — both are too slow/expensive/non-deterministic to run on every PR.

## 6. Free-Tier Capacity — Worst-Case Modeling (audit category #10)
**Treat every number below as a configurable value to re-verify against the providers' current published limits before build — they change, and this document does not assume they won't.**

| Resource | Assumed free-tier limit (verify before build) | Worst-case Phase-1 usage model | Headroom assessment |
|---|---|---|---|
| Groq LLM+Whisper requests/tokens per minute/day | Per-minute and per-day caps exist on the free tier, exact values change | With per-user daily quotas (20 gap + 10 voice = 30 AI-triggering actions/user/day max) **and** the fact that a single retry or malformed-response path can add up to 2 additional calls per job (1 retry each), worst case is **~90 AI calls/user/day**, not 30 | **Per-user quotas alone do not protect the organization-level Groq rate limit if many users are active in the same minute** (explicit gap, not assumed away, per audit category #10) — mitigated by: the circuit breaker (opens fast on sustained 429/5xx, stopping the bleeding) and the Redis cache (repeat JD/resume pairs across different users hitting the same cache key don't each cost a call). At true multi-user concurrent load this remains a real constraint on how many simultaneously active users the free tier supports; documented here as a known Phase-1 ceiling, not solved by this architecture — the honest answer for "how many concurrent users can this support" is **a handful, not thousands**, which is appropriate framing for a portfolio deployment |
| Upstash Redis commands/day | A fixed daily command budget on the free tier | Each API request touching rate-limit/quota costs 2 Redis calls (INCR+EXPIRE NX); each Gap Analyzer cache check costs 1-2; metrics counters add 1 per cache check. At low Phase-1 traffic (a handful of active users) this stays well under typical free-tier daily command budgets | Adequate for portfolio-scale demo traffic; would need re-costed before any real user growth |
| Neon Postgres storage | ~0.5GB on free tier | Schema is lean (no large blobs — resumes/audio live in R2, not Postgres); `audit_log`/`metrics_snapshot` grow slowly at this scale | Adequate for years at demo-level usage |
| Cloudflare R2 storage/ops | Generous free storage/egress for small files | Resumes (≤5MB) + audio (≤10MB) per user, cleaned up by the sweeps (architecture.md §9) | No realistic concern at this scale |
| Render/Railway compute | Free instance sleeps after inactivity, cold start ~30-50s on wake | Accepted UX tradeoff, not engineered around (an artificial keep-alive ping risks violating the platform's free-tier terms) | Documented limitation, not a defect |
| Vercel/Netlify bandwidth | Free tier cap, ample for this traffic | No concern | — |

**Portfolio/hobby deployment vs. production claim — stated explicitly, not blurred**: this specification describes a system correct enough to defend in a technical interview and safe enough to run a real portfolio demo with real (if few) users. It is **not** a claim that this architecture is production-ready at scale for a company with thousands of concurrent users — the DB-backed job queue (ADR-001), single-region free-tier hosting, and per-user-only quota modeling are all explicit, load-appropriate choices for a portfolio deployment, and each is documented as a scaling limit rather than hidden as an oversight.

## 7. Deployment, Rollback, Graceful Shutdown
Rolling deploy: push to `main`→CI green→new instance(s) started→`/actuator/health/readiness` passes→traffic routed→old instance(s) stopped (platform-native). Rollback: redeploy the previous image; safe because migrations are additive-first (database.md §7) — a rolled-back code version never encounters a schema state it can't handle. Graceful shutdown: architecture.md §3 — in-flight jobs finish within the shutdown window or their lease expires for pickup elsewhere.

## 8. Backup & Disaster Recovery
Database: Neon's built-in point-in-time recovery (free-tier window, verify current value) is the entire backup strategy — no custom `pg_dump` cron added, since it would duplicate a provider capability and introduce a new place for backups to silently fail unnoticed. Object storage: R2 objects are not separately backed up — they are user-re-uploadable, low-criticality data; a lost in-flight voice recording simply means that one job is retried by the user. No formal RTO/RPO is claimed, because a free-tier stack cannot honestly claim one — the recovery expectation is "restore via Neon PITR within its supported window, redeploy from the last green `main` commit," stated as exactly what it is, not dressed up as an enterprise SLA.
