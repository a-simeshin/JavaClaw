package ai.javaclaw.security.session;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically removes expired sessions and sessions revoked more than 7 days ago.
 * Interval configurable via javaclaw.security.session.cleanup-interval (default PT1H).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private static final Duration REVOKED_RETENTION = Duration.ofDays(7);

    private final UserSessionRepository sessionRepository;

    @Scheduled(fixedDelayString = "${javaclaw.security.session.cleanup-interval:PT1H}")
    public void cleanup() {
        Instant now = Instant.now();
        Instant revokedThreshold = now.minus(REVOKED_RETENTION);
        try {
            int deleted = sessionRepository.deleteExpired(now, revokedThreshold);
            if (deleted > 0) {
                log.info("SessionCleanupJob: deleted {} expired/revoked sessions", deleted);
            }
        } catch (Exception e) {
            log.error("SessionCleanupJob failed", e);
        }
    }
}
