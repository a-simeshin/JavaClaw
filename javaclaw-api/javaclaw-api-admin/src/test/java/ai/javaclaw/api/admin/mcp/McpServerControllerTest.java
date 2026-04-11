package ai.javaclaw.api.admin.mcp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.api.admin.AdminExceptionHandler;
import ai.javaclaw.users.UserResolver;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class McpServerControllerTest {

    @Mock
    private McpServerService service;

    @Mock
    private UserResolver userResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new McpServerController(service, userResolver))
                .setControllerAdvice(new AdminExceptionHandler())
                .build();
    }

    @Test
    void createUpdateStatusDeleteLifecycle() throws Exception {
        final McpServerDto created =
                new McpServerDto("generated-id", "local", "stdio", "node ./server.js", null, Map.of(), true);
        when(service.create(any(McpServerDto.class))).thenReturn(created);

        mockMvc.perform(
                        post("/api/mcp-servers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"local\",\"transport\":\"stdio\",\"command\":\"node ./server.js\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("local"))
                .andExpect(jsonPath("$.transport").value("stdio"));

        when(service.list()).thenReturn(List.of(created));
        mockMvc.perform(get("/api/mcp-servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("local"));

        when(service.status("generated-id")).thenReturn(new McpServerStatusDto("generated-id", "connected", null));
        mockMvc.perform(get("/api/mcp-servers/generated-id/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("connected"));

        final McpServerDto updated =
                new McpServerDto("generated-id", "local", "stdio", "node ./s.js", null, Map.of(), false);
        when(service.update(eq("generated-id"), any(McpServerDto.class))).thenReturn(updated);
        mockMvc.perform(
                        put("/api/mcp-servers/generated-id")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"local\",\"transport\":\"stdio\",\"command\":\"node ./s.js\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        when(service.status("generated-id")).thenReturn(new McpServerStatusDto("generated-id", "disabled", null));
        mockMvc.perform(get("/api/mcp-servers/generated-id/status"))
                .andExpect(jsonPath("$.status").value("disabled"));

        doNothing().when(service).delete("generated-id");
        mockMvc.perform(delete("/api/mcp-servers/generated-id")).andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingReturnsNotFound() throws Exception {
        doThrow(new NoSuchElementException("mcp server not found: does-not-exist"))
                .when(service)
                .delete("does-not-exist");

        mockMvc.perform(delete("/api/mcp-servers/does-not-exist")).andExpect(status().isNotFound());
    }
}
