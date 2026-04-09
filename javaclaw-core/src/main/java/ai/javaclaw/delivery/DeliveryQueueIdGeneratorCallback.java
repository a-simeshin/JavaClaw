package ai.javaclaw.delivery;

import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
class DeliveryQueueIdGeneratorCallback implements BeforeConvertCallback<DeliveryQueue> {

    @Override
    public DeliveryQueue onBeforeConvert(final DeliveryQueue delivery) {
        if (delivery.getId() == null) {
            return new DeliveryQueue(
                    UUID.randomUUID().toString(),
                    delivery.getTaskId(),
                    delivery.getConversationId(),
                    delivery.getChannelName(),
                    delivery.getMessage(),
                    delivery.getStatus(),
                    delivery.getAttempts(),
                    delivery.getMaxAttempts(),
                    delivery.getErrorMessage(),
                    delivery.getClaimedBy(),
                    delivery.getClaimedAt(),
                    delivery.getNextRetryAt(),
                    delivery.getCreatedAt(),
                    delivery.getCompletedAt());
        }
        return delivery;
    }
}
