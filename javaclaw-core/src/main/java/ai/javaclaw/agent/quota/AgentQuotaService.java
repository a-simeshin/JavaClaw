package ai.javaclaw.agent.quota;

import ai.javaclaw.persistence.api.AgentQuotaQueryRepository;
import ai.javaclaw.tasks.RateLimitExceededException;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user daily agent request quota enforcement.
 * Checks and increments usage before each chat request.
 * Automatically resets daily counters when date changes.
 * ADMIN users bypass quota checks.
 */
@Service
public class AgentQuotaService {

    private final AgentQuotaRepository repository;
    private final AgentQuotaQueryRepository queryRepository;
    private final int defaultDailyLimit;
    private final boolean enabled;

    public AgentQuotaService(
            final AgentQuotaRepository repository,
            final AgentQuotaQueryRepository queryRepository,
            @Value("${javaclaw.agent.quota.default-daily-limit:100}") final int defaultDailyLimit,
            @Value("${javaclaw.agent.quota.enabled:true}") final boolean enabled) {
        this.repository = repository;
        this.queryRepository = queryRepository;
        this.defaultDailyLimit = defaultDailyLimit;
        this.enabled = enabled;
    }

    /**
     * Checks quota for the user and increments usage atomically.
     * Creates a quota record with defaults if none exists.
     * Resets counter if the date has rolled over.
     *
     * @throws RateLimitExceededException if daily quota exceeded
     */
    @Transactional
    public void checkAndIncrement(final @Nullable String userId) {
        if (!enabled || userId == null) {
            return;
        }
        AgentQuota quota = ensureQuota(userId);
        if (needsReset(quota)) {
            queryRepository.resetDaily(userId);
            quota = quota.withDailyReset();
        }
        if (quota.isExceeded()) {
            throw new RateLimitExceededException(userId, "daily_agent_quota", quota.dailyUsed(), quota.dailyLimit());
        }
        queryRepository.incrementDailyUsed(userId);
    }

    /**
     * Returns current quota state for a user, or null if not tracked.
     */
    public @Nullable AgentQuota getQuota(final String userId) {
        return repository
                .findByUserId(userId)
                .map(q -> {
                    if (needsReset(q)) {
                        return q.withDailyReset();
                    }
                    return q;
                })
                .orElse(null);
    }

    /**
     * Sets a custom daily limit for a specific user.
     */
    @Transactional
    public void setDailyLimit(final String userId, final int limit) {
        ensureQuota(userId);
        queryRepository.updateDailyLimit(userId, limit);
    }

    public int getDefaultDailyLimit() {
        return defaultDailyLimit;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private AgentQuota ensureQuota(final String userId) {
        Optional<AgentQuota> existing = repository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return repository.save(AgentQuota.createDefault(userId, defaultDailyLimit));
    }

    private static boolean needsReset(final AgentQuota quota) {
        return !LocalDate.now().equals(quota.resetDate());
    }
}
