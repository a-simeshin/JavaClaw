package ai.javaclaw.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    // ObjectMapper is not guaranteed to be in the context as a named bean in SB4 MOCK mode;
    // instantiate directly — default configuration is sufficient for Map parsing.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ── Shared state for chained tests ────────────────────────────────────────

    static String createdConversationId;
    static String createdMcpServerId;
    static String createdSkillId;
    static String createdFilePath;

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
                .andExpect(jsonPath("$.title").value("New conversation"))
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
    void getMe_withNoPrincipal_returnsGuest() throws Exception {
        mockMvc.perform(get("/api/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.username").value("guest"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @Order(51)
    void getHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.components").isArray());
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
}
