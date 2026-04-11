package ai.javaclaw.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void badCredentials_returns401WithCorrectType() {
        ProblemDetail detail = handler.handleBadCredentials(new BadCredentialsException("bad"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(detail.getDetail()).isEqualTo("Invalid credentials");
        assertThat(detail.getType().toString()).isEqualTo("about:auth/bad-credentials");
    }

    @Test
    void locked_returns423WithCorrectType() {
        ProblemDetail detail = handler.handleLocked(new LockedException("locked"));

        assertThat(detail.getStatus()).isEqualTo(423);
        assertThat(detail.getDetail()).isEqualTo("Account is locked");
        assertThat(detail.getType().toString()).isEqualTo("about:auth/account-locked");
    }

    @Test
    void disabled_returns403WithCorrectType() {
        ProblemDetail detail = handler.handleDisabled(new DisabledException("disabled"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(detail.getDetail()).isEqualTo("Account is disabled");
        assertThat(detail.getType().toString()).isEqualTo("about:auth/account-disabled");
    }
}
