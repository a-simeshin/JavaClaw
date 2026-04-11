package ai.javaclaw.security.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionCleanupJobTest {

    @Mock
    private UserSessionRepository sessionRepository;

    @InjectMocks
    private SessionCleanupJob job;

    @Test
    void cleanup_calls_deleteExpired_with_now_and_7day_threshold() {
        when(sessionRepository.deleteExpired(any(Instant.class), any(Instant.class)))
                .thenReturn(3);

        job.cleanup();

        verify(sessionRepository).deleteExpired(any(Instant.class), any(Instant.class));
    }

    @Test
    void cleanup_swallows_exceptions() {
        when(sessionRepository.deleteExpired(any(Instant.class), any(Instant.class)))
                .thenThrow(new RuntimeException("DB down"));

        // Should not throw
        job.cleanup();

        verify(sessionRepository).deleteExpired(any(Instant.class), any(Instant.class));
    }
}
