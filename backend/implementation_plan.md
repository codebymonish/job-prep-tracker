# Job Prep Tracker — Backend Configuration Foundation
## FINAL Implementation Plan

> **Evidence labelling used throughout this document:**
> - **[ACTUALLY VERIFIED]** — Command was executed on the developer's machine and output was observed
> - **[REQUIRES VERIFICATION]** — Item is unresolved or depends on external information not yet confirmed
> - All other statements are engineering conclusions drawn directly from verified evidence

---

## A. Verified Repository State

### Current state of `.mvn/` directory — [ACTUALLY VERIFIED]

```
Test-Path C:\dev\job-prep-tracker\backend\.mvn\maven.config
→ False
```

```
Directory listing of C:\dev\job-prep-tracker\backend\.mvn:
  settings.xml   (535 bytes)
  wrapper/       (subdirectory)
```

**`.mvn/maven.config` does NOT exist.** This is the actual current repository state at the time of this plan.

> **Correction of prior plan claim:** An earlier version of this plan stated that `.mvn/maven.config` had been "live-tested". This was imprecise. The file was created temporarily for syntax verification, the test was run, and the file was then deleted. The test (`.\mvnw test-compile`) was executed while the file existed. After verification, the file was explicitly deleted and confirmed absent (`Test-Path → False`). The current repository does not contain `.mvn/maven.config`. The plan records it as a file to be created during implementation.

### Current `.\mvnw test-compile` output — [ACTUALLY VERIFIED]

```
.\mvnw test-compile → EXIT CODE 1
[FATAL] Non-resolvable parent POM for com.jobtracker:job-tracker:0.0.1-SNAPSHOT
        Could not transfer artifact
        org.springframework.boot:spring-boot-starter-parent:pom:4.1.1
        from/to com.trinity (http://dockerhubdev.trinitymobility.com:8081/...):
        No such host is known (dockerhubdev.trinitymobility.com)
```

Maven contacts the company Nexus mirror and fails. This is **Maven POM resolution**, not compilation. The build does not reach the compile phase.

### Confirmed files

| File | Confirmed by |
|---|---|
| `pom.xml` | File inspection |
| `src/main/java/com/jobtracker/JobTrackerApplication.java` | File inspection |
| `src/main/resources/application.yaml` — 4 lines only | File inspection |
| `src/main/resources/db/migration/` — directory, empty | File inspection |
| `src/test/java/com/jobtracker/JobTrackerApplicationTests.java` | File inspection |
| `src/test/java/com/jobtracker/TestcontainersConfiguration.java` | File inspection |
| `src/test/java/com/jobtracker/TestJobTrackerApplication.java` | File inspection |
| `.mvn/settings.xml` — Maven Central mirror | File inspection |

### Confirmed absent

| File | Confirmed by |
|---|---|
| `.mvn/maven.config` | `Test-Path → False` [ACTUALLY VERIFIED] |
| `src/test/resources/` | Build log: "skip non existing resourceDirectory" [ACTUALLY VERIFIED] |
| `src/main/resources/application-local.yaml` | File inspection |
| `src/main/resources/application-local.yaml.example` | File inspection |
| `docs/` | File inspection — no specification documents in repository |

---

## B. Confirmed Root Causes

Two independent blockers exist. They must be resolved in order.

### Blocker 1 — Maven mirror conflict [ACTUALLY VERIFIED]

**What fails:** `.\mvnw test-compile` → EXIT CODE 1

**Stage that fails:** Maven POM resolution. The build does not reach dependency resolution, compilation, or test compilation.

