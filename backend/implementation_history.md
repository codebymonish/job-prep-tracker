# HANDOFF CONTEXT: Job Prep Tracker — Backend Foundation Complete

> **This document was verified against the actual files on disk on 2026-09-14.**
> Every code block in Section 5 (Current File State) was read directly from the repository.
> This file is self-contained. The receiving agent does not need any other file.

---

## 1. Project Identifiers

| Property | Value |
|---|---|
| OS | Windows |
| Repository root | `C:\dev\job-prep-tracker` |
| Backend module | `C:\dev\job-prep-tracker\backend` |
| Active branch | `feature/project-foundation` |
| Git state | Changes NOT staged for commit. **DO NOT commit.** |
| Spring Boot | 4.1.1 |
| Java | 21 |
| Testcontainers | 2.0.5 (managed by Spring Boot 4.1.1 BOM) |
| PostgreSQL image | `postgres:17.11` (pinned, no `-alpine`) |
| Redis image | `redis:7.2.16` (pinned, no `-alpine`) |
| Spring AI | 2.0.1 |
| Spring Cloud | 2025.1.3 |

---

## 2. Strict Operating Rules — MUST Be Respected by Any Agent

These were mandated explicitly by the user. Violating any of them is not permitted.

1. Read the entire plan before making any changes.
2. Inspect the actual repository before editing.
3. Do not redesign the architecture.
4. Do not make architectural decisions that contradict the plan.
5. Do not change `pom.xml` dependencies **unless an actual build/runtime error requires it** (this rule was invoked once — see Section 4, Step 7).
6. Do NOT modify `C:\Users\monish.roy\.m2\settings.xml`. That is the company Maven settings file and is unrelated to this project.
7. The project uses `.mvn/settings.xml` via `.mvn/maven.config`. Never bypass this.
8. Do not use company SQL Server, SSMS, database, credentials, or infrastructure.
9. **No git operations.** Do not commit, push, reset, or modify branch history in any way.
10. **No Docker image placeholders.** Before touching `TestcontainersConfiguration.java`, versions must be resolved. If they cannot be confirmed, stop and report. Do not leave `<postgres-version>` or `<redis-version>` in source.

---

## 3. Company Infrastructure Context

- **Company Nexus mirror URL:** `http://dockerhubdev.trinitymobility.com:8081/repository/maven-public`
- **User-level Maven settings:** `C:\Users\monish.roy\.m2\settings.xml`
- **Mirror configuration in user settings:**
  ```xml
  <mirror>
    <id>com.trinity</id>
    <mirrorOf>*</mirrorOf>
    <url>http://dockerhubdev.trinitymobility.com:8081/repository/maven-public</url>
  </mirror>
  ```
- `mirrorOf: *` intercepts all Maven repository requests.
- The host `dockerhubdev.trinitymobility.com` is **unreachable** from the developer's environment.
- Without the project-level fix (`.mvn/maven.config`), `.\mvnw` fails immediately at POM resolution.
- **The fix:** `.mvn/maven.config` passes `-s .mvn/settings.xml` automatically, selecting the project-local settings that point to Maven Central instead.

---

## 4. What Was Done — Chronological History

### Steps 1–6 (executed by a prior agent session called "Gemini")

| Step | Action |
|---|---|
| 1 | Created `.mvn/maven.config` (fixes Maven mirror blocker) |
| 2 | Added `maven-failsafe-plugin` to `pom.xml` |
| 3 | Replaced `application.yaml` (4-line stub → full config) |
| 4 | Created `application-local.yaml.example` |
| 5 | Added `src/main/resources/application-local.yaml` to `.gitignore` |
| 6 | Deleted `JobTrackerApplicationTests.java`; created `JobTrackerApplicationIT.java` |

**Intentional halt before Step 7:** Per Rule 10, the agent stopped because Docker image versions were not confirmed. The user reviewed the implementation, verified `TestcontainersConfiguration.java` was untouched, and approved the halt.

### Step 7 (executed in the current session)

The user provided: `postgres:17.11` and `redis:7.2.16`.

**Attempt 1 — Compilation failure:**
- Plan said to change `PostgreSQLContainer` to `PostgreSQLContainer<?>`.
- Error: `cannot use '<>' with non-generic class org.testcontainers.postgresql.PostgreSQLContainer`
- **Root cause:** The `testcontainers-postgresql` jar bundled by Spring Boot 4.1.1 / Testcontainers 2.0.5 exposes `PostgreSQLContainer` as a raw (non-generic) class.
- **Fix:** Use raw type `PostgreSQLContainer` (no `<?>`). This is correct for this version — not a workaround.

**Attempt 2 — PostgreSQL timezone rejection:**
- Error: `FlywaySqlUnableToConnectToDbException: FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`
- **Root cause:** The Windows JVM default timezone is `Asia/Calcutta`. PostgreSQL 17 rejects this legacy IANA alias. The JDBC driver passes the JVM timezone to the server at connection time.
- **Fix (invoked Rule 5):** Added `<user.timezone>UTC</user.timezone>` to `maven-failsafe-plugin` `<systemPropertyVariables>` in `pom.xml`. This forces the Failsafe integration-test JVM — and therefore the JDBC connection — to use UTC.

