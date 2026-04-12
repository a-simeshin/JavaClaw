package ai.javaclaw.persistence.api;

/**
 * Dialect-sensitive UPDATE statements against the {@code users} table.
 *
 * <p>Extracted from {@code AppUserRepository} so the {@code now()} / {@code datetime('now')}
 * call is isolated behind a dialect-conditional bean.
 */
public interface AppUserQueryRepository {

    /** Marks the user inactive and bumps {@code updated_at} to the current timestamp. */
    int deactivate(String userId);

    /** Replaces the stored password hash and bumps {@code updated_at}. */
    int updatePassword(String userId, String passwordHash);

    /** Replaces the user's role and bumps {@code updated_at}. */
    int updateRole(String userId, String role);
}
