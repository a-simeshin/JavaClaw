package ai.javaclaw.agent.audit;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface ChatAuditLogRepository extends ListCrudRepository<ChatAuditLog, Long> {

    List<ChatAuditLog> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    List<ChatAuditLog> findByUserIdOrderByCreatedAtDesc(String userId);
}