**Attempt 3 — Spring Boot Redis URL parse failure:**
- Error: `DataRedisUrlSyntaxException: Invalid Redis URL '${SPRING_DATA_REDIS_URL}'` / `URISyntaxException: Illegal character in path at index 1`
- **Root cause:** Spring Boot 4.1.1 eagerly parses `spring.data.redis.url` as a `java.net.URI` during auto-configuration, before Testcontainers `@ServiceConnection` has had a chance to override the host/port. The literal placeholder string `${SPRING_DATA_REDIS_URL}` fails URI parsing.
- **Fix:** Added `properties = "spring.data.redis.url="` to `@SpringBootTest` in `JobTrackerApplicationIT.java`. This nullifies the property in the test Spring `Environment`, forcing the context to rely entirely on the `@ServiceConnection`-provided values.

**Final result:** `.\mvnw verify` → EXIT CODE 0, BUILD SUCCESS. Hibernate log confirmed: `Database version: 17.11`. 1 Failsafe integration test passed.

---

## 5. Current File State — Full Contents Verified From Disk

Every file below was read directly from the repository immediately before this document was written.

---

### `.mvn/maven.config`
**Path:** `C:\dev\job-prep-tracker\backend\.mvn\maven.config`
```
-s
.mvn/settings.xml
```
> Two lines only. No trailing spaces. Line 1 = `-s`. Line 2 = `.mvn/settings.xml`.
> **Critical:** Single-line form `-s .mvn/settings.xml` fails — Maven parses the whole line as one token, producing path ` .mvn/settings.xml` (with leading space). Two-line form is the only correct syntax.

---

### `.mvn/settings.xml`
**Path:** `C:\dev\job-prep-tracker\backend\.mvn\settings.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>

<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">

    <mirrors>
        <mirror>
            <id>central</id>
            <name>Maven Central</name>
            <url>https://repo.maven.apache.org/maven2</url>
            <mirrorOf>*</mirrorOf>
        </mirror>
    </mirrors>

</settings>
```
> This file was **not modified**. It pre-existed. It is the project-level settings that routes all Maven traffic to Maven Central (bypassing the company Nexus).

---

### `pom.xml`
**Path:** `C:\dev\job-prep-tracker\backend\pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.jobtracker</groupId>
	<artifactId>job-tracker</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name/>
	<description/>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>21</java.version>
		<spring-ai.version>2.0.1</spring-ai.version>
		<spring-cloud.version>2025.1.3</spring-cloud.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-redis-reactive</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-mail</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>3.1.0</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-pdf-document-reader</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-tika-document-reader</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.cloud</groupId>
			<artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
		</dependency>

		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-redis-reactive-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-mail-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-testcontainers</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-spring-boot-testcontainers</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-junit-jupiter</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-postgresql</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-dependencies</artifactId>
				<version>${spring-cloud.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
			<dependency>
				<groupId>org.springframework.ai</groupId>
				<artifactId>spring-ai-bom</artifactId>
				<version>${spring-ai.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-failsafe-plugin</artifactId>
				<!-- version managed by spring-boot-starter-parent -->
				<configuration>
					<systemPropertyVariables>
						<user.timezone>UTC</user.timezone>
					</systemPropertyVariables>
				</configuration>
				<executions>
					<execution>
						<goals>
							<goal>integration-test</goal>
							<goal>verify</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

</project>
```
> **Key notes for next agent:**
> - Dependency section was **not changed** from the original — all 8 focused Spring Boot test starters are present as-is.
> - The **only** change to `pom.xml` from what Gemini left was adding `<configuration><systemPropertyVariables><user.timezone>UTC</user.timezone>` inside the failsafe plugin (Step 7, Attempt 2 fix).
> - No version was pinned on `maven-failsafe-plugin` — version is managed by `spring-boot-starter-parent`.

---

### `src/main/resources/application.yaml`
**Path:** `C:\dev\job-prep-tracker\backend\src\main\resources\application.yaml`
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
> **No `spring.mail.*` properties.** Mail is an optional integration — including it here would make `SPRING_MAIL_HOST` mandatory for every context startup. Mail config belongs in `application-local.yaml.example` only.
> **`spring.data.redis.url` IS present** even though it causes `DataRedisUrlSyntaxException` in integration tests — that is handled by overriding the property in `@SpringBootTest(properties = "spring.data.redis.url=")`. The property must stay here for production fail-fast behavior.

---

### `src/main/resources/application-local.yaml.example`
**Path:** `C:\dev\job-prep-tracker\backend\src\main\resources\application-local.yaml.example`
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
> This file is **committed** to the repository. It is a template; it contains no real credentials.

---

### `.gitignore`
**Path:** `C:\dev\job-prep-tracker\backend\.gitignore`
```
HELP.md
target/
.mvn/wrapper/maven-wrapper.jar
!**/src/main/**/target/
!**/src/test/**/target/

### STS ###
.apt_generated
.classpath
.factorypath
.project
.settings
.springBeans
.sts4-cache

### IntelliJ IDEA ###
.idea
*.iws
*.iml
*.ipr

### NetBeans ###
/nbproject/private/
/nbbuild/
/dist/
/nbdist/
/.nb-gradle/
build/
!**/src/main/**/build/
!**/src/test/**/build/

### VS Code ###
.vscode/

src/main/resources/application-local.yaml
```
> The last line `src/main/resources/application-local.yaml` was added during Step 5. This ensures developers' personal overrides are never accidentally committed.

