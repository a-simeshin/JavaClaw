package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("A2A Protocol Integration Tests")
class A2aIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Agent Card Discovery")
    class AgentCard {

        @Test
        @DisplayName("GET /.well-known/agent.json returns agent card without auth")
        void agentCardIsPublic() throws Exception {
            mockMvc.perform(get("/.well-known/agent.json").with(anonymous()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.name").value("JavaClaw"))
                    .andExpect(jsonPath("$.url").value("/api/a2a"))
                    .andExpect(jsonPath("$.version").value("1.0.0"))
                    .andExpect(jsonPath("$.capabilities.stateTransitionHistory").value(true))
                    .andExpect(jsonPath("$.skills").isArray())
                    .andExpect(jsonPath("$.skills.length()").value(3));
        }
    }

    @Nested
    @DisplayName("JSON-RPC Endpoint")
    class JsonRpc {

        @Test
        @DisplayName("POST /api/a2a requires authentication")
        void requiresAuth() throws Exception {
            String body = jsonRpc("tasks/get", "1", Map.of("id", "nonexistent"));
            mockMvc.perform(post("/api/a2a")
                            .with(anonymous())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("tasks/send creates a task and returns submitted state")
        void tasksSend() throws Exception {
            String body = jsonRpc(
                    "tasks/send",
                    "req-1",
                    Map.of(
                            "message",
                            Map.of(
                                    "role",
                                    "user",
                                    "parts",
                                    List.of(Map.of("type", "text", "text", "Summarize the project README")))));

            mockMvc.perform(post("/api/a2a")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                    .andExpect(jsonPath("$.id").value("req-1"))
                    .andExpect(jsonPath("$.result.id").isString())
                    .andExpect(jsonPath("$.result.status").value("submitted"))
                    .andExpect(jsonPath("$.result.history[0].role").value("user"))
                    .andExpect(jsonPath("$.result.history[0].parts[0].text").value("Summarize the project README"))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("tasks/get retrieves existing task")
        void tasksGet() throws Exception {
            // First create a task via tasks/send
            String sendBody = jsonRpc(
                    "tasks/send",
                    "s1",
                    Map.of(
                            "message",
                            Map.of(
                                    "role",
                                    "user",
                                    "parts",
                                    List.of(Map.of("type", "text", "text", "Test task for get")))));

            String sendResponse = mockMvc.perform(post("/api/a2a")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sendBody))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String taskId = objectMapper
                    .readTree(sendResponse)
                    .path("result")
                    .path("id")
                    .asText();

            // Now get the task
            String getBody = jsonRpc("tasks/get", "g1", Map.of("id", taskId));
            mockMvc.perform(post("/api/a2a")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(getBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.id").value(taskId))
                    .andExpect(jsonPath("$.result.status").value("submitted"));
        }

        @Test
        @DisplayName("tasks/get returns error for nonexistent task")
        void tasksGetNotFound() throws Exception {
            String body = jsonRpc("tasks/get", "g2", Map.of("id", "nonexistent-id"));
            mockMvc.perform(post("/api/a2a")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error.code").value(-32001))
                    .andExpect(jsonPath("$.error.message").value("Task not found: nonexistent-id"));
        }

        @Test
        @DisplayName("tasks/cancel cancels a submitted task")
        void tasksCancel() throws Exception {
            // Create a task
            String sendBody = jsonRpc(
                    "tasks/send",
                    "s2",
                    Map.of(
                            "message",
                            Map.of(
                                    "role",
                                    "user",
                                    "parts",
                                    List.of(Map.of("type", "text", "text", "Task to cancel")))));

            String sendResponse = mockMvc.perform(post("/api/a2a")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sendBody))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String taskId = objectMapper
                    .readTree(sendResponse)
                    .path("result")
                    .path("id")
                    .asText();

            // Cancel it
            String cancelBody = jsonRpc("tasks/cancel", "c1", Map.of("id", taskId));
            mockMvc.perform(post("/api/a2a")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cancelBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.id").value(taskId))
                    .andExpect(jsonPath("$.result.status").value("canceled"));
        }

        @Test
        @DisplayName("unknown method returns METHOD_NOT_FOUND error")
        void unknownMethod() throws Exception {
            String body = jsonRpc("tasks/unknown", "u1", Map.of());
            mockMvc.perform(post("/api/a2a")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error.code").value(-32601))
                    .andExpect(jsonPath("$.error.message").value("Method not found: tasks/unknown"));
        }

        @Test
        @DisplayName("tasks/send without message returns INVALID_PARAMS")
        void tasksSendNoMessage() throws Exception {
            String body = jsonRpc("tasks/send", "e1", Map.of());
            mockMvc.perform(post("/api/a2a")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error.code").value(-32602));
        }
    }

    private static String jsonRpc(String method, String id, Map<String, Object> params) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "method", method,
                "id", id,
                "params", params));
    }
}
