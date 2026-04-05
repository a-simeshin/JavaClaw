package ai.javaclaw.api.admin.skills;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class SkillControllerTest {

    @Mock
    private SkillService skillService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new SkillController(skillService))
                .setControllerAdvice(new AdminExceptionHandler())
                .build();
    }

    @Test
    void createListUpdateDeleteLifecycle() throws Exception {
        final SkillDto created = new SkillDto("uuid-1", "brave", "Web search", true);
        when(skillService.create(any(SkillDto.class))).thenReturn(created);

        mockMvc.perform(post("/api/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"brave\",\"description\":\"Web search\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("uuid-1"))
                .andExpect(jsonPath("$.name").value("brave"));

        when(skillService.list()).thenReturn(List.of(created));

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("brave"));

        final SkillDto updated = new SkillDto("uuid-1", "brave", "Web search", false);
        when(skillService.update(eq("uuid-1"), any(SkillDto.class))).thenReturn(updated);

        mockMvc.perform(put("/api/skills/uuid-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"brave\",\"description\":\"Web search\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/skills/uuid-1")).andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingReturnsNotFound() throws Exception {
        doThrow(new NoSuchElementException("skill not found: does-not-exist"))
                .when(skillService)
                .delete("does-not-exist");

        mockMvc.perform(delete("/api/skills/does-not-exist")).andExpect(status().isNotFound());
    }
}
