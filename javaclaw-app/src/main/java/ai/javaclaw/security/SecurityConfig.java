package ai.javaclaw.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration — HTTP Basic with DB-backed users (Phase 7.1).
 *
 * <p>Public endpoints (no auth required):
 * <ul>
 *   <li>{@code /actuator/**} — health/info/metrics</li>
 *   <li>{@code /api/health} — custom health check</li>
 *   <li>Static resources (SPA: {@code /}, {@code /index.html}, {@code /assets/**})</li>
 * </ul>
 *
 * <p>All other {@code /api/**} require authentication.
 * Admin-only endpoints ({@code /api/skills}, {@code /api/mcp-servers}, {@code /api/users}) require ADMIN role.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: health & actuator
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        .requestMatchers("/api/health")
                        .permitAll()
                        // Public: A2A Agent Card discovery
                        .requestMatchers("/.well-known/agent.json")
                        .permitAll()
                        // Public: SPA static resources
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico")
                        .permitAll()
                        // Admin-only endpoints
                        .requestMatchers("/api/skills/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/mcp-servers/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/users/**")
                        .hasRole("ADMIN")
                        // MCP server endpoint — authenticated
                        .requestMatchers("/api/mcp/**")
                        .authenticated()
                        // A2A protocol endpoint — authenticated
                        .requestMatchers("/api/a2a")
                        .authenticated()
                        // All other API endpoints require authentication
                        .requestMatchers("/api/**")
                        .authenticated()
                        // Everything else (e.g. SPA routes) — permit
                        .anyRequest()
                        .permitAll())
                .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
