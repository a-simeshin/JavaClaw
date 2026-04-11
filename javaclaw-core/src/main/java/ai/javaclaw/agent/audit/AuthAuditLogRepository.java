package ai.javaclaw.agent.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface AuthAuditLogRepository extends ListCrudRepository<AuthAuditLog, Long> {

    List<AuthAuditLog> findByUsernameOrderByCreatedAtDesc(String username);

    List<AuthAuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType);

    List<AuthAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
