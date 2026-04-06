package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for admin REST APIs: skills, MCP servers, and files.
 *
 * <p>Full CRUD lifecycle with validation against a real PostgreSQL instance.
 */
class AdminApiIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SkillsCrud {

        private static String createdSkillId;

        @Test
        @Order(1)
        void listSkills_returnsArray() throws Exception {
            mockMvc.perform(get("/api/skills"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @Order(2)
        void createSkill_withValidData_returns201() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/skills")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test Skill\",\"description\":\"A test skill\",\"enabled\":true}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("Test Skill"))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andReturn();

            @SuppressWarnings("unchecked")
            Map<String, Object> body =
                    objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
            createdSkillId = (String) body.get("id");
            assertThat(createdSkillId).isNotBlank();
        }

        @Test
        @Order(3)
        void createSkill_withBlankName_returns400() throws Exception {
            mockMvc.perform(post("/api/skills")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\",\"description\":\"bad\",\"enabled\":false}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(4)
        void updateSkill_toggleEnabled_returns200() throws Exception {
            assertThat(createdSkillId).as("skill must be created first").isNotNull();

            mockMvc.perform(put("/api/skills/{id}", createdSkillId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test Skill\",\"description\":\"Updated\",\"enabled\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
        }

        @Test
        @Order(5)
        void deleteSkill_returns204() throws Exception {
            assertThat(createdSkillId).as("skill must be created first").isNotNull();

            mockMvc.perform(delete("/api/skills/{id}", createdSkillId)).andExpect(status().isNoContent());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class McpServersCrud {

        private static String createdServerId;

        @Test
        @Order(1)
        void listMcpServers_returnsArray() throws Exception {
            mockMvc.perform(get("/api/mcp-servers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @Order(2)
        void createMcpServer_withValidStdio_returns201() throws Exception {
            MvcResult result = mockMvc.perform(
                            post("/api/mcp-servers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"name\":\"test-mcp\",\"transport\":\"stdio\",\"command\":\"cat\",\"enabled\":true}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("test-mcp"))
                    .andExpect(jsonPath("$.transport").value("stdio"))
                    .andReturn();

            @SuppressWarnings("unchecked")
            Map<String, Object> body =
                    objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
            createdServerId = (String) body.get("id");
            assertThat(createdServerId).isNotBlank();
        }

        @Test
        @Order(3)
        void createMcpServer_withInvalidTransport_returns400() throws Exception {
            mockMvc.perform(post("/api/mcp-servers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"bad\",\"transport\":\"grpc\",\"command\":\"cat\",\"enabled\":true}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(4)
        void updateMcpServer_returns200() throws Exception {
            assertThat(createdServerId).as("server must be created first").isNotNull();

            mockMvc.perform(
                            put("/api/mcp-servers/{id}", createdServerId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"name\":\"updated-mcp\",\"transport\":\"stdio\",\"command\":\"echo\",\"enabled\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("updated-mcp"));
        }

        @Test
        @Order(5)
        void getMcpServerStatus_returnsStatus() throws Exception {
            assertThat(createdServerId).as("server must be created first").isNotNull();

            mockMvc.perform(get("/api/mcp-servers/{id}/status", createdServerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").isString());
        }

        @Test
        @Order(6)
        void deleteMcpServer_returns204() throws Exception {
            assertThat(createdServerId).as("server must be created first").isNotNull();

            mockMvc.perform(delete("/api/mcp-servers/{id}", createdServerId)).andExpect(status().isNoContent());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FilesCrud {

        private static final String TEST_FILE_PATH = "integ-test-dir/test-file-" + System.currentTimeMillis() + ".txt";

        @Test
        @Order(1)
        void listFiles_returnsTreeObject() throws Exception {
            // GET /api/files returns a single FileNodeDto (root dir object), not an array
            mockMvc.perform(get("/api/files").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("dir"))
                    .andExpect(jsonPath("$.name").isString())
                    .andExpect(jsonPath("$.children").isArray());
        }

        @Test
        @Order(2)
        void createFile_returns201() throws Exception {
            mockMvc.perform(post("/api/files")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content("{\"path\":\"" + TEST_FILE_PATH
                                    + "\",\"content\":\"hello from integration test\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(TEST_FILE_PATH))
                    .andExpect(jsonPath("$.content").value("hello from integration test"));
        }

        @Test
        @Order(3)
        void readFile_byPath_returnsContent() throws Exception {
            mockMvc.perform(get("/api/files/{path}", TEST_FILE_PATH).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("hello from integration test"));
        }

        @Test
        @Order(4)
        void createFile_withBlankPath_returns400() throws Exception {
            mockMvc.perform(post("/api/files")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"path\":\"\",\"content\":\"data\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