---

### `src/test/java/com/jobtracker/TestcontainersConfiguration.java`
**Path:** `C:\dev\job-prep-tracker\backend\src\test\java\com\jobtracker\TestcontainersConfiguration.java`
```java
package com.jobtracker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7.2.16")).withExposedPorts(6379);
	}

}
```
> **Important for next agent:**
> - `PostgreSQLContainer` is the **raw type** (no `<?>`). Using `PostgreSQLContainer<?>` causes a compile error with Testcontainers 2.0.5 because that version's class is non-generic.
> - `GenericContainer<?>` retains its generic type — this is correct and unaffected.
> - Images are `postgres:17.11` and `redis:7.2.16` — no `-alpine` suffix (the user confirmed these exact tags from already-downloaded images).

---

### `src/test/java/com/jobtracker/JobTrackerApplicationIT.java`
**Path:** `C:\dev\job-prep-tracker\backend\src\test\java\com\jobtracker\JobTrackerApplicationIT.java`
```java
package com.jobtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "spring.data.redis.url=")
@Import(TestcontainersConfiguration.class)
class JobTrackerApplicationIT {

    @Test
    void contextLoads() {
    }

}
```
> **Critical:** `properties = "spring.data.redis.url="` (empty string) is mandatory. Without it, Spring Boot 4.1.1 attempts to parse the literal `${SPRING_DATA_REDIS_URL}` string as a URI during auto-configuration, before `@ServiceConnection` takes effect, causing `DataRedisUrlSyntaxException`.
> This file matches the Failsafe pattern `**/*IT.java`. It does NOT match Surefire patterns. It will never run during `.\mvnw test`.

---

### `src/test/java/com/jobtracker/TestJobTrackerApplication.java`
**Path:** `C:\dev\job-prep-tracker\backend\src\test\java\com\jobtracker\TestJobTrackerApplication.java`
```java
package com.jobtracker;

import org.springframework.boot.SpringApplication;

public class TestJobTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
```
> This file was **not modified**. It is the developer convenience entry point for running the app locally with Docker containers. Requires Docker. Lives in `src/test/java` intentionally.

---

### DELETED: `src/test/java/com/jobtracker/JobTrackerApplicationTests.java`
This file **no longer exists** in the repository. It was deleted during Step 6.
It was a Surefire test with `@SpringBootTest` that failed because no DataSource was configured. Replaced by `JobTrackerApplicationIT.java` under Failsafe.

---

## 6. Maven Lifecycle Reference

| Command | What runs | Docker needed | Env vars needed | Expected result |
|---|---|---|---|---|
| `.\mvnw test-compile` | POM resolution + compile + test-compile | No | No | EXIT 0 |
| `.\mvnw test` | Surefire only | No | No | EXIT 0, Tests run: 0 |
| `.\mvnw verify` | Surefire + Failsafe (integration tests) | **Yes** | No | EXIT 0, 1 IT passed |

> `.\mvnw test` produces `Tests run: 0` intentionally — no Surefire tests exist yet. This is the correct steady state until business logic is written.

---

## 7. Architectural Constraints Going Forward

1. **Entity + Migration together:** Every new `@Entity` MUST be introduced alongside a Flyway migration script. `ddl-auto: validate` will throw `SchemaManagementException: Missing table` if an entity exists without a table. There is no bypass.
2. **Mail is optional:** `spring.mail.host` must NOT be added to `application.yaml`. It goes in deployment environment config only. Adding it to `application.yaml` makes it mandatory everywhere.
3. **No `latest` image tags:** If new Testcontainers containers are added, they must use pinned version tags.
4. **No Surefire integration tests:** Any class that loads a Spring context must be named `*IT.java` and live in the Failsafe lifecycle.
5. **No commit:** The working tree has uncommitted changes. The user has explicitly prohibited git operations.

---

## 8. Key Technical Facts (For Future Debugging)

- **`PostgreSQLContainer` is non-generic in Testcontainers 2.0.5.** Do not add `<?>`.
- **PostgreSQL 17 rejects `Asia/Calcutta`.** The `user.timezone=UTC` system property in Failsafe is the fix. This is a permanent requirement for this developer's Windows machine.
- **Spring Boot 4.1.1 eagerly parses `spring.data.redis.url`.** The `@SpringBootTest(properties = "spring.data.redis.url=")` override is not a hack — it is the required pattern for any integration test that uses a Testcontainers Redis `@ServiceConnection` with this Spring Boot version.
- **`.mvn/maven.config` uses two-line syntax.** Confirmed working. Single-line fails silently with a path error.
- **The company mirror (`dockerhubdev.trinitymobility.com`) intercepts `mirrorOf=*`.** Any agent that removes `.mvn/maven.config` will break the build immediately.
