package ai.javaclaw.api;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Environment / whoami endpoints consumed by the SPA shell
 * ({@code GET /api/me}, {@code GET /api/health}).
 *
 * <p>When Spring Security is absent (current state), {@code /api/me} echoes the
 * servlet principal if present, otherwise falls back to {@code guest / USER}.
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    @GetMapping("/me")
    public UserInfoDto me(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal == null) {
            return new UserInfoDto("guest", "USER");
        }
        String role = request.isUserInRole("ADMIN") ? "ADMIN" : "USER";
        return new UserInfoDto(principal.getName(), role);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString(), "components", List.of("chat", "memory"));
    }

    public record UserInfoDto(String username, String role) {}
}
