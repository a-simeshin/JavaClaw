package ai.javaclaw.agent.quota;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JDBC repository for the {@code agent_quotas} table.
 *
 * <p>The three dialect-sensitive {@code UPDATE} statements that used to live here
 * have been extracted to
 * {@link ai.javaclaw.persistence.api.AgentQuotaQueryRepository}.
 */
@Repository
public interface AgentQuotaRepository extends ListCrudRepository<AgentQuota, Long> {

    Optional<AgentQuota> findByUserId(String userId);
}
