package ai.javaclaw.security.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface UserSessionRepository extends CrudRepository<UserSession, String> {

    @Query("SELECT * FROM user_session WHERE token_hash = :tokenHash AND revoked_at IS NULL AND expires_at > :now")
    Optional<UserSession> findActiveByTokenHash(String tokenHash, Instant now);

    @Query("SELECT * FROM user_session WHERE user_id = :userId AND revoked_at IS NULL AND expires_at > :now")
    List<UserSession> findAllActiveByUserId(String userId, Instant now);

    @Modifying
    @Query("UPDATE user_session SET revoked_at = :revokedAt, revocation_reason = :reason WHERE id = :id")
    void revokeById(String id, Instant revokedAt, String reason);

    @Modifying
    @Query(
            "UPDATE user_session SET revoked_at = :revokedAt, revocation_reason = :reason WHERE user_id = :userId AND revoked_at IS NULL")
    int revokeAllByUserId(String userId, Instant revokedAt, String reason);

    @Modifying
    @Query("UPDATE user_session SET last_used_at = :lastUsedAt WHERE id = :id")
    void updateLastUsedAt(String id, Instant lastUsedAt);

    @Modifying
    @Query(
            "DELETE FROM user_session WHERE expires_at < :threshold OR (revoked_at IS NOT NULL AND revoked_at < :revokedThreshold)")
    int deleteExpired(Instant threshold, Instant revokedThreshold);
}
