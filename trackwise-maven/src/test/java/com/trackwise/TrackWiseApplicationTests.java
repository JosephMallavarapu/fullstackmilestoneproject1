package com.trackwise;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TrackWiseApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts with DB config correctly
    }
}
