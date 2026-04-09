package ai.javaclaw.agent.audit;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface TaskAuditLogRepository extends ListCrudRepository<TaskAuditLog, Long> {

    List<TaskAuditLog> findByTaskIdOrderByCreatedAtAsc(String taskId);

    List<TaskAuditLog> findByExecutionIdOrderByCreatedAtAsc(String executionId);

    List<TaskAuditLog> findByTaskIdAndEventTypeOrderByCreatedAtAsc(String taskId, String eventType);
}