**Cause:** `~/.m2/settings.xml` (user-level, not this project's file) contains:
```xml
<mirror>
  <id>com.trinity</id>
  <mirrorOf>*</mirrorOf>
  <url>http://dockerhubdev.trinitymobility.com:8081/repository/maven-public</url>
</mirror>
```
`mirrorOf: *` intercepts all repository requests. The host is unreachable from the developer’s environment. Maven does not automatically use `.mvn/settings.xml` — that file is not selected merely because it exists in the project. The `-s` option explicitly selects an alternate settings file for that invocation. Without `-s`, Maven uses only the global and user settings, which include the company mirror.

**Fix:** Create `.mvn/maven.config` so that Maven automatically uses the project-level settings file for all invocations.

**Evidence the fix works:** [ACTUALLY VERIFIED]
```
# File .mvn/maven.config created with two-line content (see Section D)
.\mvnw test-compile → EXIT CODE 0, BUILD SUCCESS (3.58s)
No contact with dockerhubdev.trinitymobility.com
```

**What this verifies:** Maven POM resolution succeeds. Main source compilation succeeds. Test source compilation succeeds (including `TestcontainersConfiguration.java` with its Testcontainers imports). `src/test/resources/` is noted as non-existent, which is expected.

**What this does NOT verify:** dependency resolution from scratch (artifacts were already in local cache), Spring context startup, test execution, Testcontainers runtime startup.

### Blocker 2 — Spring ApplicationContext load failure [ACTUALLY VERIFIED]

**What fails:** `.\mvnw -s .\.mvn\settings.xml test` → EXIT CODE 1

**Stage that fails:** Spring ApplicationContext startup during Surefire test execution.

**Error:**
```
Tests run: 1, Failures: 0, Errors: 1
JobTrackerApplicationTests.contextLoads — IllegalState Failed to load ApplicationContext
```

**Cause:** `JobTrackerApplicationTests` has `@SpringBootTest`, which triggers a full Spring context load. The context requires a DataSource. No `spring.datasource.url` is configured. No embedded database is on the classpath. `@Import(TestcontainersConfiguration.class)` is commented out, so no container DataSource is provided.

**Fix:** Move the full-context test to `JobTrackerApplicationIT.java` (Failsafe, requires Docker). Delete `JobTrackerApplicationTests.java`. `mvn test` then runs zero Surefire tests → BUILD SUCCESS.

---

## C. Dependency Decisions

**No dependency in `pom.xml` is changed.** All decisions are grounded in verified evidence.

### Focused Spring Boot test starters

Eight starters (`spring-boot-starter-actuator-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-data-redis-reactive-test`, `spring-boot-starter-flyway-test`, `spring-boot-starter-mail-test`, `spring-boot-starter-security-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-webmvc-test`) are present without explicit versions — managed by the Spring Boot 4.1.1 BOM.

**Evidence:** After fixing the Maven mirror (Blocker 1), `test-compile` succeeds. This means all dependencies resolved and all test classes that use types from these starters compiled without error. Dependency resolution and compilation are confirmed successful.

**Decision: unchanged.**

### `org.testcontainers:testcontainers-postgresql`

`TestcontainersConfiguration.java` imports `org.testcontainers.postgresql.PostgreSQLContainer` (Testcontainers 2.x package path). This class **compiled successfully** in the verified `test-compile` run.

**Evidence:** [ACTUALLY VERIFIED] `.\mvnw test-compile` with `maven.config` active → BUILD SUCCESS. Test class with `PostgreSQLContainer` import compiled without error.

**This confirms:** compilation succeeds. It does NOT confirm container startup (requires Docker).

**Decision: unchanged.**

### `springdoc-openapi-starter-webmvc-ui:3.1.0`

**Evidence from `mvn test` auto-configuration report:** [ACTUALLY VERIFIED]
```
Unconditional classes:
  org.springdoc.core.configuration.SpringDocWebServerConfiguration.EmbeddedWebServerConfiguration
  org.springdoc.core.configuration.SpringDocWebServerConfiguration.DeployedWebServerConfiguration
```
Springdoc loads unconditionally in the Spring Boot 4.1.1 context. No incompatibility observed.

**Decision: unchanged.** Do not change without an actual compile or runtime failure as evidence.

### All other dependencies

| Dependency | Decision |
|---|---|
| `spring-boot-testcontainers` | Unchanged |
| `testcontainers-junit-jupiter` | Unchanged |
| `spring-ai-spring-boot-testcontainers` | Unchanged |
| `org.postgresql:postgresql` (runtime) | Unchanged |
| All production starters | Unchanged |

---

## D. Maven Configuration Strategy

### Problem

The developer's normal Maven user settings are located at `~/.m2/settings.xml` and contain the company mirror. The project also contains `.mvn/settings.xml`, but Maven does not automatically select it — Maven does not use a project-level `settings.xml` merely because it exists. The `-s` option explicitly selects an alternate settings file. `.mvn/maven.config` supplies `-s` and `.mvn/settings.xml` automatically for every Maven invocation in this project.

### Mechanism: `.mvn/maven.config`

Maven reads `.mvn/maven.config` and prepends its contents as additional command-line arguments to every `mvn` / `mvnw` invocation for that project. This is the documented Maven project-configuration mechanism for per-project CLI defaults.

### Exact verified syntax — [ACTUALLY VERIFIED]

**File content tested and confirmed working:**

```
LINE[-s]
LINE[.mvn/settings.xml]
```

(shown with `Get-Content | ForEach-Object { "LINE[$_]" }` to expose exact line structure)

That is, the file contains exactly two lines:
- Line 1: `-s`
- Line 2: `.mvn/settings.xml`

**Why two lines:** Maven `maven.config` treats each line as a single token. Placing `-s .mvn/settings.xml` on one line passes it as one token (the entire string becomes the argument to `-s`, resulting in a space-prefixed path error). Each argument — flag and value — must occupy its own line.

**Confirmed error with single-line syntax (tested):**
```
.\mvnw test-compile → EXIT CODE 1
[ERROR] The specified user settings file does not exist:
        C:\dev\job-prep-tracker\backend\ .mvn\settings.xml
```
The leading space before `.mvn` confirms the single-line form was parsed as one token.

**Confirmed success with two-line syntax (tested):**
```
.\mvnw test-compile → EXIT CODE 0, BUILD SUCCESS (3.58s)
```

### Current state

`.mvn/maven.config` does **not** currently exist. It is created during implementation (Step 1). After creation, all `mvnw` commands automatically use `.mvn/settings.xml`.

---

## E. Application Configuration Architecture

### Design

| File | Location | Committed | Purpose |
|---|---|---|---|
| `application.yaml` | `src/main/resources/` | Yes | Structural defaults + required production env-var bindings |
| `application-local.yaml.example` | `src/main/resources/` | Yes | Template showing all local-dev overrides; no real values |
| `application-local.yaml` | `src/main/resources/` | No (gitignored) | Developer's personal copy with real local/cloud values |

### Why mail is absent from `application.yaml`

`MailSenderAutoConfiguration` is conditional: it only creates a `JavaMailSender` bean when `spring.mail.host` is present in the environment. If the property is absent, mail autoconfiguration is silently skipped — no error, no bean, no failed context.

Including `spring.mail.host: ${SPRING_MAIL_HOST}` in `application.yaml` would make `SPRING_MAIL_HOST` **mandatory** for every context startup, including tests, CI jobs, and environments where mail is not needed. This is incorrect. Mail is an optional integration.

Mail configuration belongs only in `application-local.yaml.example` (as documentation for developers who need it) and in the production environment's configuration mechanism where mail features are used.

### Why Redis IS in `application.yaml`

Redis is a required production dependency. `${SPRING_DATA_REDIS_URL}` in `application.yaml` ensures the application fails fast and explicitly in production if the variable is absent. This does not affect:
- **Surefire unit tests**: no Spring context is loaded → placeholder never evaluated
- **`@WebMvcTest` slices**: Redis autoconfiguration is excluded from the slice by Spring Boot design
- **`@DataJpaTest` slices**: Redis autoconfiguration is excluded from the slice by Spring Boot design
- **`JobTrackerApplicationIT`**: `@ServiceConnection` registers the Testcontainers Redis URL at highest priority in the Spring `Environment`; `${SPRING_DATA_REDIS_URL}` in `application.yaml` is never evaluated for that property during the integration test

### `application.yaml` — exact content to implement

```yaml
spring:
  application:
    name: job-tracker

  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 4
      minimum-idle: 1
      connection-timeout: 5000
      idle-timeout: 300000
      leak-detection-threshold: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true

  data:
    redis:
      url: ${SPRING_DATA_REDIS_URL}
```

**Hikari values are taken exactly from the established project specification.** Do not substitute.

### `application-local.yaml.example` — exact content to implement

```yaml
# ============================================================
# Local developer configuration template
# ============================================================
# 1. Copy this file to application-local.yaml (same directory)
# 2. Fill in real values
# 3. application-local.yaml is gitignored — never commit it
# 4. Activate: SPRING_PROFILES_ACTIVE=local
#              or -Dspring.profiles.active=local in IDE
# ============================================================

spring:
  datasource:
    url: jdbc:postgresql://<host>/<dbname>?sslmode=require
    username: <username>
    password: <password>

  data:
    redis:
      url: rediss://<host>:<port>

  # Mail is optional. Only include if mail features are needed locally.
  # mail:
  #   host: localhost
  #   port: 1025
  #   username: ""
  #   password: ""
  #   properties:
  #     mail:
  #       smtp:
  #         auth: false
  #         starttls:
  #           enable: false
```

### `.gitignore` addition

```
src/main/resources/application-local.yaml
```

### Flyway and `ddl-auto: validate` — explicit constraint

**Current state:**
- `db/migration/` is empty
- Zero JPA entities exist
- Flyway with zero migrations: succeeds (`Successfully validated 0 migrations`) — [REQUIRES VERIFICATION at Docker-capable stage]
- JPA `validate` with no entities: succeeds (nothing to validate)

**Enforced constraint going forward:**

> Every new JPA `@Entity` class MUST be introduced together with a Flyway migration script that creates the corresponding database table. They must be committed in the same change.

`ddl-auto: validate` does not create or modify schema. It compares the entity model against the existing schema. If a table expected by an entity does not exist (because no migration created it), the Spring context fails to start with `SchemaManagementException: Missing table [table_name]`. This failure cannot be bypassed without changing `ddl-auto`, which the project specification does not permit.

---

## F. Test Strategy

### Decision on `contextLoads()`

The current `JobTrackerApplicationTests.contextLoads()` with `@SpringBootTest` is a full Spring context integration test. It requires a DataSource (PostgreSQL), Redis, and — once entities exist — a valid schema. On the current machine it cannot pass. It is not a unit test.

**Decision: move to `JobTrackerApplicationIT.java` under Failsafe with `@Import(TestcontainersConfiguration.class)` uncommented.**

This is not converted to a reduced `@SpringBootTest` with autoconfiguration excluded. Such a test:
- Does not validate that the application actually starts
- Provides false confidence
- Has no business value for an empty codebase
- Creates maintenance burden when real infrastructure is added

**No fake unit smoke test is introduced.**

### Unit tests going forward

Future unit tests test actual business logic (services, validators, domain objects) using JUnit 5 and Mockito. No Spring context is required. Slice tests (`@WebMvcTest`, `@DataJpaTest`) are written against actual controllers and repositories when those exist.

### Surefire and Failsafe lifecycle

**`.\mvnw test`** — runs Surefire only:
- Maven lifecycle phases: `validate → compile → test-compile → test`
- Surefire default patterns: `**/*Tests.java`, `**/*Test.java`, `**/Test*.java`
- After deleting `JobTrackerApplicationTests.java`: zero files match
- Result: `Tests run: 0, BUILD SUCCESS`
- No Spring context loaded, no Docker, no credentials required

**`.\mvnw verify`** — runs Surefire then Failsafe:
- Maven lifecycle phases: all `test` phases + `integration-test → verify`
- Failsafe default patterns: `**/*IT.java`, `**/*ITCase.java`, `**/IT*.java`
- `JobTrackerApplicationIT.java` matches `**/*IT.java`
- Testcontainers starts PostgreSQL and Redis containers
- Docker is required
- Result when Docker is available: `Tests run: 0` (Surefire) + `JobTrackerApplicationIT PASSED` (Failsafe)

`JobTrackerApplicationIT.java` matches Failsafe patterns and does NOT match any Surefire pattern. It will never execute during `.\mvnw test`.

No additional flags (`-DskipTests=false`, `-DskipITs`, etc.) are used unless a specific use case requires them.

### `maven-failsafe-plugin`

Currently absent from `pom.xml`. Must be added to `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <!-- version managed by spring-boot-starter-parent -->
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

No `<includes>` override is needed — `**/*IT.java` is the Failsafe default.

---

## G. Testcontainers Strategy

### `TestJobTrackerApplication.java` — [ACTUALLY VERIFIED by inspection]

**Actual implementation:**
```java
package com.jobtracker;

import org.springframework.boot.SpringApplication;

public class TestJobTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.from(JobTrackerApplication::main)
            .with(TestcontainersConfiguration.class)
            .run(args);
    }

}
```

`SpringApplication.from(X::main).with(Y.class)` is the Spring Boot 3.x+ API for running the application locally with additional test configuration. This class:
- Lives in `src/test/java/` — correct; it uses test infrastructure and must not be in production sources
- References `JobTrackerApplication::main` — delegates to the real entry point
- Injects `TestcontainersConfiguration` — PostgreSQL and Redis containers start automatically
- Requires Docker to run — by design; this is a developer convenience for local runs when Docker is available

**Decision: unchanged.** The implementation is correct for its purpose.

### `TestcontainersConfiguration.java` — required changes

**Current issues confirmed by inspection:**
1. `postgres:latest` — non-reproducible image tag
2. `redis:latest` — non-reproducible image tag
3. `PostgreSQLContainer` without `<?>` — raw type; generates unchecked-cast warning at compile time

**Image version decision:**

> [REQUIRES VERIFICATION] The established project specification does not define Docker image versions. Before implementation, the developer must confirm:
> - The PostgreSQL major version of the production/cloud database (e.g., Neon PostgreSQL version)
> - The Redis major version of the production/cloud cache (e.g., Upstash Redis version)
> The image tags in `TestcontainersConfiguration.java` should match production.

**This plan does not invent versions.** The implementer must fill in the confirmed versions. The placeholders below use `<postgres-version>` and `<redis-version>`:

```java
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:<postgres-version>-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:<redis-version>-alpine"))
            .withExposedPorts(6379);
    }

}
```

If the developer cannot determine the production versions before implementation, they may use `postgres:17-alpine` and `redis:7-alpine` as reasonable current stable defaults — but this must be an **explicit decision**, not a silent assumption. These are not spec-defined values.

**What is not changed:**
- `@TestConfiguration(proxyBeanMethods = false)` — correct
- `@ServiceConnection` on Postgres — correct; provides DataSource connection properties
- `@ServiceConnection(name = "redis")` on Redis — correct; provides Redis connection
- Import `org.testcontainers.postgresql.PostgreSQLContainer` — confirmed compiling [ACTUALLY VERIFIED]; unchanged
- `.withExposedPorts(6379)` on Redis — correct

---

## H. Exact Files to Create / Modify / Delete

### Create (new files)

| File | Contents |
|---|---|
| `.mvn/maven.config` | Two lines: `-s` on line 1; `.mvn/settings.xml` on line 2 |
| `src/main/resources/application-local.yaml.example` | Template from Section E; intended to be committed |
| `src/test/java/com/jobtracker/JobTrackerApplicationIT.java` | Full-context integration test (see Section F) |

### Modify (existing files)

| File | What changes |
|---|---|
| `pom.xml` | Add `maven-failsafe-plugin` to `<build><plugins>` only |
| `src/main/resources/application.yaml` | Replace 4-line file with full configuration from Section E |
| `.gitignore` | Add `src/main/resources/application-local.yaml` |
| `src/test/java/com/jobtracker/TestcontainersConfiguration.java` | Replace unpinned image tags with confirmed versions; add `<?>` to `PostgreSQLContainer` |

### Delete

| File | Reason |
|---|---|
| `src/test/java/com/jobtracker/JobTrackerApplicationTests.java` | Replaced by `JobTrackerApplicationIT.java`; a `@SpringBootTest` test belongs in Failsafe, not Surefire |

### Do NOT change

| File | Reason |
|---|---|
| `pom.xml` — dependency section | All dependencies confirmed to resolve and compile; no changes |
| `src/main/java/com/jobtracker/JobTrackerApplication.java` | Correct as-is |
| `src/test/java/com/jobtracker/TestJobTrackerApplication.java` | Confirmed correct by inspection |
| `.mvn/settings.xml` | Maven Central mirror; correct |
| `mvnw`, `mvnw.cmd` | Maven wrapper; unchanged |

### `JobTrackerApplicationIT.java` — exact content

```java
package com.jobtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JobTrackerApplicationIT {

    @Test
    void contextLoads() {
    }

}
```

---

## I. Exact Implementation Order

Execute steps in order. Complete verification for each step before proceeding.

---

### Step 1 — Create `.mvn/maven.config`

**File:** `C:\dev\job-prep-tracker\backend\.mvn\maven.config`

**Exact contents (two lines, no trailing spaces):**
```
-s
.mvn/settings.xml
```

**Rationale:** Fixes the Maven mirror conflict (Blocker 1). Must be done first — every subsequent step depends on `mvnw` working.

**Verification:** [ACTUALLY VERIFIED — syntax confirmed before plan was written]
```
.\mvnw test-compile
```
Expected: EXIT CODE 0, BUILD SUCCESS. No contact with `dockerhubdev.trinitymobility.com`.

This verifies: successful POM/model resolution using the available local cache plus configured repositories, plus main and test compilation (including Testcontainers classes).

This does NOT verify: test execution, Spring context startup, Testcontainers container runtime.

---

### Step 2 — Add `maven-failsafe-plugin` to `pom.xml`

Add the plugin XML from Section F inside `<build><plugins>`. Do not touch any dependency.

**Verification:**
```
.\mvnw test-compile
```
Expected: still EXIT CODE 0. Plugin addition does not affect compilation.

---

### Step 3 — Replace `application.yaml`

Replace the full contents of `src/main/resources/application.yaml` with the YAML from Section E.

**Verification:** Visual inspection — confirm Hikari values, no `spring.mail.*`, Redis URL present, DataSource URL present.

No runnability change at this step — the file is not evaluated until a Spring context is loaded.

---

### Step 4 — Create `application-local.yaml.example`

Create `src/main/resources/application-local.yaml.example` with content from Section E.

**Verification:**
```
git status
```
Expected: `application-local.yaml.example` listed as an untracked file ready to be added.

---

### Step 5 — Update `.gitignore`

Add:
```
src/main/resources/application-local.yaml
```

**Verification:**
```
# Temporarily create the file:
echo test > src/main/resources/application-local.yaml
git status
```
Expected: `application-local.yaml` does NOT appear in untracked files. Delete the temporary file after confirming.

---

### Step 6 — Delete `JobTrackerApplicationTests.java` and create `JobTrackerApplicationIT.java`

Delete: `src/test/java/com/jobtracker/JobTrackerApplicationTests.java`

Create: `src/test/java/com/jobtracker/JobTrackerApplicationIT.java` with content from Section H.

**Verification — compilation:**
```
.\mvnw test-compile
```
Expected: EXIT CODE 0. `JobTrackerApplicationIT.java` compiles. `JobTrackerApplicationTests.java` no longer exists.

**Verification — unit test execution (primary goal of Blocker 2 fix):**
```
.\mvnw test
```
Expected: EXIT CODE 0, BUILD SUCCESS. Surefire output: `Tests run: 0, Failures: 0, Errors: 0, Skipped: 0`. No Spring ApplicationContext started. No Docker contacted. No environment variables required.

> **Note:** At the current foundation stage, zero Surefire tests is expected because no business-logic code exists yet. This is not a desired steady state. Every testable unit of business logic introduced going forward must be accompanied by meaningful unit tests.

---

### Step 7 — Update `TestcontainersConfiguration.java`

Before modifying `TestcontainersConfiguration.java`, the PostgreSQL and Redis image versions must be explicitly resolved. Do not leave `<postgres-version>` or `<redis-version>` in source code. If the versions cannot be established, stop Step 7 and report the missing information.

Changes:
- Replace `postgres:latest` with `postgres:<confirmed-version>-alpine`
- Replace `redis:latest` with `redis:<confirmed-version>-alpine`
- Change `PostgreSQLContainer` to `PostgreSQLContainer<?>` (remove raw type)

**Verification:**
```
.\mvnw test-compile
```
Expected: EXIT CODE 0. No unchecked-cast warnings from `PostgreSQLContainer`.

---

## J. Current-Machine Verification Matrix

All commands assume `.mvn/maven.config` exists (after Step 1). No Docker, no PostgreSQL, no Redis, no external credentials required for any item in this matrix.

| ID | Stage verified | Command | Expected result | Status |
|---|---|---|---|---|
| J1 | POM resolution | `.\mvnw test-compile` | EXIT 0; no `dockerhubdev` contact | After Step 1 |
| J2 | Main compilation | `.\mvnw compile` | EXIT 0; BUILD SUCCESS | After Step 1 |
| J3 | Test compilation (incl. Testcontainers) | `.\mvnw test-compile` | EXIT 0; "skip non existing resourceDirectory" | After Step 1 |
| J4 | Unit test execution — zero tests | `.\mvnw test` | EXIT 0; `Tests run: 0` | After Step 6 |
| J5 | No Spring context during `test` | J4 output | No `Starting JobTrackerApplication` or `ApplicationContext` in Surefire output | After Step 6 |
| J6 | No Docker during `mvn test` | J4 output | No Testcontainers or Docker output | After Step 6 |
| J7 | No credentials required for `mvn test` | Run J4 with no env vars set | EXIT 0; BUILD SUCCESS | After Step 6 |
| J8 | `application-local.yaml` gitignored | Create file, `git status` | File absent from untracked list | After Step 5 |
| J9 | `application-local.yaml.example` ready to commit | `git status` | File listed as untracked file ready to be added | After Step 4 |

**Distinctions:**
- J1 = POM resolution
- J2 = main source compilation
- J3 = test source compilation (separate from J2; confirms Testcontainers imports resolve)
- J4–J7 = Surefire test execution
- J5 = Spring context startup (confirmed NOT happening)
- J6 = Testcontainers/Docker runtime (confirmed NOT happening)

---

## K. Docker-Available Verification Matrix

These verifications require Docker Desktop to be installed and running. None are currently executable on the developer's machine.

| ID | Stage verified | Command | Expected result |
|---|---|---|---|
| K1 | Testcontainers container startup | `.\mvnw verify` (log) | `Started PostgreSQLContainer` and Redis container messages |
| K2 | PostgreSQL image version pinned | K1 log | Pinned image tag used (not `latest`) |
| K3 | Redis image version pinned | K1 log | Pinned image tag used (not `latest`) |
| K4 | Flyway runs with zero migrations | K1 log | `Successfully validated 0 migrations` |
| K5 | Spring context loads in IT | K1 log | `Started JobTrackerApplicationIT` without error |
| K6 | Integration test passes | `.\mvnw verify` | EXIT 0; `JobTrackerApplicationIT > contextLoads() PASSED` |
| K7 | Surefire unaffected | K1 log | Surefire: `Tests run: 0` before Failsafe runs |
| K8 | `mvn test` still clean after Docker is available | `.\mvnw test` | EXIT 0; `Tests run: 0`; no Docker contact |

**Distinctions within K:**
- K1 = Testcontainers runtime startup (Docker required)
- K2–K3 = image version verification (separate from K1; confirms the pinned tag is used)
- K4 = Flyway execution (requires real PostgreSQL from K1)
- K5 = Spring ApplicationContext startup (requires K1 + K4)
- K6 = JUnit test execution (requires K5)

---

## L. CI Strategy

### Stage 1 — Unit tests

No Docker required. No environment variables required.

```
./mvnw test
```

Runs: validate → compile → test-compile → Surefire.
Result at this foundation stage: `Tests run: 0, BUILD SUCCESS` (no business logic yet; unit tests are introduced alongside production code).

`.mvn/maven.config` is committed, so CI runners do not need `-s`. The company Nexus mirror is not present in CI runner `~/.m2/settings.xml`.

### Stage 2 — Integration tests

The integration-test job requires a Docker-capable CI runner.

```
./mvnw verify
```

`verify` runs the full lifecycle: compile → test-compile → Surefire → `integration-test` (Failsafe) → `verify` (Failsafe reports).

Surefire runs zero tests. Failsafe runs `JobTrackerApplicationIT`. Testcontainers provides PostgreSQL and Redis — no environment variables needed for the test itself.

No additional flags are required. `-DskipTests=false` is not used.

---

## M. Risks / Unknowns

| Item | Classification | Detail |
|---|---|---|
| Docker image versions | [REQUIRES VERIFICATION] | Project spec does not define image tags. Developer must confirm PostgreSQL and Redis versions in production/cloud before Step 7. Plan uses `<postgres-version>` and `<redis-version>` as placeholders. |
| First JPA entity without migration | Future risk — explicit constraint | `ddl-auto: validate` will fail at context startup if an entity exists without a corresponding Flyway migration. Entity + migration must be introduced together. |
| Mail features in production | No current risk | Mail is absent from `application.yaml`. When mail features are implemented, `spring.mail.*` properties must be added to the deployment environment configuration, not to `application.yaml`. |
| `testcontainers-postgresql` Testcontainers 2.x module | Resolved | Compilation confirmed [ACTUALLY VERIFIED]. Dependency unchanged. |
| Springdoc 3.1.0 / Spring Boot 4.1.1 | Resolved | Auto-configuration report shows Springdoc loading unconditionally [ACTUALLY VERIFIED]. Dependency unchanged. |
| Maven Nexus mirror | Syntax/behavior verified temporarily; repository fix pending implementation | `.mvn/maven.config` syntax was verified by temporary creation and test; the file was then deleted. The file must still be created as Step 1 and then re-verified in the current repository state. |

---

## N. Final Acceptance Criteria

### Group 1 — Current machine (no Docker, no credentials)

All items in this group are verifiable immediately after implementation.

- [ ] `.mvn/maven.config` exists with exactly two lines: `-s` and `.mvn/settings.xml`
- [ ] `.\mvnw test-compile` exits with code 0; BUILD SUCCESS; no contact with `dockerhubdev.trinitymobility.com`
- [ ] `.\mvnw compile` exits with code 0; BUILD SUCCESS
- [ ] `.\mvnw test` exits with code 0; BUILD SUCCESS; Surefire: `Tests run: 0` (expected at foundation stage — no business logic yet)
- [ ] `.\mvnw test` does not start any Spring `ApplicationContext`
- [ ] `.\mvnw test` does not start or connect to application infrastructure such as Docker, PostgreSQL, Redis, or mail services
- [ ] `.\mvnw test` runs without any environment variables set
- [ ] `JobTrackerApplicationTests.java` does not exist
- [ ] `JobTrackerApplicationIT.java` exists, compiles, and is annotated with `@SpringBootTest` and `@Import(TestcontainersConfiguration.class)`
- [ ] `application.yaml` contains no `spring.mail.*` properties
- [ ] `application.yaml` Hikari values exactly: `maximum-pool-size: 4`, `minimum-idle: 1`, `connection-timeout: 5000`, `idle-timeout: 300000`, `leak-detection-threshold: 30000`
- [ ] `application.yaml` contains `spring.datasource.url: ${SPRING_DATASOURCE_URL}`
- [ ] `application.yaml` contains `spring.data.redis.url: ${SPRING_DATA_REDIS_URL}`
- [ ] `application-local.yaml.example` exists in `src/main/resources/` and is intended to be committed (shows as untracked ready to be added)
- [ ] `application-local.yaml` does not appear in `git status` untracked files
- [ ] `pom.xml` contains `maven-failsafe-plugin` in `<build><plugins>`
- [ ] `pom.xml` dependency section is unchanged from current state
- [ ] `TestcontainersConfiguration.java` uses `PostgreSQLContainer<?>` (no raw type)
- [ ] `TestcontainersConfiguration.java` does not use `latest` image tags
- [ ] Docker image versions in `TestcontainersConfiguration.java` match confirmed production versions [REQUIRES VERIFICATION before Step 7]
- [ ] No literal placeholder (`<postgres-version>` or `<redis-version>`) remains in any source file after Step 7 is complete
- [ ] No secrets or credentials appear in any committed file

### Group 2 — When Docker is available (deferred)

- [ ] `.\mvnw verify` exits with code 0; BUILD SUCCESS
- [ ] Surefire: `Tests run: 0` at this stage (no regression; will grow as business logic is added)
- [ ] Failsafe: `JobTrackerApplicationIT > contextLoads() PASSED`
- [ ] Testcontainers starts PostgreSQL and Redis containers with pinned image tags
- [ ] Flyway log: `Successfully validated 0 migrations`
- [ ] Spring context starts without error in `JobTrackerApplicationIT`
- [ ] `.\mvnw test` continues to exit code 0 (ITs do not bleed into Surefire)
