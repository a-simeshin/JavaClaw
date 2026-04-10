package ai.javaclaw.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.agent.audit.DeliveryAuditLog;
import ai.javaclaw.agent.audit.DeliveryAuditLogRepository;
import ai.javaclaw.agent.audit.TaskAuditLog;
import ai.javaclaw.agent.audit.TaskAuditLogRepository;
import ai.javaclaw.integration.TestSecurityConfig;
import ai.javaclaw.tasks.ApprovalRequest;
import ai.javaclaw.tasks.ApprovalRequestRepository;
import ai.javaclaw.tasks.NotifyPolicy;
import ai.javaclaw.tasks.Task;
import ai.javaclaw.tasks.TaskExecution;
import ai.javaclaw.tasks.TaskExecutionRepository;
import ai.javaclaw.tasks.TaskRepository;
import ai.javaclaw.tasks.TaskRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Contract-compliance integration tests.
 *
 * <p>Boots the full application against a real Testcontainers PostgreSQL instance
 * and asserts that every REST endpoint returns the status codes, content-types and
 * JSON shapes documented in {@code specs/openapi.yaml}.
 *
 * <p>Tests are ordered to avoid cross-test interference (create before update/delete).
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contracttest")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(TestSecurityConfig.class)
class OpenApiContractComplianceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("javaclaw")
            .withUsername("javaclaw")
            .withPassword("javaclaw");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskExecutionRepository taskExecutionRepository;

    @Autowired
    TaskAuditLogRepository taskAuditLogRepository;

    @Autowired
    DeliveryAuditLogRepository deliveryAuditLogRepository;

    @Autowired
    ApprovalRequestRepository approvalRequestRepository;

    // ObjectMapper is not guaranteed to be in the context as a named bean in SB4 MOCK mode;
    // instantiate directly — default configuration is sufficient for Map parsing.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ── Shared state for chained tests ────────────────────────────────────────

    static String createdConversationId;
    static String createdMcpServerId;
    static String createdSkillId;
    static String createdFilePath;
    static String createdTaskId;
    static String createdChildTaskId;
    static String createdApprovalId;

    // =========================================================================
    // CONVERSATIONS
    // =========================================================================

    @Test
    @Order(10)
    void getConversations_returnsValidPageResponse() throws Exception {
        mockMvc.perform(get("/api/conversations").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @Order(11)
    void createConversation_withTitle_returnsConversationDto() throws Exception {
        String body =
                """
                {"title": "Contract test conversation", "id": "contract-test-conv-1"}
                """;
        MvcResult result = mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("contract-test-conv-1"))
                .andExpect(jsonPath("$.title").value("Contract test conversation"))
                .andExpect(jsonPath("$.messageCount").isNumber())
                .andReturn();

        // Capture for downstream tests
        String responseJson = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> dto = objectMapper.readValue(responseJson, Map.class);
        createdConversationId = (String) dto.get("id");
    }

    @Test
    @Order(12)
    void createConversation_withoutBody_returnsConversationDto() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.messageCount").value(0));
    }

    @Test
    @Order(13)
    void createConversation_titleTooLong_returns400() throws Exception {
        String tooLong = "x".repeat(121);
        String body = "{\"title\": \"" + tooLong + "\"}";
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(14)
    void getConversationMessages_byId() throws Exception {
        String id = createdConversationId != null ? createdConversationId : "contract-test-conv-1";
        mockMvc.perform(get("/api/conversations/{id}/messages", id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @Order(19)
    void deleteConversation_returns204() throws Exception {
        // Create a fresh one so we can safely delete it
        String body = "{\"title\": \"to-delete\"}";
        MvcResult created = mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> dto = objectMapper.readValue(created.getResponse().getContentAsString(), Map.class);
        String id = (String) dto.get("id");

        mockMvc.perform(delete("/api/conversations/{id}", id)).andExpect(status().isNoContent());
    }

    // =========================================================================
    // FILES
    // =========================================================================

    @Test
    @Order(20)
    void listFiles_returnsTreeStructure() throws Exception {
        mockMvc.perform(get("/api/files").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("dir"))
                .andExpect(jsonPath("$.name").isString())
                .andExpect(jsonPath("$.children").isArray());
    }

    @Test
    @Order(21)
    void writeFile_createsOrUpdatesFile() throws Exception {
        createdFilePath = "contract-test-dir/hello.txt";
        String body = "{\"path\": \"" + createdFilePath + "\", \"content\": \"hello contract\"}";
        mockMvc.perform(put("/api/files/{path}", createdFilePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.path").value(createdFilePath))
                .andExpect(jsonPath("$.content").value("hello contract"));
    }

    @Test
    @Order(22)
    void readFile_byPath_returnsFileContent() throws Exception {
        String path = createdFilePath != null ? createdFilePath : "contract-test-dir/hello.txt";
        mockMvc.perform(get("/api/files/{path}", path).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.path").isString())
                .andExpect(jsonPath("$.content").isString());
    }

    @Test
    @Order(23)
    void createFile_withBlankPath_returns400() throws Exception {
        String body = "{\"path\": \"\", \"content\": \"data\"}";
        mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(24)
    void createFile_newFile_returns201() throws Exception {
        String newPath = "contract-test-dir/new-file-" + System.currentTimeMillis() + ".txt";
        String body = "{\"path\": \"" + newPath + "\", \"content\": \"new content\"}";
        mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.path").value(newPath))
                .andExpect(jsonPath("$.content").value("new content"));
    }

    @Test
    @Order(29)
    void deleteFile_returns204() throws Exception {
        // Write a fresh file then delete it
        String path = "contract-test-dir/to-delete-" + System.currentTimeMillis() + ".txt";
        String writeBody = "{\"path\": \"" + path + "\", \"content\": \"bye\"}";
        mockMvc.perform(put("/api/files/{path}", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/files/{path}", path)).andExpect(status().isNoContent());
    }

    // =========================================================================
    // MCP SERVERS
    // =========================================================================

    @Test
    @Order(30)
    void listMcpServers_returnsList() throws Exception {
        mockMvc.perform(get("/api/mcp-servers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(31)
    void createMcpServer_withValidStdio_returnsDto() throws Exception {
        String body =
                """
                {
                  "name": "Contract Test MCP",
                  "transport": "stdio",
                  "command": "echo hello",
                  "enabled": true
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/mcp-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Contract Test MCP"))
                .andExpect(jsonPath("$.transport").value("stdio"))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> dto = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        createdMcpServerId = (String) dto.get("id");
    }

    @Test
    @Order(32)
    void createMcpServer_withInvalidTransport_returns400() throws Exception {
        String body =
                """
                {
                  "name": "Bad Transport MCP",
                  "transport": "websocket",
                  "enabled": true
                }
                """;
        mockMvc.perform(post("/api/mcp-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(33)
    void updateMcpServer_returns200() throws Exception {
        assertThat(createdMcpServerId)
                .as("createdMcpServerId must have been set in createMcpServer test")
                .isNotNull();

        String body =
                """
                {
                  "name": "Updated MCP",
                  "transport": "stdio",
                  "command": "echo updated",
                  "enabled": false
                }
                """;
        mockMvc.perform(put("/api/mcp-servers/{id}", createdMcpServerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdMcpServerId))
                .andExpect(jsonPath("$.name").value("Updated MCP"));
    }

    @Test
    @Order(34)
    void getMcpServerStatus_returnsStatus() throws Exception {
        assertThat(createdMcpServerId)
                .as("createdMcpServerId must have been set")
                .isNotNull();

        mockMvc.perform(get("/api/mcp-servers/{id}/status", createdMcpServerId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdMcpServerId))
                .andExpect(jsonPath("$.status").isString());
    }

    @Test
    @Order(39)
    void deleteMcpServer_returns204() throws Exception {
        assertThat(createdMcpServerId)
                .as("createdMcpServerId must have been set")
                .isNotNull();

        mockMvc.perform(delete("/api/mcp-servers/{id}", createdMcpServerId)).andExpect(status().isNoContent());
    }

    // =========================================================================
    // SKILLS
    // =========================================================================

    @Test
    @Order(40)
    void listSkills_returnsList() throws Exception {
        mockMvc.perform(get("/api/skills").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(41)
    void createSkill_withValidData_returnsDto() throws Exception {
        String body =
                """
                {
                  "name": "Web Search Skill",
                  "description": "Search the web using DuckDuckGo",
                  "enabled": true
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Web Search Skill"))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> dto = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        createdSkillId = (String) dto.get("id");
    }

    @Test
    @Order(42)
    void createSkill_withBlankName_returns400() throws Exception {
        String body =
                """
                {
                  "name": "",
                  "enabled": true
                }
                """;
        mockMvc.perform(post("/api/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(43)
    void updateSkill_returns200() throws Exception {
        assertThat(createdSkillId)
                .as("createdSkillId must have been set in createSkill test")
                .isNotNull();

        String body =
                """
                {
                  "name": "Updated Skill",
                  "description": "An updated description",
                  "enabled": false
                }
                """;
        mockMvc.perform(put("/api/skills/{id}", createdSkillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdSkillId))
                .andExpect(jsonPath("$.name").value("Updated Skill"));
    }

    @Test
    @Order(49)
    void deleteSkill_returns204() throws Exception {
        assertThat(createdSkillId).as("createdSkillId must have been set").isNotNull();

        mockMvc.perform(delete("/api/skills/{id}", createdSkillId)).andExpect(status().isNoContent());
    }

    // =========================================================================
    // SYSTEM
    // =========================================================================

    @Test
    @Order(50)
    void getMe_withAuthenticatedUser_returnsAdminInfo() throws Exception {
        mockMvc.perform(get("/api/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @Order(51)
    void getHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.components").isMap());
    }

    // =========================================================================
    // CHAT
    // =========================================================================

    @Test
    @Order(60)
    void sendChatMessage_returnsSseStream() throws Exception {
        String body =
                """
                {"content": "ping", "conversationId": "contract-test-chat-1"}
                """;
        MvcResult result = mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        // Assert Content-Type is text/plain
        String contentType = result.getResponse().getContentType();
        assertThat(contentType)
                .as("SSE endpoint must return text/plain")
                .isNotNull()
                .contains("text/plain");

        // Vercel AI SDK header must be present
        String vercelHeader = result.getResponse().getHeader("x-vercel-ai-data-stream");
        assertThat(vercelHeader).as("x-vercel-ai-data-stream header must be v1").isEqualTo("v1");
    }

    @Test
    @Order(61)
    void sendChatMessage_withBlankContent_returns400() throws Exception {
        String body = """
                {"content": ""}
                """;
        // No Accept header: endpoint produces text/plain, sending APPLICATION_JSON triggers 406 before validation
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(62)
    void sendChatMessage_withMissingContent_returns400() throws Exception {
        String body = """
                {"conversationId": "some-conv"}
                """;
        // No Accept header: endpoint produces text/plain, sending APPLICATION_JSON triggers 406 before validation
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // TASKS
    // =========================================================================

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

    @Test
    @Order(70)
    void listTasks_returnsArray() throws Exception {
        Task task = saveTask("contract-task", "test desc", Task.Status.todo, "guest", "contract-conv");
        createdTaskId = task.getId();

        mockMvc.perform(get("/api/tasks").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name == 'contract-task')]").exists());
    }

    @Test
    @Order(71)
    void listTasks_filterByStatus() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "todo").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(72)
    void listTasks_filterByUserId() throws Exception {
        mockMvc.perform(get("/api/tasks").param("userId", "guest").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(73)
    void getTask_returnsTaskDto() throws Exception {
        assertThat(createdTaskId).as("createdTaskId must have been set").isNotNull();

        mockMvc.perform(get("/api/tasks/{id}", createdTaskId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdTaskId))
                .andExpect(jsonPath("$.name").value("contract-task"))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.conversationId").value("contract-conv"))
                .andExpect(jsonPath("$.notifyPolicy").value("done_only"))
                .andExpect(jsonPath("$.runtimeType").value("async"))
                .andExpect(jsonPath("$.timeoutSeconds").value(300))
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    @Order(74)
    void getTask_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}", "non-existent-task").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(75)
    void getChildTasks_returnsArray() throws Exception {
        assertThat(createdTaskId).as("createdTaskId must have been set").isNotNull();

        // Create a child task
        Task child = new Task(
                null,
                "child-task",
                Instant.now(),
                Instant.now(),
                Task.Status.todo,
                "child desc",
                null,
                null,
                "contract-conv",
                createdTaskId,
                NotifyPolicy.done_only,
                TaskRuntime.async,
                300,
                false,
                "guest",
                null,
                null,
                null);
        child = taskRepository.save(child);
        createdChildTaskId = child.getId();

        mockMvc.perform(get("/api/tasks/{id}/children", createdTaskId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("child-task"))
                .andExpect(jsonPath("$[0].parentTaskId").value(createdTaskId));
    }

    @Test
    @Order(76)
    void cancelTask_returnsCancelledDto() throws Exception {
        // Create a fresh task to cancel (todo status)
        Task toCancel = saveTask("to-cancel", "cancel me", Task.Status.todo, "guest", "contract-conv");

        mockMvc.perform(post("/api/tasks/{id}/cancel", toCancel.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(toCancel.getId()))
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    @Order(79)
    void deleteTask_returns204() throws Exception {
        // Create a fresh task to delete
        Task toDelete = saveTask("to-delete", "delete me", Task.Status.todo, "guest", "contract-conv");

        mockMvc.perform(delete("/api/tasks/{id}", toDelete.getId())).andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/tasks/{id}", toDelete.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // TASK AUDIT
    // =========================================================================

    @Test
    @Order(80)
    void getTaskAudit_returnsArray() throws Exception {
        assertThat(createdTaskId).as("createdTaskId must have been set").isNotNull();

        // Insert an audit entry
        TaskAuditLog auditLog = TaskAuditLog.taskEvent(createdTaskId, null, TaskAuditLog.EVENT_CREATED);
        taskAuditLogRepository.save(auditLog);

        mockMvc.perform(get("/api/tasks/{taskId}/audit", createdTaskId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].taskId").value(createdTaskId))
                .andExpect(jsonPath("$[0].eventType").value("created"));
    }

    @Test
    @Order(81)
    void getTaskExecutions_returnsArray() throws Exception {
        assertThat(createdTaskId).as("createdTaskId must have been set").isNotNull();

        // Insert an execution
        TaskExecution execution = TaskExecution.start(createdTaskId, 1, "system prompt", "user prompt");
        taskExecutionRepository.save(execution);

        mockMvc.perform(get("/api/tasks/{taskId}/executions", createdTaskId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].taskId").value(createdTaskId))
                .andExpect(jsonPath("$[0].executionNumber").value(1))
                .andExpect(jsonPath("$[0].status").isString());
    }

    @Test
    @Order(82)
    void getTaskDeliveries_returnsArray() throws Exception {
        assertThat(createdTaskId).as("createdTaskId must have been set").isNotNull();

        // Insert a delivery audit log
        DeliveryAuditLog delivery =
                DeliveryAuditLog.delivered(createdTaskId, "contract-conv", "web_chat", "Test message", 1, 50L);
        deliveryAuditLogRepository.save(delivery);

        mockMvc.perform(get("/api/tasks/{taskId}/deliveries", createdTaskId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].taskId").value(createdTaskId))
                .andExpect(jsonPath("$[0].status").value("delivered"))
                .andExpect(jsonPath("$[0].channelName").value("web_chat"));
    }

    // =========================================================================
    // APPROVALS
    // =========================================================================

    @Test
    @Order(85)
    void getPendingApprovals_returnsArray() throws Exception {
        assertThat(createdTaskId).as("createdTaskId must have been set").isNotNull();

        // Create a pending approval
        ApprovalRequest approval = ApprovalRequest.create(
                createdTaskId, "contract-conv", "Proceed?", Instant.now().plusSeconds(300));
        approval = approvalRequestRepository.save(approval);
        createdApprovalId = approval.getId();

        mockMvc.perform(get("/api/chat/approval/pending")
                        .param("conversationId", "contract-conv")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].question").value("Proceed?"))
                .andExpect(jsonPath("$[0].status").value("pending"));
    }

    @Test
    @Order(86)
    void respondToApproval_returns200() throws Exception {
        assertThat(createdApprovalId).as("createdApprovalId must have been set").isNotNull();

        String body = """
                {"response": "Yes, proceed"}
                """;
        mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", createdApprovalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @Order(87)
    void respondToApproval_notFound_returns404() throws Exception {
        String body = """
                {"response": "Yes"}
                """;
        mockMvc.perform(post("/api/chat/approval/{approvalId}/respond", "non-existent-approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // NOTIFICATIONS (SSE)
    // =========================================================================

    @Test
    @Order(90)
    void subscribeNotifications_returnsSseStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/chat/notifications/{conversationId}", "contract-conv")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType)
                .as("SSE notification endpoint must return text/event-stream")
                .isNotNull()
                .contains("text/event-stream");
    }

    // =========================================================================
    // CHAT AUDIT
    // =========================================================================

    @Test
    @Order(95)
    void getChatAudit_returnsArray() throws Exception {
        mockMvc.perform(get("/api/audit/chat")
                        .param("conversationId", "contract-conv")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(96)
    void getChatAudit_withLimit_returnsArray() throws Exception {
        mockMvc.perform(get("/api/audit/chat")
                        .param("conversationId", "contract-conv")
                        .param("limit", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }
}
