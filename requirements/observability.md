# observability.md — Logging, Metrics, Health, Dashboards, Alerts
Supersedes all prior versions. Incorporates FINAL-AUDIT.md items #7, #8, #13.

## 1. Structured Logging
JSON format (Logback JSON encoder), fields on every line: `timestamp, level, correlationId, userId(UUID|null), path, method, message, exception`. No PII in any field (security.md §6, two-layer redaction). Correlation ID generated at request entry, propagated via MDC, copied onto the `async_jobs` row at creation and re-inserted into MDC when a worker claims the job — a job's logs trace back to the request that created it even across threads/instances.
Levels: `ERROR` (unhandled exceptions, exhausted-retry AI failures, permanently FAILED jobs), `WARN` (retry attempts, rate-limit/quota hits, cache miss during a Redis outage, circuit-breaker state transitions), `INFO` (auth events, job state transitions), `DEBUG` (dev only).
Sink: stdout (Render/Railway log viewer, sufficient at Phase-1 volume); optional forward to Better Stack (Logtail) free tier for full-text search/longer retention.

## 2. Health, Liveness, Readiness
`GET /actuator/health` (custom indicators: Postgres `SELECT 1`, Redis `PING`, 2s timeout each). `/actuator/health/liveness` (process-alive only — a transient DB blip shouldn't cause the platform to kill a healthy instance). `/actuator/health/readiness` (includes dependency checks — gates traffic routing on deploy). `/actuator/info` (build version, git commit).

## 3. Metrics — Exact Instrumentation (every metric has a real source; none reference an undefined table/counter)

| Metric | Formula | Instrumentation point | Storage | Period | Dimensions | Exposed via | Target |
|---|---|---|---|---|---|---|---|
| `http.request.duration` (p95) | 95th percentile controller duration | Micrometer `Timer`, Spring Boot Actuator auto-instrumentation | Grafana Cloud (Prometheus remote-write) | rolling 15min | `uri`,`status` | Grafana | <500ms non-AI |
| `http.request.errors` | count(status≥500)/count(all) | same timer, tagged by status | Grafana Cloud | rolling 15min | `uri` | Grafana | <1% |
| `ai.dependency.latency` (fixed per audit #13 — previously not separated from overall API latency) | Micrometer `Timer` wrapped **specifically** around the Groq/Whisper HTTP client call, not the controller method | Custom `@Timed`/manual `Timer.Sample` around the client bean | Grafana Cloud | rolling 15min | `provider`(groq/whisper),`outcome` | Grafana | informational |
| `async_job.queue_wait_seconds` (fixed per audit #7 — previously conflated with processing time) | `avg(processing_started_at - created_at)` for jobs claimed in the period | SQL against `async_jobs` | `metrics_snapshot` | hourly | `jobType` | `/admin/stats` | <5s |
| `async_job.processing_seconds` (fixed per audit #7) | `avg(completed_at - processing_started_at)` for `status='DONE'` | same query pass | `metrics_snapshot` | hourly | `jobType` | `/admin/stats` | <30s gap, <60s voice |
| `async_job.success_rate` | `count(status='DONE')/count(status IN ('DONE','FAILED'))` | same query pass | `metrics_snapshot` | hourly | `jobType` | `/admin/stats` | >95% |
| `async_job.queue_depth` (new, audit #13) | `count(status='QUEUED')` at snapshot time | SQL against `async_jobs` | `metrics_snapshot` | hourly (point-in-time) | `jobType` | `/admin/stats` | informational, alert if sustained >20 |
| `async_job.oldest_queued_age_seconds` (new, audit #13) | `now() - min(created_at) WHERE status='QUEUED'` | same query pass | `metrics_snapshot` | hourly | `jobType` | `/admin/stats` | alert if >300s |
| `async_job.avg_attempt_count` (new, audit #13 — retry pressure) | `avg(attempt_count)` for jobs completed (`DONE` or `FAILED`) in the period | same query pass | `metrics_snapshot` | hourly | `jobType` | `/admin/stats` | informational, alert if >1.5 (suggests a flaky dependency) |
| `ai.provider_failure_rate` (new, audit #13) | count of Resilience4j circuit-breaker `CLOSED→OPEN` transitions in the period | Resilience4j event listener, logged `WARN` and counted via a Micrometer counter | Grafana Cloud + `metrics_snapshot` (hourly copy for `/admin/stats`) | hourly | `provider` | Grafana + `/admin/stats` | alert if >0 twice in 1h |
| `cache.hit_rate` (Gap Analyzer) — **race fixed, audit #8** | `hits/(hits+misses)` for the last fully-elapsed hour bucket | Redis `INCR metrics:cache:gap:hit:{yyyyMMddHH}` / `:miss:{yyyyMMddHH}`, each key TTL 2h, **read-only** (never reset) by the aggregation job | `metrics_snapshot` | hourly | none | `/admin/stats` | >20% |
| `db.pool.active`/`db.pool.pending` (new, audit #13) | HikariCP's own Micrometer metrics, exported automatically once Micrometer+HikariCP are both on the classpath — no custom code | Actuator/Micrometer | Grafana Cloud | real-time | `pool` | Grafana | alert if `pending`>0 sustained |
| `active_users_24h` | `count(distinct userId)` from `audit_log`, `created_at>now()-24h` | SQL against `audit_log` | `metrics_snapshot` | daily (computed hourly, rolling) | none | `/admin/stats` | informational |
| `test_coverage` | JaCoCo line coverage % | `jacoco-maven-plugin`, bound to the `verify` phase | CI artifact + Codecov | per commit | none | README badge | ≥80% backend |

## 4. Metrics Aggregation Job — Race-Free Design (audit #7, #8)
A single `@Scheduled(cron="0 0 * * * *")` hourly job, singleton-guarded via Postgres advisory lock (architecture.md §5, lock key `metrics_aggregation`):
1. Runs the `async_jobs` queries (queue wait, processing time, success rate, queue depth, oldest-age, avg attempts) for the hour just completed.
2. Reads (never resets) the Redis cache-hit/miss buckets for that same hour — the bucket key itself encodes the hour, so there is nothing to reset; a bucket simply stops being written to once the hour rolls over and expires on its own 2-hour TTL. This is the fix for audit #8: the old design's `GET`-then-`SET 0` pattern had a window where a concurrent `INCR` could be lost; bucketing by hour removes the read-modify-write step entirely.
3. Reads the Resilience4j circuit-breaker transition counter.
4. Runs the `audit_log` distinct-user query.
5. Writes one `metrics_snapshot` row per metric with `period_start`/`period_end` set to the completed hour.
`/admin/stats` (api-spec.md §8) always reads only the latest row per `metric_name` — never computes anything live.

## 5. Dashboards & Alerts
- **Grafana Cloud (free tier)**: `http.request.duration`, `http.request.errors`, `ai.dependency.latency`, `db.pool.active`/`pending`, `ai.provider_failure_rate` — the metrics that benefit from real-time/time-series visualization, sourced via Micrometer/Prometheus remote-write.
- **`/admin/stats`**: business/queue-health metrics computed hourly from small aggregate queries — kept separate from the Grafana pipeline deliberately, since paying for a second time-series ingestion path for hourly-cadence business metrics would be unjustified complexity (rule against architecture astronautics).
- **UptimeRobot (free tier)**: 5-minute `/actuator/health` check, public status page, email alert on down — the sole paging mechanism, proportionate to a single-maintainer project.
- **Alert conditions, concretely**: `http.request.errors`>5% for 5min; `async_job.oldest_queued_age_seconds`>300; `ai.provider_failure_rate`>0 twice within 1h; `db.pool.pending`>0 sustained — all check-able manually against Grafana/`/admin/stats` at this scale; no auto-paging integration beyond UptimeRobot's email, which is the proportionate choice for a solo-maintained system, not a gap.

## 6. Operational Debugging Runbook
1. UptimeRobot alert/status page → is the API reachable at all.
2. `/actuator/health` → which dependency (DB/Redis) is failing, if either.
3. Render/Railway logs (or Better Stack), filter by `correlationId` or `level:ERROR`.
4. Stuck job suspected: query `async_jobs WHERE status='PROCESSING' AND locked_until < now()` — should be empty outside the 60s sweep window; persistently non-empty means the sweep itself is failing (check its own `INFO`/`ERROR` logs).
5. AI-specific failure: `async_jobs.last_error` on the failed row; circuit-breaker transitions logged `WARN`; `ai.provider_failure_rate` in Grafana confirms whether it's an isolated failure or a sustained provider outage.
6. Queue backlog suspected: `async_job.queue_depth`/`oldest_queued_age_seconds` in `/admin/stats` — a sustained high value with a healthy `ai.provider_failure_rate` points to a worker-loop problem (check whether the polling `@Scheduled` method is throwing and being silently caught somewhere it shouldn't be — architecture.md §3's per-job try/catch is scoped to the finalize step specifically to avoid this).
