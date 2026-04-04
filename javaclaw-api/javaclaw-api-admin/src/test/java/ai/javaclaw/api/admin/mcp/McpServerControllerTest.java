package ai.javaclaw.api.admin.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.api.admin.AdminExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class McpServerControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new McpServerController(new McpServerStore()))
                .setControllerAdvice(new AdminExceptionHandler())
                .build();
    }

    @Test
    void createUpdateStatusDeleteLifecycle() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/mcp-servers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"local\",\"transport\":\"stdio\",\"command\":\"node ./server.js\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("local"))
                .andExpect(jsonPath("$.transport").value("stdio"))
                .andReturn();

        String id = extractId(result);

        mockMvc.perform(get("/api/mcp-servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("local"));

        mockMvc.perform(get("/api/mcp-servers/" + id + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("connected"));

        mockMvc.perform(
                        put("/api/mcp-servers/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"local\",\"transport\":\"stdio\",\"command\":\"node ./s.js\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/mcp-servers/" + id + "/status"))
                .andExpect(jsonPath("$.status").value("disabled"));

        mockMvc.perform(delete("/api/mcp-servers/" + id)).andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/mcp-servers/does-not-exist")).andExpect(status().isNotFound());
    }

    private static String extractId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        int i = body.indexOf("\"id\":\"") + 6;
        int j = body.indexOf("\"", i);
        return body.substring(i, j);
    }
}
