package ai.javaclaw.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.audit.TaskAuditService;
import ai.javaclaw.agent.event.AgentEvent;
import ai.javaclaw.agent.event.EventBus;
import ai.javaclaw.agent.event.EventKind;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private EventBus eventBus;

    @Mock
    private TaskAuditService taskAuditService;

    @Captor
    private ArgumentCaptor<AgentEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<ApprovalRequest> approvalCaptor;

    @Captor
    private ArgumentCaptor<Task> taskCaptor;

    private ApprovalService approvalService;

    private static final String TASK_ID = "task-123";
    private static final String CONV_ID = "conv-456";
    private static final String QUESTION = "Found ticket for 9500. Buy?";

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(approvalRequestRepository, taskRepository, eventBus, taskAuditService);
    }

    // --- T14: requestApproval saves to DB, emits event, blocks on Future ---

    @Test
    void requestApprovalSavesToDbAndEmitsEvent() throws Exception {
        final Task task = createTask(TASK_ID, CONV_ID, Task.Status.in_progress);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> {
            ApprovalRequest req = inv.getArgument(0);
            return new ApprovalRequest(
                    "approval-1",
                    req.getTaskId(),
                    req.getConversationId(),
                    req.getQuestion(),
                    req.getResponse(),
                    req.getStatus(),
                    req.getTimeoutAt(),
                    req.getCreatedAt(),
                    req.getRespondedAt());
        });
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        // Run requestApproval in a separate thread (it blocks)
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> resultFuture =
                    executor.submit(() -> approvalService.requestApproval(TASK_ID, QUESTION, Duration.ofSeconds(5)));

            // Wait for the future to be registered
            Thread.sleep(200);

            // Verify approval saved to DB
            verify(approvalRequestRepository).save(approvalCaptor.capture());
            ApprovalRequest saved = approvalCaptor.getValue();
            assertThat(saved.getTaskId()).isEqualTo(TASK_ID);
            assertThat(saved.getConversationId()).isEqualTo(CONV_ID);
            assertThat(saved.getQuestion()).isEqualTo(QUESTION);
            assertThat(saved.getStatus()).isEqualTo(ApprovalRequest.Status.pending);

            // Verify task status set to awaiting_human_input
            verify(taskRepository).save(taskCaptor.capture());
            assertThat(taskCaptor.getValue().getStatus()).isEqualTo(Task.Status.awaiting_human_input);

            // Verify event emitted
            verify(eventBus).emit(eventCaptor.capture());
            AgentEvent event = eventCaptor.getValue();
            assertThat(event.kind()).isEqualTo(EventKind.APPROVAL_REQUESTED);

            // Verify audit logged
            verify(taskAuditService).logApprovalRequested(eq(TASK_ID), eq(null), eq(QUESTION));

            // Complete the future to unblock the thread
            approvalService.getPendingFutures().get(TASK_ID).complete("Yes");
            assertThat(resultFuture.get()).isEqualTo("Yes");
        } finally {
            executor.shutdownNow();
        }
    }

    // --- T15: submitApproval completes Future, task resumes ---

    @Test
    void submitApprovalCompletesFutureAndResumesTask() {
        final ApprovalRequest pending =
                ApprovalRequest.create(TASK_ID, CONV_ID, QUESTION, Instant.now().plusSeconds(60));
        when(approvalRequestRepository.findByConversationIdAndStatus(CONV_ID, ApprovalRequest.Status.pending))
                .thenReturn(List.of(pending));
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        final Task task = createTask(TASK_ID, CONV_ID, Task.Status.awaiting_human_input);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        // Pre-register a future (simulates requestApproval blocking)
        CompletableFuture<String> future = new CompletableFuture<>();
        approvalService.getPendingFutures().put(TASK_ID, future);

        boolean result = approvalService.submitApproval(CONV_ID, "Да");

        assertThat(result).isTrue();
        assertThat(future).isCompletedWithValue("Да");

        // Verify approval updated in DB as approved
        verify(approvalRequestRepository).save(approvalCaptor.capture());
        assertThat(approvalCaptor.getValue().getStatus()).isEqualTo(ApprovalRequest.Status.approved);

        // Verify task restored to in_progress
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(Task.Status.in_progress);

        // Verify event + audit
        verify(eventBus).emit(eventCaptor.capture());
        assertThat(eventCaptor.getValue().kind()).isEqualTo(EventKind.APPROVAL_RECEIVED);
        verify(taskAuditService).logApprovalReceived(eq(TASK_ID), eq(null), eq("Да"));
    }

    @Test
    void submitApprovalDeniedWhenResponseIsNo() {
        final ApprovalRequest pending =
                ApprovalRequest.create(TASK_ID, CONV_ID, QUESTION, Instant.now().plusSeconds(60));
        when(approvalRequestRepository.findByConversationIdAndStatus(CONV_ID, ApprovalRequest.Status.pending))
                .thenReturn(List.of(pending));
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        final Task task = createTask(TASK_ID, CONV_ID, Task.Status.awaiting_human_input);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        approvalService.submitApproval(CONV_ID, "Нет, не покупай");

        verify(approvalRequestRepository).save(approvalCaptor.capture());
        assertThat(approvalCaptor.getValue().getStatus()).isEqualTo(ApprovalRequest.Status.denied);
    }

    // --- T16: approval timeout → auto-deny, task continues with "timed out" ---

    @Test
    void requestApprovalTimesOutAndAutodenies() {
        final Task task = createTask(TASK_ID, CONV_ID, Task.Status.in_progress);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        // Use very short timeout so it expires quickly
        String result = approvalService.requestApproval(TASK_ID, QUESTION, Duration.ofMillis(100));

        assertThat(result).isNull();

        // Verify timeout handling: approval marked as timed out
        // save called 3 times: 1) initial approval, 2) task→awaiting, 3) approval→timeout, 4) task→in_progress
        verify(taskAuditService).logTimeout(eq(TASK_ID), eq(null));
    }

    // --- T17: no pending approval → normal chat flow ---

    @Test
    void submitApprovalReturnsFalseWhenNoPending() {
        when(approvalRequestRepository.findByConversationIdAndStatus(CONV_ID, ApprovalRequest.Status.pending))
                .thenReturn(List.of());

        boolean result = approvalService.submitApproval(CONV_ID, "Some message");

        assertThat(result).isFalse();
        verify(approvalRequestRepository, never()).save(any());
        verify(eventBus, never()).emit(any());
    }

    @Test
    void hasPendingApprovalReturnsTrueWhenPending() {
        final ApprovalRequest pending =
                ApprovalRequest.create(TASK_ID, CONV_ID, QUESTION, Instant.now().plusSeconds(60));
        when(approvalRequestRepository.findByConversationIdAndStatus(CONV_ID, ApprovalRequest.Status.pending))
                .thenReturn(List.of(pending));

        assertThat(approvalService.hasPendingApproval(CONV_ID)).isTrue();
    }

    @Test
    void hasPendingApprovalReturnsFalseWhenNone() {
        when(approvalRequestRepository.findByConversationIdAndStatus(CONV_ID, ApprovalRequest.Status.pending))
                .thenReturn(List.of());

        assertThat(approvalService.hasPendingApproval(CONV_ID)).isFalse();
    }

    @Test
    void getPendingApprovalsReturnsListForConversation() {
        final ApprovalRequest r1 =
                ApprovalRequest.create(TASK_ID, CONV_ID, "Q1", Instant.now().plusSeconds(60));
        final ApprovalRequest r2 =
                ApprovalRequest.create("task-2", CONV_ID, "Q2", Instant.now().plusSeconds(60));
        when(approvalRequestRepository.findByConversationIdAndStatus(CONV_ID, ApprovalRequest.Status.pending))
                .thenReturn(List.of(r1, r2));

        List<ApprovalRequest> result = approvalService.getPendingApprovals(CONV_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    void submitApprovalRecognizesVariousApprovalPhrases() {
        // Test that "yes", "ok", "ок", "одобряю", "approve" are all treated as approval
        for (String phrase : List.of("Да", "yes", "ок", "ok", "одобряю", "approve", "да, покупай")) {
            org.mockito.Mockito.clearInvocations(approvalRequestRepository, taskRepository, eventBus, taskAuditService);

            final ApprovalRequest pending = ApprovalRequest.create(
                    TASK_ID, CONV_ID, QUESTION, Instant.now().plusSeconds(60));
            when(approvalRequestRepository.findByConversationIdAndStatus(CONV_ID, ApprovalRequest.Status.pending))
                    .thenReturn(List.of(pending));
            when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
            final Task task = createTask(TASK_ID, CONV_ID, Task.Status.awaiting_human_input);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
            when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            approvalService.submitApproval(CONV_ID, phrase);

            var captor = ArgumentCaptor.forClass(ApprovalRequest.class);
            verify(approvalRequestRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus())
                    .as("Phrase '%s' should be approved", phrase)
                    .isEqualTo(ApprovalRequest.Status.approved);
        }
    }

    private Task createTask(String taskId, String conversationId, Task.Status status) {
        return new Task(
                taskId, "Test Task", Instant.now(), Instant.now(), status, "Description", null, null, conversationId);
    }
}
