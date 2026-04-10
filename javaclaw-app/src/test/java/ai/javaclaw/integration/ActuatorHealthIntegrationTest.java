package ai.javaclaw.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Spring Boot Actuator health/info/metrics endpoints
 * and the custom {@code /api/health} endpoint.
 */
class ActuatorHealthIntegrationTest extends IntegrationTestBase {

    // ── Spring Actuator endpoints ──

    @Test
    @DisplayName("GET /actuator/health returns UP with DB indicator")
    void actuatorHealthReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/health shows db component details")
    void actuatorHealthShowsDbComponent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/health/readiness returns UP")
    void actuatorReadinessProbe() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/health/liveness returns UP")
    void actuatorLivenessProbe() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/info returns app info")
    void actuatorInfoReturnsAppInfo() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/metrics returns metric names")
    void actuatorMetricsReturnsNames() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    // ── Custom /api/health endpoint ──

    @Test
    @DisplayName("GET /api/health returns UP with db component")
    void customHealthReturnsUpWithDb() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.components.chat").value("UP"))
                .andExpect(jsonPath("$.components.memory").value("UP"))
                .andExpect(jsonPath("$.components.db").value("UP"));
    }
}
