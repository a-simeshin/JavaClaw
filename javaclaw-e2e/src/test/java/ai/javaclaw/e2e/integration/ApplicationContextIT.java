package ai.javaclaw.e2e.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/**
 * Smoke integration test: boots the full Spring Boot application against a
 * PostgreSQL Testcontainer and verifies the actuator health endpoint responds.
 * Serves as the baseline that the Spring context wires correctly.
 */
class ApplicationContextIT extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Test
    void actuatorHealthIsUp() {
        RestClient client =
                RestClient.builder().baseUrl("http://localhost:" + port).build();

        String body = client.get().uri("/actuator/health").retrieve().body(String.class);

        assertThat(body).contains("UP");
    }
}
