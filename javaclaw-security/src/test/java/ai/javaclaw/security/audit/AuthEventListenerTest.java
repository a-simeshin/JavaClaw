package ai.javaclaw.security.audit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthEventListenerTest {

    @Mock
    private AuthAuditService authAuditService;

    @InjectMocks
    private AuthEventListener authEventListener;

    @Test
    void onAuthSuccess_logsLoginSuccess() {
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated("alice", null, java.util.List.of());
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

        authEventListener.onAuthSuccess(event);

        verify(authAuditService)
                .log(eq(AuthEventType.LOGIN_SUCCESS), eq("alice"), isNull(), isNull(), eq("Authentication successful"));
    }

    @Test
    void onAuthFailure_logsLoginFailure() {
        Authentication auth = UsernamePasswordAuthenticationToken.unauthenticated("bob", "wrong");
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");
        AbstractAuthenticationFailureEvent event = new AuthenticationFailureBadCredentialsEvent(auth, ex);

        authEventListener.onAuthFailure(event);

        verify(authAuditService)
                .log(eq(AuthEventType.LOGIN_FAILURE), eq("bob"), isNull(), isNull(), eq("Bad credentials"));
    }

    @Test
    void onAuthSuccess_usesAuthenticationName() {
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated("carol", null, java.util.List.of());
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

        authEventListener.onAuthSuccess(event);

        verify(authAuditService)
                .log(eq(AuthEventType.LOGIN_SUCCESS), eq("carol"), isNull(), isNull(), eq("Authentication successful"));
    }

    @Test
    void onAuthFailure_passesExceptionMessageAsDetail() {
        Authentication auth = UsernamePasswordAuthenticationToken.unauthenticated("dave", "pass");
        BadCredentialsException ex = new BadCredentialsException("Account locked");
        AbstractAuthenticationFailureEvent event = new AuthenticationFailureBadCredentialsEvent(auth, ex);

        authEventListener.onAuthFailure(event);

        verify(authAuditService)
                .log(eq(AuthEventType.LOGIN_FAILURE), eq("dave"), isNull(), isNull(), eq("Account locked"));
    }
}
