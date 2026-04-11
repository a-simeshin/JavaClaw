package ai.javaclaw.agent.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthAuditServiceTest {

    private AuthAuditLogRepository repository;
    private AuthAuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuthAuditLogRepository.class);
        service = new AuthAuditService(repository);
    }

    @Test
    void logLoginSuccess_savesRecord() {
        service.logLoginSuccess("admin", "127.0.0.1", "/api/chat");

        verify(repository).save(argThat(log -> {
            assertThat(log.eventType()).isEqualTo(AuthAuditLog.EVENT_LOGIN_SUCCESS);
            assertThat(log.username()).isEqualTo("admin");
            assertThat(log.remoteAddr()).isEqualTo("127.0.0.1");
            assertThat(log.requestUri()).isEqualTo("/api/chat");
            return true;
        }));
    }

    @Test
    void logLoginFailure_savesRecordWithDetail() {
        service.logLoginFailure("hacker", "10.0.0.1", "/api/admin", "Bad credentials");

        verify(repository).save(argThat(log -> {
            assertThat(log.eventType()).isEqualTo(AuthAuditLog.EVENT_LOGIN_FAILURE);
            assertThat(log.username()).isEqualTo("hacker");
            assertThat(log.detail()).isEqualTo("Bad credentials");
            return true;
        }));
    }

    @Test
    void logAccessDenied_savesRecordWithDetail() {
        service.logAccessDenied("user", "172.16.0.5", "/api/skills", "ROLE_ADMIN required");

        verify(repository).save(argThat(log -> {
            assertThat(log.eventType()).isEqualTo(AuthAuditLog.EVENT_ACCESS_DENIED);
            assertThat(log.username()).isEqualTo("user");
            assertThat(log.detail()).isEqualTo("ROLE_ADMIN required");
            return true;
        }));
    }

    @Test
    void logLoginSuccess_swallowsRepositoryException() {
        doThrow(new RuntimeException("DB down")).when(repository).save(any());

        // Should not throw
        service.logLoginSuccess("admin", "127.0.0.1", "/api/chat");

        verify(repository).save(any());
    }

    @Test
    void logLoginFailure_swallowsRepositoryException() {
        doThrow(new RuntimeException("DB down")).when(repository).save(any());

        service.logLoginFailure("hacker", "10.0.0.1", "/api/admin", "Bad credentials");

        verify(repository).save(any());
    }

    @Test
    void logAccessDenied_swallowsRepositoryException() {
        doThrow(new RuntimeException("DB down")).when(repository).save(any());

        service.logAccessDenied("user", null, null, null);

        verify(repository).save(any());
    }

    @Test
    void findByUsername_delegatesToRepository() {
        var expected = List.of(AuthAuditLog.loginSuccess("admin", null, null));
        when(repository.findByUsernameOrderByCreatedAtDesc("admin")).thenReturn(expected);

        var result = service.findByUsername("admin");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void findByEventType_delegatesToRepository() {
        var expected = List.of(AuthAuditLog.loginFailure("x", null, null, "Bad credentials"));
        when(repository.findByEventTypeOrderByCreatedAtDesc("login_failure")).thenReturn(expected);

        var result = service.findByEventType("login_failure");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void findByDateRange_delegatesToRepository() {
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-12-31T23:59:59Z");
        when(repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)).thenReturn(List.of());

        var result = service.findByDateRange(from, to);

        assertThat(result).isEmpty();
        verify(repository).findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    @Test
    void findAll_delegatesToRepository() {
        when(repository.findAll()).thenReturn(List.of());

        var result = service.findAll();

        assertThat(result).isEmpty();
        verify(repository).findAll();
    }
}
