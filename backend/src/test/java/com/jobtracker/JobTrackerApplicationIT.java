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
