package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Configures MockMvc with a default admin user for all requests.
 * Import this from any {@code @SpringBootTest} that needs authenticated MockMvc by default.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    MockMvcBuilderCustomizer defaultAdminUser() {
        return builder -> builder.defaultRequest(get("/").with(user("admin").roles("ADMIN")));
    }
}
