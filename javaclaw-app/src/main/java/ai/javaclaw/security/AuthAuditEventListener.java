package ai.javaclaw.security;

import ai.javaclaw.agent.audit.AuthAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.stereotype.Component;

/**
 * Listens for Spring Security authentication and authorization events
 * and records them in the auth audit log for compliance (12.1).
 */
@Component
public class AuthAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditEventListener.class);

    private final AuthAuditService authAuditService;

    public AuthAuditEventListener(AuthAuditService authAuditService) {
        this.authAuditService = authAuditService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        var auth = event.getAuthentication();
        String username = auth.getName();
        // In stateless HTTP Basic, remote addr is not directly available from the event
        authAuditService.logLoginSuccess(username, null, null);
        log.debug("Auth audit: login_success for user '{}'", username);
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        var auth = event.getAuthentication();
        String username = auth.getName();
        String detail = event.getException().getMessage();
        authAuditService.logLoginFailure(username, null, null, detail);
        log.debug("Auth audit: login_failure for user '{}': {}", username, detail);
    }

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        var auth = event.getAuthentication().get();
        String username = auth != null ? auth.getName() : "anonymous";
        String detail = event.getAuthorizationResult() != null
                ? event.getAuthorizationResult().toString()
                : null;
        authAuditService.logAccessDenied(username, null, null, detail);
        log.debug("Auth audit: access_denied for user '{}'", username);
    }
}
