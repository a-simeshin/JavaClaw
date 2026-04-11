package ai.javaclaw.agent.quota;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Per-user daily agent usage quota. Tracks how many chat requests a user has made today,
 * with automatic daily reset when the date changes.
 */
@Table("agent_quotas")
public record AgentQuota(
        @Id Long id,
        @Column("user_id") String userId,
        @Column("daily_limit") int dailyLimit,
        @Column("daily_used") int dailyUsed,
        @Column("reset_date") LocalDate resetDate) {

    public static AgentQuota createDefault(final String userId, final int defaultDailyLimit) {
        return new AgentQuota(null, userId, defaultDailyLimit, 0, LocalDate.now());
    }

    public boolean isExceeded() {
        return dailyUsed >= dailyLimit;
    }

    public AgentQuota withIncrementedUsage() {
        return new AgentQuota(id, userId, dailyLimit, dailyUsed + 1, resetDate);
    }

    public AgentQuota withDailyReset() {
        return new AgentQuota(id, userId, dailyLimit, 0, LocalDate.now());
    }
}
