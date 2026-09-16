package com.jobtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {"spring.profiles.active=test"})
@Import(TestcontainersConfiguration.class)
public class FlywayMigrationIT {

    @Test
    void migrationsApplySuccessfully() {
        // If the context loads, Flyway has successfully applied the migrations
        // because we have spring-boot-starter-flyway and the Testcontainers DB running.
    }
}
