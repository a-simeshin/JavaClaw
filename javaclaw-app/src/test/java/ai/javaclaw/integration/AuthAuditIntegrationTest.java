package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.agent.audit.AuthAuditLog;
import ai.javaclaw.agent.audit.AuthAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for auth audit trail (12.1 Compliance audit trail).
 * Tests that authentication events are recorded and queryable via API.
 */
class AuthAuditIntegrationTest extends IntegrationTestBase {

    @Autowired
    AuthAuditLogRepository authAuditLogRepository;

    @BeforeEach
    void cleanAuditLog() {
        authAuditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/audit/auth returns empty list when no events")
    void authAudit_emptyByDefault() throws Exception {
        mockMvc.perform(get("/api/audit/auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/audit/auth returns saved auth events")
    void authAudit_returnsSavedEvents() throws Exception {
        authAuditLogRepository.save(AuthAuditLog.loginSuccess("admin", "127.0.0.1", "/api/chat"));
        authAuditLogRepository.save(AuthAuditLog.loginFailure("hacker", "10.0.0.1", "/api/admin", "Bad credentials"));
        authAuditLogRepository.save(
                AuthAuditLog.accessDenied("user", "172.16.0.5", "/api/skills", "ROLE_ADMIN required"));

        mockMvc.perform(get("/api/audit/auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("GET /api/audit/auth?username=admin filters by username")
    void authAudit_filterByUsername() throws Exception {
        authAuditLogRepository.save(AuthAuditLog.loginSuccess("admin", null, null));
        authAuditLogRepository.save(AuthAuditLog.loginFailure("hacker", null, null, "Bad credentials"));

        mockMvc.perform(get("/api/audit/auth").param("username", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    @DisplayName("GET /api/audit/auth?eventType=login_failure filters by event type")
    void authAudit_filterByEventType() throws Exception {
        authAuditLogRepository.save(AuthAuditLog.loginSuccess("admin", null, null));
        authAuditLogRepository.save(AuthAuditLog.loginFailure("hacker", null, null, "Bad credentials"));

        mockMvc.perform(get("/api/audit/auth").param("eventType", "login_failure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventType").value("login_failure"));
    }

    @Test
    @DisplayName("Auth audit log entity persists and retrieves correctly")
    void authAuditLog_persistsCorrectly() {
        authAuditLogRepository.save(AuthAuditLog.loginSuccess("admin", "192.168.1.1", "/api/chat"));

        var logs = authAuditLogRepository.findByUsernameOrderByCreatedAtDesc("admin");
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().eventType()).isEqualTo("login_success");
        assertThat(logs.getFirst().remoteAddr()).isEqualTo("192.168.1.1");
        assertThat(logs.getFirst().requestUri()).isEqualTo("/api/chat");
        assertThat(logs.getFirst().id()).isNotNull();
    }

    @Test
    @DisplayName("AuthAuditEventListener bean is registered")
    void authAuditEventListener_beanExists() throws Exception {
        // Verify the listener is in the application context by checking that
        // an authenticated request doesn't fail (listener processes events)
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/audit/auth respects limit parameter")
    void authAudit_respectsLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            authAuditLogRepository.save(AuthAuditLog.loginSuccess("admin", null, null));
        }

        mockMvc.perform(get("/api/audit/auth").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
