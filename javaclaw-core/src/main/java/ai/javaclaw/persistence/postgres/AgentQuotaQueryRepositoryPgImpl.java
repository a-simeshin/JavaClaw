package ai.javaclaw.persistence.postgres;

import ai.javaclaw.persistence.api.AgentQuotaQueryRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Postgres implementation of {@link AgentQuotaQueryRepository}. */
@Component
@ConditionalOnProperty(
        prefix = "javaclaw.persistence",
        name = "dialect",
        havingValue = "postgresql",
        matchIfMissing = true)
public class AgentQuotaQueryRepositoryPgImpl implements AgentQuotaQueryRepository {

    private static final String INCREMENT_SQL =
            "UPDATE agent_quotas SET daily_used = daily_used + 1, updated_at = now() WHERE user_id = :userId";
    private static final String RESET_SQL =
            "UPDATE agent_quotas SET daily_used = 0, reset_date = CURRENT_DATE, updated_at = now() WHERE user_id = :userId";
    private static final String UPDATE_LIMIT_SQL =
            "UPDATE agent_quotas SET daily_limit = :limit, updated_at = now() WHERE user_id = :userId";

    private final NamedParameterJdbcTemplate jdbc;

    public AgentQuotaQueryRepositoryPgImpl(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int incrementDailyUsed(final String userId) {
        return jdbc.update(INCREMENT_SQL, Map.of("userId", userId));
    }

    @Override
    public int resetDaily(final String userId) {
        return jdbc.update(RESET_SQL, Map.of("userId", userId));
    }

    @Override
    public int updateDailyLimit(final String userId, final int limit) {
        final Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("limit", limit);
        return jdbc.update(UPDATE_LIMIT_SQL, params);
    }
}
