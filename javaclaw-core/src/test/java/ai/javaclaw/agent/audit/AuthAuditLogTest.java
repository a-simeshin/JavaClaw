package ai.javaclaw.agent.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthAuditLogTest {

    @Test
    void loginSuccess_setsAllFields() {
        AuthAuditLog log = AuthAuditLog.loginSuccess("admin", "192.168.1.1", "/api/chat");

        assertThat(log.id()).isNull();
        assertThat(log.eventType()).isEqualTo(AuthAuditLog.EVENT_LOGIN_SUCCESS);
        assertThat(log.username()).isEqualTo("admin");
        assertThat(log.remoteAddr()).isEqualTo("192.168.1.1");
        assertThat(log.requestUri()).isEqualTo("/api/chat");
        assertThat(log.detail()).isNull();
        assertThat(log.createdAt()).isNotNull();
    }

    @Test
    void loginFailure_setsDetailAndEventType() {
        AuthAuditLog log = AuthAuditLog.loginFailure("hacker", "10.0.0.1", "/api/admin", "Bad credentials");

        assertThat(log.eventType()).isEqualTo(AuthAuditLog.EVENT_LOGIN_FAILURE);
        assertThat(log.username()).isEqualTo("hacker");
        assertThat(log.remoteAddr()).isEqualTo("10.0.0.1");
        assertThat(log.requestUri()).isEqualTo("/api/admin");
        assertThat(log.detail()).isEqualTo("Bad credentials");
        assertThat(log.createdAt()).isNotNull();
    }

    @Test
    void accessDenied_setsDetailAndEventType() {
        AuthAuditLog log = AuthAuditLog.accessDenied("user", "172.16.0.5", "/api/skills", "ROLE_ADMIN required");

        assertThat(log.eventType()).isEqualTo(AuthAuditLog.EVENT_ACCESS_DENIED);
        assertThat(log.username()).isEqualTo("user");
        assertThat(log.remoteAddr()).isEqualTo("172.16.0.5");
        assertThat(log.requestUri()).isEqualTo("/api/skills");
        assertThat(log.detail()).isEqualTo("ROLE_ADMIN required");
    }

    @Test
    void loginSuccess_withNullFields() {
        AuthAuditLog log = AuthAuditLog.loginSuccess("admin", null, null);

        assertThat(log.eventType()).isEqualTo("login_success");
        assertThat(log.username()).isEqualTo("admin");
        assertThat(log.remoteAddr()).isNull();
        assertThat(log.requestUri()).isNull();
    }

    @Test
    void eventTypeConstants_haveExpectedValues() {
        assertThat(AuthAuditLog.EVENT_LOGIN_SUCCESS).isEqualTo("login_success");
        assertThat(AuthAuditLog.EVENT_LOGIN_FAILURE).isEqualTo("login_failure");
        assertThat(AuthAuditLog.EVENT_ACCESS_DENIED).isEqualTo("access_denied");
    }
}
