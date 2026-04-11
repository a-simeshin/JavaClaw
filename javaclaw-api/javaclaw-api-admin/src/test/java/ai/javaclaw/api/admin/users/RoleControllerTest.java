package ai.javaclaw.api.admin.users;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.agent.config.RoleAgentConfigService;
import ai.javaclaw.agent.config.RoleModelAllowlistService;
import ai.javaclaw.api.admin.AdminExceptionHandler;
import ai.javaclaw.users.CustomRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private CustomRoleService roleService;

    @Mock
    private RoleAgentConfigService agentConfigService;

    @Mock
    private RoleModelAllowlistService modelAllowlistService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new RoleController(roleService, agentConfigService, modelAllowlistService))
                .setControllerAdvice(new AdminExceptionHandler())
                .build();
    }

    @Test
    void whenDeleteAllowedModelWithSlash_thenNoContent() throws Exception {
        doNothing().when(modelAllowlistService).removeAllowedModel("USER", "openai/gpt-4o");

        mockMvc.perform(delete("/api/roles/USER/allowed-models/openai/gpt-4o")).andExpect(status().isNoContent());

        verify(modelAllowlistService).removeAllowedModel("USER", "openai/gpt-4o");
    }

    @Test
    void whenDeleteAllowedModelSimple_thenNoContent() throws Exception {
        doNothing().when(modelAllowlistService).removeAllowedModel("ADMIN", "gpt-4o");

        mockMvc.perform(delete("/api/roles/ADMIN/allowed-models/gpt-4o")).andExpect(status().isNoContent());

        verify(modelAllowlistService).removeAllowedModel("ADMIN", "gpt-4o");
    }

    @Test
    void whenDeleteAllowedModelWithMultipleSlashes_thenNoContent() throws Exception {
        doNothing().when(modelAllowlistService).removeAllowedModel("USER", "provider/org/model-v2");

        mockMvc.perform(delete("/api/roles/USER/allowed-models/provider/org/model-v2"))
                .andExpect(status().isNoContent());

        verify(modelAllowlistService).removeAllowedModel("USER", "provider/org/model-v2");
    }
}
