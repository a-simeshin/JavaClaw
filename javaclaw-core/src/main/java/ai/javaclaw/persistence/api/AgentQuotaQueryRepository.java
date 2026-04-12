package ai.javaclaw.persistence.api;

/**
 * Dialect-sensitive UPDATE statements against the {@code agent_quotas} table.
 *
 * <p>Extracted from {@code AgentQuotaRepository} so the {@code now()} /
 * {@code CURRENT_DATE} calls are isolated behind a dialect-conditional bean.
 */
public interface AgentQuotaQueryRepository {

    /** Increments {@code daily_used} by one and bumps {@code updated_at}. */
    int incrementDailyUsed(String userId);

    /** Resets {@code daily_used} to 0, stamps {@code reset_date} with today, bumps {@code updated_at}. */
    int resetDaily(String userId);

    /** Updates {@code daily_limit} and bumps {@code updated_at}. */
    int updateDailyLimit(String userId, int limit);
}
