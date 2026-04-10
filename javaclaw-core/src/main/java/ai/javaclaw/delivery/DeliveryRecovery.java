package ai.javaclaw.delivery;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Processes pending deliveries on application startup.
 *
 * <p>Handles two recovery scenarios:
 * <ol>
 *   <li>Pending deliveries that were queued but never sent (e.g., pod crashed before sending)</li>
 *   <li>Pending deliveries whose retry time has arrived</li>
 * </ol>
 *
 * <p>Multi-pod safety: each delivery is claimed via {@link DeliveryQueue#withClaimed} before
 * processing. In production, the query should use {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * to prevent duplicate processing across pods.
 */
@Component
public class DeliveryRecovery {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRecovery.class);

    private final DeliveryQueueRepository deliveryQueueRepository;
    private final ChannelRegistry channelRegistry;
    private final ChannelContextService channelContextService;
    private final DeliveryAuditLogRepository deliveryAuditLogRepository;
    private final String podId;

    public DeliveryRecovery(
            final DeliveryQueueRepository deliveryQueueRepository,
            final ChannelRegistry channelRegistry,
            final ChannelContextService channelContextService,
            final DeliveryAuditLogRepository deliveryAuditLogRepository) {
        this.deliveryQueueRepository = deliveryQueueRepository;
        this.channelRegistry = channelRegistry;
        this.channelContextService = channelContextService;
        this.deliveryAuditLogRepository = deliveryAuditLogRepository;
        this.podId = "pod-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingDeliveries() {
        log.info("DeliveryRecovery starting — scanning for pending deliveries (podId={})", podId);

        final List<DeliveryQueue> pendingDeliveries = findRecoverableDeliveries();

        if (pendingDeliveries.isEmpty()) {
            log.info("DeliveryRecovery — no pending deliveries found");
            return;
        }

        log.info("DeliveryRecovery — found {} pending deliveries to process", pendingDeliveries.size());

        int delivered = 0;
        int failed = 0;

        for (final DeliveryQueue delivery : pendingDeliveries) {
            try {
                processDelivery(delivery);
                delivered++;
            } catch (final Exception e) {
                failed++;
                log.error(
                        "DeliveryRecovery — failed to process delivery {} for task {}: {}",
                        delivery.getId(),
                        delivery.getTaskId(),
                        e.getMessage());
            }
        }

        log.info("DeliveryRecovery complete — delivered: {}, failed: {}", delivered, failed);
    }

    List<DeliveryQueue> findRecoverableDeliveries() {
        final List<DeliveryQueue> retryReady =
                deliveryQueueRepository.findByStatusAndNextRetryAtBefore(DeliveryQueue.Status.pending, Instant.now());

        final List<DeliveryQueue> allPending = deliveryQueueRepository.findByStatus(DeliveryQueue.Status.pending);

        final ArrayList<DeliveryQueue> result = new ArrayList<>(retryReady);
        // Include pending deliveries with no nextRetryAt (never attempted, first delivery)
        for (final DeliveryQueue d : allPending) {
            if (d.getNextRetryAt() == null && !result.contains(d)) {
                result.add(d);
            }
        }

        return result;
    }

    void processDelivery(final DeliveryQueue delivery) {
        final DeliveryQueue claimed = delivery.withClaimed(podId);
        deliveryQueueRepository.save(claimed);

        final long startTime = System.currentTimeMillis();

        try {
            final RoutingContext routingContext = channelContextService
                    .getContext(delivery.getConversationId())
                    .orElse(null);
            if (routingContext == null) {
                throw new IllegalStateException("No routing context for conversation " + delivery.getConversationId());
            }

            final Channel channel = channelRegistry.getChannel(delivery.getChannelName());
            channel.sendMessage(routingContext, delivery.getMessage());

            deliveryQueueRepository.save(claimed.withDelivered());

            final long durationMs = System.currentTimeMillis() - startTime;
            logDeliveryAudit(delivery, claimed.getAttempts() + 1, durationMs, null);

            log.debug(
                    "DeliveryRecovery — delivered {} to {} via {}",
                    delivery.getId(),
                    delivery.getConversationId(),
                    delivery.getChannelName());
        } catch (final Exception e) {
            deliveryQueueRepository.save(claimed.withFailed(e.getMessage()));

            final long durationMs = System.currentTimeMillis() - startTime;
            logDeliveryAudit(delivery, claimed.getAttempts() + 1, durationMs, e.getMessage());

            log.warn(
                    "DeliveryRecovery — delivery {} failed (attempt {}): {}",
                    delivery.getId(),
                    claimed.getAttempts() + 1,
                    e.getMessage());
        }
    }

    private void logDeliveryAudit(
            final DeliveryQueue delivery, final int attempts, final long durationMs, final String errorMessage) {
        try {
            final DeliveryAuditLog auditLog;
            if (errorMessage == null) {
                auditLog = DeliveryAuditLog.delivered(
                        delivery.getTaskId(),
                        delivery.getConversationId(),
                        delivery.getChannelName(),
                        delivery.getMessage(),
                        attempts,
                        durationMs);
            } else {
                auditLog = DeliveryAuditLog.failed(
                        delivery.getTaskId(),
                        delivery.getConversationId(),
                        delivery.getChannelName(),
                        delivery.getMessage(),
                        attempts,
                        errorMessage,
                        durationMs);
            }
            deliveryAuditLogRepository.save(auditLog);
        } catch (final Exception e) {
            log.warn(
                    "DeliveryRecovery — failed to save audit log for delivery {}: {}",
                    delivery.getId(),
                    e.getMessage());
        }
    }

    String getPodId() {
        return podId;
    }
}
