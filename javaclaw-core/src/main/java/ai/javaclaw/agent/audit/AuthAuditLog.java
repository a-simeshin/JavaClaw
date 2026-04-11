package ai.javaclaw.agent.audit;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Immutable audit record for authentication and authorization events.
 * Tracks login successes, login failures, and access denied events
 * for compliance audit trail (12.1).
 */
@Table("auth_audit_log")
public record AuthAuditLog(
        @Id Long id,
        @Column("event_type") String eventType,
        @Column("username") String username,
        @Column("remote_addr") String remoteAddr,
        @Column("request_uri") String requestUri,
        @Column("detail") String detail,
        @Column("created_at") Instant createdAt) {

    public static final String EVENT_LOGIN_SUCCESS = "login_success";
    public static final String EVENT_LOGIN_FAILURE = "login_failure";
    public static final String EVENT_ACCESS_DENIED = "access_denied";

    public static AuthAuditLog loginSuccess(String username, String remoteAddr, String requestUri) {
        return new AuthAuditLog(null, EVENT_LOGIN_SUCCESS, username, remoteAddr, requestUri, null, Instant.now());
    }

    public static AuthAuditLog loginFailure(String username, String remoteAddr, String requestUri, String detail) {
        return new AuthAuditLog(null, EVENT_LOGIN_FAILURE, username, remoteAddr, requestUri, detail, Instant.now());
    }

    public static AuthAuditLog accessDenied(String username, String remoteAddr, String requestUri, String detail) {
        return new AuthAuditLog(null, EVENT_ACCESS_DENIED, username, remoteAddr, requestUri, detail, Instant.now());
    }
}
