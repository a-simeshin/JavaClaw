package ai.javaclaw.tasks;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface ApprovalRequestRepository extends ListCrudRepository<ApprovalRequest, String> {

    List<ApprovalRequest> findByConversationIdAndStatus(String conversationId, ApprovalRequest.Status status);

    List<ApprovalRequest> findByTaskId(String taskId);

    Optional<ApprovalRequest> findByTaskIdAndStatus(String taskId, ApprovalRequest.Status status);

    List<ApprovalRequest> findByStatusAndTimeoutAtBefore(ApprovalRequest.Status status, Instant now);
}
