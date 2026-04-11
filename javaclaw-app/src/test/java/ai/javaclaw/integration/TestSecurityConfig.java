package ai.javaclaw.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ai.javaclaw.users.Permission;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Configures MockMvc with a default admin user for all requests.
 * Includes ROLE_ADMIN + all granular PERM_* authorities (Phase 13).
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    MockMvcBuilderCustomizer defaultAdminUser() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        for (Permission p : Permission.values()) {
            authorities.add(new SimpleGrantedAuthority(p.authority()));
        }
        return builder -> builder.defaultRequest(get("/").with(user("admin").authorities(authorities)));
    }
}
