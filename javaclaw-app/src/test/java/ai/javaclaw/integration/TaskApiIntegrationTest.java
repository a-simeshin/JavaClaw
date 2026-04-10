package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskRepository;
import ai.javaclaw.tasks.TaskRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for Task REST API endpoints ({@code /api/tasks}).
 *
 * <p>Verifies task CRUD, filtering, cancel, parent-child hierarchy, and approval
 * pending endpoints against a real PostgreSQL database via Testcontainers.
 * Covers integration test scenarios T58 (task lifecycle via API).
 */
class TaskApiIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    TaskRepository taskRepository;

    @BeforeEach
    void cleanTasks() {
        taskRepository.deleteAll();
    }

    private Task saveTask(String name, String description, Task.Status status, String userId, String conversationId) {
        Task task = new Task(
                null,
                name,
                Instant.now(),
                Instant.now(),
                status,
                description,
                null,
                null,
                conversationId,
                null,
                NotifyPolicy.done_only,
                TaskRuntime.async,
                300,
                false,
                userId,
                null,
                null,
                null);
        return taskRepository.save(task);
    }

    private Task saveChildTask(String name, Task.Status status, String parentTaskId, String userId) {
        Task task = new Task(
                null,
                name,
                Instant.now(),
                Instant.now(),
                status,
                "child desc",
                null,
                null,
                "conv-1",
                parentTaskId,
                NotifyPolicy.done_only,
                TaskRuntime.async,
                300,
                false,
                userId,
                null,
                null,
                null);
        return taskRepository.save(task);
    }

    @Nested
    class ListTasks {

        @Test
        void emptyDatabase_returnsEmptyArray() throws Exception {
            mockMvc.perform(get("/api/tasks").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void returnsAllTasks() throws Exception {
            saveTask("Task A", "desc A", Task.Status.completed, "user-1", "conv-1");
            saveTask("Task B", "desc B", Task.Status.in_progress, "user-2", "conv-2");

            mockMvc.perform(get("/api/tasks").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void filterByStatus() throws Exception {
            saveTask("Done", "d", Task.Status.completed, "user-1", "conv-1");
            saveTask("Running", "d", Task.Status.in_progress, "user-1", "conv-2");

            mockMvc.perform(get("/api/tasks").param("status", "completed").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Done"));
        }

        @Test
        void filterByUserId() throws Exception {
            saveTask("User1 Task", "d", Task.Status.completed, "user-1", "conv-1");
            saveTask("User2 Task", "d", Task.Status.completed, "user-2", "conv-2");

            mockMvc.perform(get("/api/tasks").param("userId", "user-1").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("User1 Task"));
        }

        @Test
        void filterByUserIdAndStatus() throws Exception {
            saveTask("A", "d", Task.Status.completed, "user-1", "conv-1");
            saveTask("B", "d", Task.Status.failed, "user-1", "conv-2");
            saveTask("C", "d", Task.Status.completed, "user-2", "conv-3");

            mockMvc.perform(get("/api/tasks")
                            .param("userId", "user-1")
                            .param("status", "completed")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("A"));
        }

        @Test
        void invalidStatus_returns400() throws Exception {
            mockMvc.perform(get("/api/tasks").param("status", "INVALID_STATUS").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class GetTask {

        @Test
        void existingTask_returnsDetails() throws Exception {
            Task saved = saveTask("My Task", "my desc", Task.Status.in_progress, "user-1", "conv-1");

            MvcResult result = mockMvc.perform(
                            get("/api/tasks/{id}", saved.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("My Task"))
                    .andExpect(jsonPath("$.description").value("my desc"))
                    .andExpect(jsonPath("$.status").value("in_progress"))
                    .andExpect(jsonPath("$.userId").value("user-1"))
                    .andExpect(jsonPath("$.conversationId").value("conv-1"))
                    .andExpect(jsonPath("$.notifyPolicy").value("done_only"))
                    .andExpect(jsonPath("$.runtimeType").value("async"))
                    .andExpect(jsonPath("$.timeoutSeconds").value(300))
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(body.path("id").asText()).isEqualTo(saved.getId());
            assertThat(body.path("createdAt").asText()).isNotBlank();
        }

        @Test
        void nonExistentTask_returns404() throws Exception {
            mockMvc.perform(get("/api/tasks/{id}", "non-existent-id").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class CancelTask {

        @Test
        void cancelInProgressTask_setsStatusCancelled() throws Exception {
            Task saved = saveTask("Running Task", "desc", Task.Status.in_progress, "user-1", "conv-1");

            mockMvc.perform(post("/api/tasks/{id}/cancel", saved.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("cancelled"));

            // Verify in DB
            Task updated = taskRepository.findById(saved.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(Task.Status.cancelled);
            assertThat(updated.getCancelledAt()).isNotNull();
        }

        @Test
        void cancelAlreadyCompletedTask_remainsCompleted() throws Exception {
            Task saved = saveTask("Done Task", "desc", Task.Status.completed, "user-1", "conv-1");

            mockMvc.perform(post("/api/tasks/{id}/cancel", saved.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("completed"));
        }

        @Test
        void cancelNonExistentTask_returns400() throws Exception {
            mockMvc.perform(post("/api/tasks/{id}/cancel", "non-existent").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class DeleteTask {

        @Test
        void deleteExistingTask_returns204() throws Exception {
            Task saved = saveTask("To Delete", "desc", Task.Status.completed, "user-1", "conv-1");

            mockMvc.perform(delete("/api/tasks/{id}", saved.getId())).andExpect(status().isNoContent());

            assertThat(taskRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        void deleteNonExistentTask_returns204() throws Exception {
            // Spring Data JDBC deleteById is idempotent
            mockMvc.perform(delete("/api/tasks/{id}", "non-existent")).andExpect(status().isNoContent());
        }
    }

    @Nested
    class ParentChildTasks {

        @Test
        void childrenEndpoint_returnsChildTasks() throws Exception {
            Task parent = saveTask("Parent", "parent desc", Task.Status.in_progress, "user-1", "conv-1");
            Task child1 = saveChildTask("Child 1", Task.Status.completed, parent.getId(), "user-1");
            Task child2 = saveChildTask("Child 2", Task.Status.in_progress, parent.getId(), "user-1");

            mockMvc.perform(get("/api/tasks/{id}/children", parent.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void childrenEndpoint_noChildren_returnsEmptyArray() throws Exception {
            Task parent = saveTask("Lonely Parent", "desc", Task.Status.completed, "user-1", "conv-1");

            mockMvc.perform(get("/api/tasks/{id}/children", parent.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void childTask_hasParentTaskIdInResponse() throws Exception {
            Task parent = saveTask("Parent", "desc", Task.Status.completed, "user-1", "conv-1");
            Task child = saveChildTask("Child", Task.Status.completed, parent.getId(), "user-1");

            mockMvc.perform(get("/api/tasks/{id}", child.getId()).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentTaskId").value(parent.getId()));
        }
    }

    @Nested
    class ApprovalPending {

        @Test
        void pendingApprovals_emptyWhenNone() throws Exception {
            mockMvc.perform(get("/api/chat/approval/pending")
                            .param("conversationId", "conv-1")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    class SseNotifications {

        @Test
        void sseEndpoint_returns200WithStreamContentType() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/chat/notifications/{conversationId}", "conv-1"))
                    .andExpect(status().isOk())
                    .andReturn();

            String contentType = result.getResponse().getContentType();
            assertThat(contentType).isNotNull();
            // SSE or event-stream content type
            assertThat(contentType).containsIgnoringCase("stream");
        }
    }
}
