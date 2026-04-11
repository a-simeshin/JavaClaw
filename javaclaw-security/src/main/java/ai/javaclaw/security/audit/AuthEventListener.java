package ai.javaclaw.security.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthEventListener {

    private final AuthAuditService authAuditService;

    @EventListener
    public void onAuthSuccess(AuthenticationSuccessEvent event) {
        authAuditService.log(
                AuthEventType.LOGIN_SUCCESS,
                event.getAuthentication().getName(),
                null,
                null,
                "Authentication successful");
    }

    @EventListener
    public void onAuthFailure(AbstractAuthenticationFailureEvent event) {
        authAuditService.log(
                AuthEventType.LOGIN_FAILURE,
                event.getAuthentication().getName(),
                null,
                null,
                event.getException().getMessage());
    }
}
