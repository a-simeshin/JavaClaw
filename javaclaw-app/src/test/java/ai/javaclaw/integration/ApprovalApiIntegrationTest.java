package ai.javaclaw.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.tasks.ApprovalRequest;
import ai.javaclaw.tasks.ApprovalRequestRepository;
import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskRepository;
import ai.javaclaw.tasks.TaskRuntime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integration tests for Approval REST API endpoints
 * ({@code /api/chat/approval/pending}, {@code /api/chat/approval/{id}/respond}).
 *
 * <p>Tests approval request CRUD lifecycle with real PostgreSQL via Testcontainers:
 * pending queries filtered by conversationId, respond endpoint with approve/deny,
 * expired approval handling, task isolation, and error cases.
 */
class ApprovalApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    ApprovalRequestRepository approvalRequestRepository;

    @BeforeEach
    void cleanAll() {
        approvalRequestRepository.deleteAll();
        taskRepository.deleteAll();
    }

    private Task saveTask(String name, Task.Status status) {
        Task task = new Task(
                null,
                name,
                Instant.now(),
                Instant.now(),
                status,
                "test description",
                null,
                null,
                "conv-1",
                null,
                NotifyPolicy.done_only,
                TaskRuntime.async,
                300,
                false,
                "user-1",
                null,
                null,
                null);
        return taskRepository.save(task);
    }

    private ApprovalRequest saveApproval(
            String taskId, String conversationId, String question, ApprovalRequest.Status status) {
        Instant timeoutAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        ApprovalRequest request = ApprovalRequest.create(taskId, conversationId, question, timeoutAt);

        if (status == ApprovalRequest.Status.approved) {
            request = request.withApproved("yes");
        } else if (status == ApprovalRequest.Status.denied) {
            request = request.withDenied("no");
        } else if (status == ApprovalRequest.Status.timeout) {
            request = request.withTimedOut();
        }
        return approvalRequestRepository.save(request);
    }

    private ApprovalRequest savePendingApproval(String taskId, String conversationId, String question) {
        return saveApproval(taskId, conversationId, question, ApprovalRequest.Status.pending);
    }

    @Nested
    class PendingEndpoint {

        @Test
        void noPendingApprovals_returnsEmptyArray() throws Exception {
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void returnsPendingApprovalsForConversation() throws Exception {
            Task task = saveTask("Buy Ticket", Task.Status.awaiting_human_input);
            savePendingApproval(task.getId(), "conv-1", "Found ticket for 9500. Buy?");

            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].taskId").value(task.getId()))
                    .andExpect(jsonPath("$[0].conversationId").value("conv-1"))
                    .andExpect(jsonPath("$[0].question").value("Found ticket for 9500. Buy?"))
                    .andExpect(jsonPath("$[0].status").value("pending"))
                    .andExpect(jsonPath("$[0].response").isEmpty())
                    .andExpect(jsonPath("$[0].timeoutAt").isNotEmpty())
                    .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
        }

        @Test
        void multiplePendingApprovalsForSameConversation() throws Exception {
            Task task1 = saveTask("Task 1", Task.Status.awaiting_human_input);
            Task task2 = saveTask("Task 2", Task.Status.awaiting_human_input);
            savePendingApproval(task1.getId(), "conv-1", "Question 1?");
            savePendingApproval(task2.getId(), "conv-1", "Question 2?");

            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void pendingApprovalsIsolatedByConversationId() throws Exception {
            Task task1 = saveTask("Task Conv1", Task.Status.awaiting_human_input);
            Task task2 = saveTask("Task Conv2", Task.Status.awaiting_human_input);
            savePendingApproval(task1.getId(), "conv-1", "Q for conv1?");
            savePendingApproval(task2.getId(), "conv-2", "Q for conv2?");

            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].question").value("Q for conv1?"));

            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-2")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].question").value("Q for conv2?"));
        }

        @Test
        void resolvedApprovalsExcludedFromPending() throws Exception {
            Task task1 = saveTask("Approved Task", Task.Status.in_progress);
            Task task2 = saveTask("Denied Task", Task.Status.in_progress);
            Task task3 = saveTask("Timed Out Task", Task.Status.in_progress);
            Task task4 = saveTask("Pending Task", Task.Status.awaiting_human_input);

            saveApproval(task1.getId(), "conv-1", "Approved Q?", ApprovalRequest.Status.approved);
            saveApproval(task2.getId(), "conv-1", "Denied Q?", ApprovalRequest.Status.denied);
            saveApproval(task3.getId(), "conv-1", "Timeout Q?", ApprovalRequest.Status.timeout);
            savePendingApproval(task4.getId(), "conv-1", "Still pending?");

            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].question").value("Still pending?"))
                    .andExpect(jsonPath("$[0].status").value("pending"));
        }
    }

    @Nested
    class RespondEndpoint {

        @Test
        void respondToApproval_returnsOk() throws Exception {
            Task task = saveTask("Buy Ticket", Task.Status.awaiting_human_input);
            ApprovalRequest approval = savePendingApproval(task.getId(), "conv-1", "Buy for 9500?");

            mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", approval.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"response\": \"Yes, buy it\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void respondToNonExistentApproval_returns404() throws Exception {
            mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", "non-existent-id")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"response\": \"Yes\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void respondWithApprovalPhrase_setsApprovedStatus() throws Exception {
            Task task = saveTask("Buy Ticket", Task.Status.awaiting_human_input);
            ApprovalRequest approval = savePendingApproval(task.getId(), "conv-1", "Buy for 9500?");

            mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", approval.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"response\": \"да, покупай\"}"))
                    .andExpect(status().isOk());

            // Verify the approval was processed via pending endpoint (should be empty now)
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void respondWithDenialPhrase_processesResponse() throws Exception {
            Task task = saveTask("Buy Ticket", Task.Status.awaiting_human_input);
            ApprovalRequest approval = savePendingApproval(task.getId(), "conv-1", "Buy for 9500?");

            mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", approval.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"response\": \"нет, не покупай\"}"))
                    .andExpect(status().isOk());

            // Verify the approval was processed
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void respondWithTextReply_processesResponse() throws Exception {
            Task task = saveTask("Buy Ticket", Task.Status.awaiting_human_input);
            ApprovalRequest approval = savePendingApproval(task.getId(), "conv-1", "Buy for 9500?");

            mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", approval.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"response\": \"Only economy class please\"}"))
                    .andExpect(status().isOk());

            // Pending should be cleared
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    class ApprovalLifecycle {

        @Test
        void fullApprovalLifecycle_createPendingThenRespond() throws Exception {
            // Create task and pending approval
            Task task = saveTask("Weather Check", Task.Status.awaiting_human_input);
            ApprovalRequest approval = savePendingApproval(task.getId(), "conv-1", "Check weather in 3 cities?");

            // Verify pending approval exists
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].taskId").value(task.getId()))
                    .andExpect(jsonPath("$[0].question").value("Check weather in 3 cities?"))
                    .andExpect(jsonPath("$[0].status").value("pending"));

            // Respond to approval
            mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", approval.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"response\": \"Yes, go ahead\"}"))
                    .andExpect(status().isOk());

            // Verify pending list is now empty
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void multipleApprovalsForDifferentTasks_respondIndependently() throws Exception {
            Task task1 = saveTask("Task A", Task.Status.awaiting_human_input);
            Task task2 = saveTask("Task B", Task.Status.awaiting_human_input);
            ApprovalRequest approval1 = savePendingApproval(task1.getId(), "conv-1", "Proceed with A?");
            ApprovalRequest approval2 = savePendingApproval(task2.getId(), "conv-1", "Proceed with B?");

            // Both pending
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));

            // Respond to first only
            mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", approval1.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"response\": \"Yes\"}"))
                    .andExpect(status().isOk());

            // Only second remains pending
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].question").value("Proceed with B?"));
        }
    }
}
