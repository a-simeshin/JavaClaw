package ai.javaclaw.agent.audit;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface DeliveryAuditLogRepository extends ListCrudRepository<DeliveryAuditLog, Long> {

    List<DeliveryAuditLog> findByTaskIdOrderByCreatedAtAsc(String taskId);

    List<DeliveryAuditLog> findByConversationIdOrderByCreatedAtDesc(String conversationId);
}
