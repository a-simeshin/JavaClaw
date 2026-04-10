package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.javaclaw.mcp.server.McpServerToolsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for MCP Server mode (9.3).
 * Verifies that JavaClaw exposes its tools via Streamable HTTP MCP protocol.
 */
class McpServerIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MvcResult mcpPost(ObjectNode request) throws Exception {
        return mcpPost(request, null);
    }

    private MvcResult mcpPost(ObjectNode request, String sessionId) throws Exception {
        var builder = post("/api/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(objectMapper.writeValueAsString(request));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return mockMvc.perform(builder).andReturn();
    }

    /** Parses JSON-RPC response from either plain JSON or SSE-wrapped format. */
    private JsonNode parseResponse(String body) throws Exception {
        if (body.startsWith("{")) {
            return objectMapper.readTree(body);
        }
        // SSE format: lines like "id:...", "event:...", "data:{json}"
        return Arrays.stream(body.split("\n"))
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5))
                .filter(data -> !data.isBlank())
                .map(data -> {
                    try {
                        return objectMapper.readTree(data);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(node -> node != null && node.has("result"))
                .findFirst()
                .orElseGet(() -> objectMapper.createObjectNode());
    }

    private String initializeSession() throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "initialize");
        ObjectNode params = request.putObject("params");
        params.put("protocolVersion", "2025-03-26");
        params.putObject("clientInfo").put("name", "test-client").put("version", "1.0.0");
        params.putObject("capabilities");

        MvcResult result = mcpPost(request);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        String sessionId = result.getResponse().getHeader("Mcp-Session-Id");

        // Send initialized notification
        ObjectNode notif = objectMapper.createObjectNode();
        notif.put("jsonrpc", "2.0");
        notif.put("method", "notifications/initialized");
        mcpPost(notif, sessionId);

        return sessionId;
    }

    @Nested
    class McpServerBeanTests {

        @Autowired
        private McpServerToolsService mcpServerToolsService;

        @Test
        void mcpServerToolsServiceIsRegistered() {
            assertThat(mcpServerToolsService).isNotNull();
        }
    }

    @Nested
    class McpEndpointTests {

        @Test
        void mcpEndpointAcceptsInitializeRequest() throws Exception {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", 1);
            request.put("method", "initialize");
            ObjectNode params = request.putObject("params");
            params.put("protocolVersion", "2025-03-26");
            params.putObject("clientInfo").put("name", "test-client").put("version", "1.0.0");
            params.putObject("capabilities");

            MvcResult result = mcpPost(request);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);

            JsonNode response = parseResponse(result.getResponse().getContentAsString());
            assertThat(response.has("result")).isTrue();
            assertThat(response.path("result").path("serverInfo").path("name").asText())
                    .isEqualTo("javaclaw-test");
        }

        @Test
        void mcpEndpointReturnsToolsList() throws Exception {
            String sessionId = initializeSession();

            ObjectNode toolsRequest = objectMapper.createObjectNode();
            toolsRequest.put("jsonrpc", "2.0");
            toolsRequest.put("id", 2);
            toolsRequest.put("method", "tools/list");

            MvcResult result = mcpPost(toolsRequest, sessionId);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);

            JsonNode response = parseResponse(result.getResponse().getContentAsString());
            assertThat(response.has("result")).isTrue();
            JsonNode tools = response.path("result").path("tools");
            assertThat(tools.isArray()).isTrue();
            assertThat(tools.size()).isGreaterThanOrEqualTo(5);

            boolean hasReadFile = false;
            boolean hasWriteFile = false;
            boolean hasListFiles = false;
            boolean hasListSkills = false;
            boolean hasListConversations = false;
            for (JsonNode tool : tools) {
                String name = tool.path("name").asText();
                if ("readFile".equals(name)) hasReadFile = true;
                if ("writeFile".equals(name)) hasWriteFile = true;
                if ("listFiles".equals(name)) hasListFiles = true;
                if ("listSkills".equals(name)) hasListSkills = true;
                if ("listConversations".equals(name)) hasListConversations = true;
            }
            assertThat(hasReadFile).as("readFile tool").isTrue();
            assertThat(hasWriteFile).as("writeFile tool").isTrue();
            assertThat(hasListFiles).as("listFiles tool").isTrue();
            assertThat(hasListSkills).as("listSkills tool").isTrue();
            assertThat(hasListConversations).as("listConversations tool").isTrue();
        }

        @Test
        void mcpEndpointRequiresAuthentication() throws Exception {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", 1);
            request.put("method", "initialize");
            ObjectNode params = request.putObject("params");
            params.put("protocolVersion", "2025-03-26");
            params.putObject("clientInfo").put("name", "test").put("version", "1.0");
            params.putObject("capabilities");

            mockMvc.perform(post("/api/mcp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                            .content(objectMapper.writeValueAsString(request))
                            .with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void mcpInitializeReturnsCorrectCapabilities() throws Exception {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", 1);
            request.put("method", "initialize");
            ObjectNode params = request.putObject("params");
            params.put("protocolVersion", "2025-03-26");
            params.putObject("clientInfo").put("name", "test").put("version", "1.0");
            params.putObject("capabilities");

            MvcResult result = mcpPost(request);
            JsonNode response = parseResponse(result.getResponse().getContentAsString());

            JsonNode capabilities = response.path("result").path("capabilities");
            assertThat(capabilities.has("tools")).isTrue();
            assertThat(response.path("result").path("protocolVersion").asText()).isEqualTo("2025-03-26");
        }
    }

    @Nested
    class McpToolCallTests {

        @Test
        void canCallListSkillsViaMcp() throws Exception {
            String sessionId = initializeSession();

            ObjectNode callRequest = objectMapper.createObjectNode();
            callRequest.put("jsonrpc", "2.0");
            callRequest.put("id", 3);
            callRequest.put("method", "tools/call");
            ObjectNode callParams = callRequest.putObject("params");
            callParams.put("name", "listSkills");
            callParams.putObject("arguments");

            MvcResult callResult = mcpPost(callRequest, sessionId);
            assertThat(callResult.getResponse().getStatus()).isEqualTo(200);

            JsonNode response = parseResponse(callResult.getResponse().getContentAsString());
            assertThat(response.has("result")).isTrue();
            assertThat(response.path("result").path("content").isArray()).isTrue();
        }

        @Test
        void canCallListConversationsViaMcp() throws Exception {
            String sessionId = initializeSession();

            ObjectNode callRequest = objectMapper.createObjectNode();
            callRequest.put("jsonrpc", "2.0");
            callRequest.put("id", 4);
            callRequest.put("method", "tools/call");
            ObjectNode callParams = callRequest.putObject("params");
            callParams.put("name", "listConversations");
            ObjectNode args = callParams.putObject("arguments");
            args.put("page", 0);
            args.put("size", 10);

            MvcResult callResult = mcpPost(callRequest, sessionId);
            assertThat(callResult.getResponse().getStatus()).isEqualTo(200);

            JsonNode response = parseResponse(callResult.getResponse().getContentAsString());
            assertThat(response.has("result")).isTrue();
        }
    }
}
