package ai.javaclaw.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration — HTTP Basic with users from {@code javaclaw.security.users[]}.
 *
 * <p>Public endpoints (no auth required):
 * <ul>
 *   <li>{@code /actuator/**} — health/info/metrics</li>
 *   <li>{@code /api/health} — custom health check</li>
 *   <li>Static resources (SPA: {@code /}, {@code /index.html}, {@code /assets/**})</li>
 * </ul>
 *
 * <p>All other {@code /api/**} require authentication.
 * Admin-only endpoints ({@code /api/skills}, {@code /api/mcp-servers}) require ADMIN role.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
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
                        // Public: SPA static resources
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico")
                        .permitAll()
                        // Admin-only endpoints
                        .requestMatchers("/api/skills/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/mcp-servers/**")
                        .hasRole("ADMIN")
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

    @Bean
    public UserDetailsService userDetailsService(SecurityProperties properties, PasswordEncoder encoder) {
        var users = properties.getUsers().stream()
                .map(entry -> User.builder()
                        .username(entry.getUsername())
                        .password(encoder.encode(entry.getPassword()))
                        .roles(entry.getRole())
                        .build())
                .toList();

        return new InMemoryUserDetailsManager(users);
    }
}
