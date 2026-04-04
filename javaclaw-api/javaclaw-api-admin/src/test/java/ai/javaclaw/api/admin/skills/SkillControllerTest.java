package ai.javaclaw.api.admin.skills;

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

class SkillControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new SkillController(new SkillStore()))
                .setControllerAdvice(new AdminExceptionHandler())
                .build();
    }

    @Test
    void createListUpdateDeleteLifecycle() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"brave\",\"description\":\"Web search\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("brave"))
                .andReturn();

        String id = extractId(created);

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("brave"));

        mockMvc.perform(put("/api/skills/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"brave\",\"description\":\"Web search\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/skills/" + id)).andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/skills/does-not-exist")).andExpect(status().isNotFound());
    }

    private static String extractId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        int i = body.indexOf("\"id\":\"") + 6;
        int j = body.indexOf("\"", i);
        return body.substring(i, j);
    }
}
