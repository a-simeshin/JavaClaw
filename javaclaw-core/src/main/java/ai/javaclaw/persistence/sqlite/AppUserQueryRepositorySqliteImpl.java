package ai.javaclaw.persistence.sqlite;

import ai.javaclaw.persistence.api.AppUserQueryRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite implementation of {@link AppUserQueryRepository}. Replaces {@code now()}
 * with {@code datetime('now')} and uses {@code 0} for the false literal since
 * SQLite stores booleans as integers.
 */
@Component
@ConditionalOnProperty(prefix = "javaclaw.persistence", name = "dialect", havingValue = "sqlite")
public class AppUserQueryRepositorySqliteImpl implements AppUserQueryRepository {

    private static final String DEACTIVATE_SQL =
            "UPDATE users SET active = 0, updated_at = datetime('now') WHERE id = :id";
    private static final String UPDATE_PASSWORD_SQL =
            "UPDATE users SET password_hash = :hash, updated_at = datetime('now') WHERE id = :id";
    private static final String UPDATE_ROLE_SQL =
            "UPDATE users SET role = :role, updated_at = datetime('now') WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbc;

    public AppUserQueryRepositorySqliteImpl(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int deactivate(final String userId) {
        return jdbc.update(DEACTIVATE_SQL, Map.of("id", userId));
    }

    @Override
    public int updatePassword(final String userId, final String passwordHash) {
        final Map<String, Object> params = new HashMap<>();
        params.put("id", userId);
        params.put("hash", passwordHash);
        return jdbc.update(UPDATE_PASSWORD_SQL, params);
    }

    @Override
    public int updateRole(final String userId, final String role) {
        final Map<String, Object> params = new HashMap<>();
        params.put("id", userId);
        params.put("role", role);
        return jdbc.update(UPDATE_ROLE_SQL, params);
    }
}
