package ai.javaclaw.tasks;

import ai.javaclaw.agent.audit.TaskAuditService;
import ai.javaclaw.agent.event.AgentEvent;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.agent.event.EventKind;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Human-in-the-loop approval service. When a task needs user permission to proceed,
 * it calls {@link #requestApproval} which saves an {@link ApprovalRequest} to DB,
 * delivers the question to the user, and blocks until the user responds or the request times out.
 *
 * <p>When the user replies in chat and a pending approval exists for that conversation,
 * {@link #submitApproval} is called to complete the blocked future and resume the task.
 */
@Service
public class ApprovalService {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRequestRepository approvalRequestRepository;
    private final TaskRepository taskRepository;
    private final EventBus eventBus;
    private final TaskAuditService taskAuditService;

    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingFutures = new ConcurrentHashMap<>();

    public ApprovalService(
            ApprovalRequestRepository approvalRequestRepository,
            TaskRepository taskRepository,
            EventBus eventBus,
            TaskAuditService taskAuditService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.taskRepository = taskRepository;
        this.eventBus = eventBus;
        this.taskAuditService = taskAuditService;
    }

    /**
     * Request approval from the user. Saves the request to DB, sets task status to awaiting_input,
     * emits APPROVAL_REQUESTED event, and blocks the calling thread until the user responds
     * or the timeout expires.
     *
     * @param taskId the task requesting approval
     * @param question the question to ask the user
     * @param timeout how long to wait for a response
     * @return the user's response text, or {@code null} if timed out
     */
    public String requestApproval(String taskId, String question, Duration timeout) {
        final Task task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

        final String conversationId = task.getConversationId();
        final Instant timeoutAt = Instant.now().plus(timeout);

        // Save approval request to DB
        final ApprovalRequest request =
                approvalRequestRepository.save(ApprovalRequest.create(taskId, conversationId, question, timeoutAt));

        // Update task status to awaiting_input
        taskRepository.save(task.withStatus(Task.Status.awaiting_human_input));

        // Emit event + audit
        final AgentEvent.EventMeta meta = AgentEvent.EventMeta.ofTask(null, taskId);
        eventBus.emit(AgentEvent.of(
                EventKind.APPROVAL_REQUESTED,
                meta,
                Map.of("approvalId", nullSafe(request.getId()), "question", question)));
        taskAuditService.logApprovalRequested(taskId, null, question);

        LOG.info("Approval requested for task '{}': {}", taskId, question);

        // Block on CompletableFuture until user responds or timeout
        final CompletableFuture<String> future = new CompletableFuture<>();
        pendingFutures.put(taskId, future);

        try {
            final String response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return response;
        } catch (TimeoutException e) {
            // Timeout — auto-deny
            handleTimeout(request, taskId);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleTimeout(request, taskId);
            return null;
        } catch (Exception e) {
            LOG.error("Error waiting for approval on task '{}'", taskId, e);
            handleTimeout(request, taskId);
            return null;
        } finally {
            pendingFutures.remove(taskId);
        }
    }

    /**
     * Submit a user's response to a pending approval. Called when the user replies in chat
     * and a pending approval exists for that conversation.
     *
     * @param conversationId the conversation where the user responded
     * @param userResponse the user's response text
     * @return true if a pending approval was found and completed, false otherwise
     */
    public boolean submitApproval(String conversationId, String userResponse) {
        final List<ApprovalRequest> pending =
                approvalRequestRepository.findByConversationIdAndStatus(conversationId, ApprovalRequest.Status.pending);

        if (pending.isEmpty()) {
            return false;
        }

        // Take the oldest pending approval for this conversation
        final ApprovalRequest request = pending.getFirst();
        final String taskId = request.getTaskId();

        // Update approval in DB
        final boolean isApproved = isApprovalResponse(userResponse);
        final ApprovalRequest updated =
                isApproved ? request.withApproved(userResponse) : request.withDenied(userResponse);
        approvalRequestRepository.save(updated);

        // Restore task to in_progress
        taskRepository
                .findById(taskId)
                .ifPresent(task -> taskRepository.save(task.withStatus(Task.Status.in_progress)));

        // Emit event + audit
        final AgentEvent.EventMeta meta = AgentEvent.EventMeta.ofTask(null, taskId);
        eventBus.emit(AgentEvent.of(
                EventKind.APPROVAL_RECEIVED,
                meta,
                Map.of("approved", String.valueOf(isApproved), "response", nullSafe(userResponse))));
        taskAuditService.logApprovalReceived(taskId, null, userResponse);

        LOG.info("Approval {} for task '{}': {}", isApproved ? "approved" : "denied", taskId, userResponse);

        // Complete the waiting future
        final CompletableFuture<String> future = pendingFutures.remove(taskId);
        if (future != null) {
            future.complete(userResponse);
        }

        return true;
    }

    /**
     * Check if there is a pending approval for the given conversation.
     * Used by ChatService to intercept user messages before normal chat flow.
     */
    public boolean hasPendingApproval(String conversationId) {
        final List<ApprovalRequest> pending =
                approvalRequestRepository.findByConversationIdAndStatus(conversationId, ApprovalRequest.Status.pending);
        return !pending.isEmpty();
    }

    /**
     * Get pending approval requests for a conversation.
     */
    public List<ApprovalRequest> getPendingApprovals(String conversationId) {
        return approvalRequestRepository.findByConversationIdAndStatus(conversationId, ApprovalRequest.Status.pending);
    }

    // Visible for testing
    ConcurrentHashMap<String, CompletableFuture<String>> getPendingFutures() {
        return pendingFutures;
    }

    private void handleTimeout(ApprovalRequest request, String taskId) {
        try {
            approvalRequestRepository.save(request.withTimedOut());

            taskRepository
                    .findById(taskId)
                    .ifPresent(task -> taskRepository.save(task.withStatus(Task.Status.in_progress)));

            final AgentEvent.EventMeta meta = AgentEvent.EventMeta.ofTask(null, taskId);
            eventBus.emit(
                    AgentEvent.of(EventKind.APPROVAL_RECEIVED, meta, Map.of("approved", "false", "timeout", "true")));
            taskAuditService.logTimeout(taskId, null);

            LOG.info("Approval timed out for task '{}'", taskId);
        } catch (Exception e) {
            LOG.error("Error handling approval timeout for task '{}'", taskId, e);
        }
    }

    private static boolean isApprovalResponse(String response) {
        if (response == null) {
            return false;
        }
        final String lower = response.toLowerCase().trim();
        return lower.equals("да")
                || lower.equals("yes")
                || lower.equals("ок")
                || lower.equals("ok")
                || lower.equals("одобряю")
                || lower.equals("approve")
                || lower.startsWith("да,")
                || lower.startsWith("yes,");
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
