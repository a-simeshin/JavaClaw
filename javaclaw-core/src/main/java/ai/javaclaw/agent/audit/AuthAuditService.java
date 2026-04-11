package ai.javaclaw.agent.audit;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for recording and querying authentication/authorization audit events.
 * All write operations are async to avoid blocking the auth flow.
 */
@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final AuthAuditLogRepository repository;

    public AuthAuditService(AuthAuditLogRepository repository) {
        this.repository = repository;
    }

    @Async
    public void logLoginSuccess(String username, String remoteAddr, String requestUri) {
        try {
            repository.save(AuthAuditLog.loginSuccess(username, remoteAddr, requestUri));
        } catch (Exception e) {
            log.warn("Failed to save auth audit (login_success): {}", e.getMessage());
        }
    }

    @Async
    public void logLoginFailure(String username, String remoteAddr, String requestUri, String detail) {
        try {
            repository.save(AuthAuditLog.loginFailure(username, remoteAddr, requestUri, detail));
        } catch (Exception e) {
            log.warn("Failed to save auth audit (login_failure): {}", e.getMessage());
        }
    }

    @Async
    public void logAccessDenied(String username, String remoteAddr, String requestUri, String detail) {
        try {
            repository.save(AuthAuditLog.accessDenied(username, remoteAddr, requestUri, detail));
        } catch (Exception e) {
            log.warn("Failed to save auth audit (access_denied): {}", e.getMessage());
        }
    }

    public List<AuthAuditLog> findByUsername(String username) {
        return repository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<AuthAuditLog> findByEventType(String eventType) {
        return repository.findByEventTypeOrderByCreatedAtDesc(eventType);
    }

    public List<AuthAuditLog> findByDateRange(Instant from, Instant to) {
        return repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    public List<AuthAuditLog> findAll() {
        return repository.findAll();
    }
}
