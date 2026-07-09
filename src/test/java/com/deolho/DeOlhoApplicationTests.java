package com.deolho;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "deolho.ai.enabled=false"
})
class DeOlhoApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring context boots successfully
    }
}
