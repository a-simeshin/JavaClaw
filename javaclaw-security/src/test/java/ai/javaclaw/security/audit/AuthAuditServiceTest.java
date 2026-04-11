package ai.javaclaw.security.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuthAuditServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private AuthAuditService authAuditService;

    @Test
    void log_loginSuccess_callsJdbcUpdate() {
        authAuditService.log(AuthEventType.LOGIN_SUCCESS, "alice", "127.0.0.1", "/api/auth/login", null);

        verify(jdbcTemplate)
                .update(
                        anyString(),
                        argThat((MapSqlParameterSource params) -> "login_success".equals(params.getValue("eventType"))
                                && "alice".equals(params.getValue("username"))
                                && "127.0.0.1".equals(params.getValue("remoteAddr"))
                                && "/api/auth/login".equals(params.getValue("requestUri"))
                                && params.getValue("createdAt") != null));
    }

    @Test
    void log_loginFailure_passesDetailToJdbc() {
        authAuditService.log(AuthEventType.LOGIN_FAILURE, "bob", "10.0.0.1", "/api/auth/login", "Bad credentials");

        verify(jdbcTemplate)
                .update(
                        anyString(),
                        argThat((MapSqlParameterSource params) -> "login_failure".equals(params.getValue("eventType"))
                                && "bob".equals(params.getValue("username"))
                                && "Bad credentials".equals(params.getValue("detail"))));
    }

    @Test
    void log_jdbcThrows_doesNotPropagateException() {
        doThrow(new RuntimeException("DB connection lost"))
                .when(jdbcTemplate)
                .update(anyString(), any(MapSqlParameterSource.class));

        assertThatCode(() ->
                        authAuditService.log(AuthEventType.ACCESS_DENIED, "eve", "1.2.3.4", "/api/admin", "Forbidden"))
                .doesNotThrowAnyException();
    }

    @Test
    void log_nullUsername_allowsNullInParams() {
        authAuditService.log(AuthEventType.LOGOUT, null, "127.0.0.1", "/api/auth/logout", null);

        verify(jdbcTemplate)
                .update(
                        anyString(),
                        argThat((MapSqlParameterSource params) ->
                                "logout".equals(params.getValue("eventType")) && params.getValue("username") == null));
    }
}
