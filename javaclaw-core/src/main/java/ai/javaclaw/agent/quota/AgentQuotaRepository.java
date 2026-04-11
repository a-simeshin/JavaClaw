package ai.javaclaw.agent.quota;

import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentQuotaRepository extends ListCrudRepository<AgentQuota, Long> {

    Optional<AgentQuota> findByUserId(String userId);

    @Modifying
    @Query("UPDATE agent_quotas SET daily_used = daily_used + 1, updated_at = now() WHERE user_id = :userId")
    void incrementDailyUsed(String userId);

    @Modifying
    @Query(
            "UPDATE agent_quotas SET daily_used = 0, reset_date = CURRENT_DATE, updated_at = now() WHERE user_id = :userId")
    void resetDaily(String userId);

    @Modifying
    @Query("UPDATE agent_quotas SET daily_limit = :limit, updated_at = now() WHERE user_id = :userId")
    void updateDailyLimit(String userId, int limit);
}
