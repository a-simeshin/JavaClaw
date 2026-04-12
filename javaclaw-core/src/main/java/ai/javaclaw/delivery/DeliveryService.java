package ai.javaclaw.delivery;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.channels.Channel;
import ai.javaclaw.channels.ChannelContextService;
import ai.javaclaw.channels.ChannelRegistry;
import ai.javaclaw.channels.RoutingContext;
import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

/**
 * Delivers task results to users via the appropriate channel.
 *
 * <p>Flow:
 * <ol>
 *   <li>Check {@link NotifyPolicy} against result status &mdash; skip if policy says silent</li>
 *   <li>Resolve target channel via {@link ChannelContextService}</li>
 *   <li>Route: Web Chat &rarr; {@link NotificationTransport#broadcast} (SSE push),
 *       External &rarr; {@link DeliveryQueue} + {@link Channel#sendMessage}</li>
 *   <li>Persist notification in user's chat memory (visible on page reload)</li>
 *   <li>Log to {@code delivery_audit_log}</li>
 * </ol>
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final String WEB_CHAT_CHANNEL = "WebChatChannel";

    private final ChannelContextService channelContextService;
    private final ChannelRegistry channelRegistry;
    private final NotificationTransport notificationTransport;
    private final DeliveryQueueRepository deliveryQueueRepository;
    private final DeliveryAuditLogRepository deliveryAuditLogRepository;
    private final ChatMemory chatMemoryRepository;

    public DeliveryService(
            final ChannelContextService channelContextService,
            final ChannelRegistry channelRegistry,
            final NotificationTransport notificationTransport,
            final DeliveryQueueRepository deliveryQueueRepository,
            final DeliveryAuditLogRepository deliveryAuditLogRepository,
            final ChatMemory chatMemoryRepository) {
        this.channelContextService = channelContextService;
        this.channelRegistry = channelRegistry;
        this.notificationTransport = notificationTransport;
        this.deliveryQueueRepository = deliveryQueueRepository;
        this.deliveryAuditLogRepository = deliveryAuditLogRepository;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    /**
     * Delivers a task result to the user, respecting the task's {@link NotifyPolicy}.
     *
     * @param task    the task whose result should be delivered
     * @param message the formatted notification message
     */
    public void deliver(final Task task, final String message) {
        if (!shouldNotify(task.getNotifyPolicy(), task.getStatus())) {
            log.debug(
                    "Skipping delivery for task {} — policy {} does not match status {}",
                    task.getId(),
                    task.getNotifyPolicy(),
                    task.getStatus());
            return;
        }

        final String conversationId = task.getConversationId();
        if (conversationId == null) {
            log.warn("Cannot deliver task {} — no conversationId", task.getId());
            return;
        }

        final String channelName = resolveChannelName(conversationId, task);
        final long startTime = System.currentTimeMillis();

        try {
            if (WEB_CHAT_CHANNEL.equals(channelName)) {
                deliverViaWebChat(conversationId, message);
            } else {
                deliverViaExternalChannel(task.getId(), conversationId, channelName, message);
            }

            persistToChatMemory(conversationId, message);

            final long durationMs = System.currentTimeMillis() - startTime;
            logDeliveryAudit(task.getId(), conversationId, channelName, message, 1, durationMs, null);

            log.debug("Delivered task {} result to {} via {}", task.getId(), conversationId, channelName);
        } catch (final Exception e) {
            final long durationMs = System.currentTimeMillis() - startTime;
            logDeliveryAudit(task.getId(), conversationId, channelName, message, 1, durationMs, e.getMessage());
            log.error(
                    "Failed to deliver task {} result to {} via {}: {}",
                    task.getId(),
                    conversationId,
                    channelName,
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Determines whether a notification should be sent based on the policy and task status.
     */
    boolean shouldNotify(final NotifyPolicy policy, final Task.Status status) {
        if (policy == null || policy == NotifyPolicy.done_only) {
            return status == Task.Status.completed || status == Task.Status.failed;
        }
        return switch (policy) {
            case silent -> false;
            case on_error -> status == Task.Status.failed;
            case on_success -> status == Task.Status.completed;
            case state_changes -> true;
            default -> status == Task.Status.completed || status == Task.Status.failed;
        };
    }

    private String resolveChannelName(final String conversationId, final Task task) {
        return channelContextService
                .getContext(conversationId)
                .map(RoutingContext::channelName)
                .orElseGet(() -> {
                    final String source = task.getSourceChannelName();
                    return source != null ? source : WEB_CHAT_CHANNEL;
                });
    }

    private void deliverViaWebChat(final String conversationId, final String message) {
        notificationTransport.broadcast(conversationId, message);
    }

    private void deliverViaExternalChannel(
            final String taskId, final String conversationId, final String channelName, final String message) {
        final DeliveryQueue delivery = DeliveryQueue.create(taskId, conversationId, channelName, message);
        deliveryQueueRepository.save(delivery);

        final RoutingContext routingContext =
                channelContextService.getContext(conversationId).orElse(null);
        if (routingContext == null) {
            log.warn("No routing context for conversation {} — queued for retry", conversationId);
            return;
        }

        try {
            final Channel channel = channelRegistry.getChannel(channelName);
            channel.sendMessage(routingContext, message);
            deliveryQueueRepository.save(delivery.withDelivered());
        } catch (final Exception e) {
            deliveryQueueRepository.save(delivery.withFailed(e.getMessage()));
            throw e;
        }
    }

    private void persistToChatMemory(final String conversationId, final String message) {
        try {
            chatMemoryRepository.appendAll(conversationId, List.of(new AssistantMessage(message)));
        } catch (final Exception e) {
            log.warn("Failed to persist notification to chat memory for {}: {}", conversationId, e.getMessage());
        }
    }

    private void logDeliveryAudit(
            final String taskId,
            final String conversationId,
            final String channelName,
            final String message,
            final int attempts,
            final Long durationMs,
            final String errorMessage) {
        try {
            final DeliveryAuditLog auditLog;
            if (errorMessage == null) {
                auditLog =
                        DeliveryAuditLog.delivered(taskId, conversationId, channelName, message, attempts, durationMs);
            } else {
                auditLog = DeliveryAuditLog.failed(
                        taskId, conversationId, channelName, message, attempts, errorMessage, durationMs);
            }
            deliveryAuditLogRepository.save(auditLog);
        } catch (final Exception e) {
            log.warn("Failed to save delivery audit log for task {}: {}", taskId, e.getMessage());
        }
    }
}
