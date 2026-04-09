package ai.javaclaw.delivery;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface DeliveryQueueRepository extends ListCrudRepository<DeliveryQueue, String> {

    List<DeliveryQueue> findByStatus(DeliveryQueue.Status status);

    List<DeliveryQueue> findByStatusAndNextRetryAtBefore(DeliveryQueue.Status status, Instant now);

    List<DeliveryQueue> findByTaskId(String taskId);

    List<DeliveryQueue> findByConversationId(String conversationId);
}
