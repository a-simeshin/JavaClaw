package ai.javaclaw.integration.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Helper for obtaining session cookies in MockMvc integration tests. */
public final class IntegrationTestAuthHelper {

    private IntegrationTestAuthHelper() {}

    public static Cookie loginAndGetSessionCookie(
            MockMvc mockMvc, ObjectMapper objectMapper, String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("JCLAW_SESSION");
        if (cookie == null) throw new IllegalStateException("Login did not return JCLAW_SESSION cookie");
        return cookie;
    }

    public static Cookie adminCookie(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return loginAndGetSessionCookie(mockMvc, objectMapper, "admin", "admin");
    }
}
