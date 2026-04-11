package ai.javaclaw.security.config;

import ai.javaclaw.security.session.SessionCookieAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SessionCookieAuthFilter sessionCookieAuthFilter;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CsrfTokenRequestAttributeHandler (without Xor) so clients can read XSRF-TOKEN cookie
        // raw and echo it back as X-XSRF-TOKEN header. Default is XorCsrfTokenRequestAttributeHandler
        // which masks the token and breaks SPA clients that read the cookie directly.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null); // read from cookie, not attribute

        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/csrf"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(ctx -> ctx.requireExplicitSave(false)
                        .securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Public: health, actuator, SPA, A2A discovery
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        .requestMatchers("/api/health")
                        .permitAll()
                        .requestMatchers("/.well-known/agent.json")
                        .permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico")
                        .permitAll()
                        // Auth endpoints — login/csrf public, rest authenticated
                        .requestMatchers("/api/auth/login", "/api/auth/csrf")
                        .permitAll()
                        // Admin-only (granular permissions)
                        .requestMatchers("/api/skills/**")
                        .hasAuthority("PERM_SKILL_LIST")
                        .requestMatchers("/api/mcp-servers/**")
                        .hasAuthority("PERM_MCP_LIST")
                        .requestMatchers("/api/users/**")
                        .hasAuthority("PERM_USER_LIST")
                        .requestMatchers("/api/roles/**")
                        .hasAuthority("PERM_USER_LIST")
                        .requestMatchers("/api/audit/**")
                        .hasAuthority("PERM_AUDIT_READ")
                        // Authenticated
                        .requestMatchers("/api/mcp/**")
                        .authenticated()
                        .requestMatchers("/api/a2a")
                        .authenticated()
                        .requestMatchers("/api/**")
                        .authenticated()
                        // SPA catch-all
                        .anyRequest()
                        .permitAll())
                .addFilterBefore(sessionCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);
        // NO .httpBasic() — Basic auth is completely removed

        return http.build();
    }
}
