package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.tasks.ApprovalRequest;
import ai.javaclaw.tasks.ApprovalRequestRepository;
import ai.javaclaw.tasks.ApprovalService;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskHandler;
import ai.javaclaw.tasks.TaskHandler.TaskResult;
import ai.javaclaw.tasks.TaskRepository;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Integration tests for T50: human-in-the-loop approval flow with real PostgreSQL.
 *
 * <p>Covers the full blocking cycle: Agent calls {@link ApprovalService#requestApproval} which
 * suspends the task thread, user responds via {@link ApprovalService#submitApproval}, and the
 * task resumes with the user's answer.
 *
 * <ul>
 *   <li>T50a: approval granted → task resumes and completes</li>
 *   <li>T50b: approval denied → task continues with denied context</li>
 *   <li>T50c: custom text reply → task receives the custom response</li>
 * </ul>
 */
class ApprovalFlowIntegrationTest extends IntegrationTestBase {

    @TestBean
    Agent agent;

    static Agent agent() {
        return Mockito.mock(Agent.class);
    }

    @Autowired
    ApprovalService approvalService;

    @Autowired
    ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskHandler taskHandler;

    @BeforeEach
    void cleanAll() {
        approvalRequestRepository.deleteAll();
        taskRepository.deleteAll();
    }

    private Task saveTask(String name, String description, String conversationId) {
        return taskRepository.save(Task.newTask(name, description)
                .withConversationId(conversationId)
                .withUserId("user-t50"));
    }

    /**
     * Configures the mock agent to call {@link ApprovalService#requestApproval} once,
     * then (after receiving user response) returns the given finalResult.
     */
    private void setupAgentWithApproval(String taskId, String question, TaskResult finalResult) {
        doAnswer(invocation -> {
                    String userResponse = approvalService.requestApproval(taskId, question, Duration.ofSeconds(30));
                    // If user approved — return finalResult; otherwise surface the response in feedback
                    if (finalResult != null) {
                        return finalResult;
                    }
                    return new TaskResult(Task.Status.completed, "User replied: " + userResponse);
                })
                .when(agent)
                .prompt(anyString(), anyString(), eq(TaskResult.class));
    }

    // ─────────────────────────────────────────────────────────────────
    // T50a: approval granted → task resumes and completes
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T50a — approval granted: task resumes and completes successfully")
    void t50a_approvalGranted_taskResumesAndCompletes() throws Exception {
        Task task = saveTask("book-flight", "Book cheapest flight to SPb", "conv-t50a");
        String conversationId = task.getConversationId();
        String taskId = task.getId();

        // Agent will block on requestApproval, then return completed after user approves
        setupAgentWithApproval(
                taskId, "Found ticket for 9500. Buy?", new TaskResult(Task.Status.completed, "Flight booked!"));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> taskHandler.executeTask(taskId));

        // Wait until approval request appears in DB
        await().atMost(5, TimeUnit.SECONDS).until(() -> approvalRequestRepository
                .findByTaskIdAndStatus(taskId, ApprovalRequest.Status.pending)
                .isPresent());

        // Task must be in awaiting_human_input state
        Task waiting = taskRepository.findById(taskId).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(Task.Status.awaiting_human_input);

        // Submit approval — "yes" triggers isApprovalResponse == true
        approvalService.submitApproval(conversationId, "yes");

        // Wait for task thread to finish
        future.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        Task completed = taskRepository.findById(taskId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(Task.Status.completed);
        assertThat(completed.getFeedback()).isEqualTo("Flight booked!");

        // ApprovalRequest should no longer be pending
        assertThat(approvalRequestRepository.findByTaskIdAndStatus(taskId, ApprovalRequest.Status.pending))
                .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────
    // T50b: approval denied → task continues with denied context
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T50b — approval denied: task continues with denied context and completes")
    void t50b_approvalDenied_taskContinuesWithDeniedContext() throws Exception {
        Task task = saveTask("buy-ticket", "Buy train ticket to Moscow", "conv-t50b");
        String conversationId = task.getConversationId();
        String taskId = task.getId();

        // Agent returns the response it received from the user
        doAnswer(invocation -> {
                    String userResponse =
                            approvalService.requestApproval(taskId, "Buy ticket for 4200?", Duration.ofSeconds(30));
                    return new TaskResult(Task.Status.completed, "User said: " + userResponse);
                })
                .when(agent)
                .prompt(anyString(), anyString(), eq(TaskResult.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> taskHandler.executeTask(taskId));

        await().atMost(5, TimeUnit.SECONDS).until(() -> approvalRequestRepository
                .findByTaskIdAndStatus(taskId, ApprovalRequest.Status.pending)
                .isPresent());

        // Submit denial
        approvalService.submitApproval(conversationId, "denied");

        future.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        Task done = taskRepository.findById(taskId).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(Task.Status.completed);
        assertThat(done.getFeedback()).contains("denied");

        // Approval should be stored as denied in DB
        ApprovalRequest approvalRequest = approvalRequestRepository
                .findByTaskIdAndStatus(taskId, ApprovalRequest.Status.denied)
                .orElseThrow(() -> new AssertionError("Expected denied ApprovalRequest in DB"));
        assertThat(approvalRequest.getStatus()).isEqualTo(ApprovalRequest.Status.denied);
    }

    // ─────────────────────────────────────────────────────────────────
    // T50c: custom text reply → task receives the custom response
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T50c — custom text reply: task receives and surfaces the exact user text")
    void t50c_approvalWithTextReply_taskReceivesCustomResponse() throws Exception {
        Task task = saveTask("book-hotel", "Book hotel in Sochi", "conv-t50c");
        String conversationId = task.getConversationId();
        String taskId = task.getId();

        final String[] capturedResponse = new String[1];

        doAnswer(invocation -> {
                    String userResponse = approvalService.requestApproval(
                            taskId, "Economy or business class?", Duration.ofSeconds(30));
                    capturedResponse[0] = userResponse;
                    return new TaskResult(Task.Status.completed, "Booking with preference: " + userResponse);
                })
                .when(agent)
                .prompt(anyString(), anyString(), eq(TaskResult.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> taskHandler.executeTask(taskId));

        await().atMost(5, TimeUnit.SECONDS).until(() -> approvalRequestRepository
                .findByTaskIdAndStatus(taskId, ApprovalRequest.Status.pending)
                .isPresent());

        // Submit custom text (not a recognized approve/deny phrase)
        approvalService.submitApproval(conversationId, "только эконом");

        future.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Agent received the exact custom text
        assertThat(capturedResponse[0]).isEqualTo("только эконом");

        Task done = taskRepository.findById(taskId).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(Task.Status.completed);
        assertThat(done.getFeedback()).contains("только эконом");
    }
}
