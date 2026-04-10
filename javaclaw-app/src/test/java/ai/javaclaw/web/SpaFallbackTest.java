package ai.javaclaw.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that the SPA resource handler:
 *   1. serves index.html for any non-API path (client-side routes),
 *   2. leaves /api/** and /actuator/** untouched so they 404 through normal channels,
 *   3. serves a real static asset when one exists.
 */
@WebMvcTest(
        controllers = {},
        useDefaultFilters = false)
@Import(SpaWebConfig.class)
@WithMockUser
class SpaFallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void clientRouteFallsBackToIndexHtml() throws Exception {
        // Use a synthetic SPA path that no backend controller owns.
        mockMvc.perform(get("/app/overview").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<div id=\"root\">")));
    }

    @Test
    void deepClientRouteFallsBackToIndexHtml() throws Exception {
        mockMvc.perform(get("/admin/users/42").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<div id=\"root\">")));
    }

    @Test
    void apiPathIsNotRewrittenToIndexHtml() throws Exception {
        mockMvc.perform(get("/api/nonexistent")).andExpect(status().isNotFound());
    }

    @Test
    void actuatorPathIsNotRewrittenToIndexHtml() throws Exception {
        mockMvc.perform(get("/actuator/nonexistent")).andExpect(status().isNotFound());
    }

    @Test
    void existingStaticAssetIsServedDirectly() throws Exception {
        mockMvc.perform(get("/assets/test-asset.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("marker:static-ok")));
    }
}
