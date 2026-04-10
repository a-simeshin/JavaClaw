package ai.javaclaw.api;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
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

    private final DataSource dataSource;

    public SystemController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

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
    public ResponseEntity<Map<String, Object>> health() {
        var result = new LinkedHashMap<String, Object>();
        result.put("timestamp", Instant.now().toString());

        var components = new LinkedHashMap<String, String>();
        components.put("chat", "UP");
        components.put("memory", "UP");

        boolean dbUp = checkDatabase();
        components.put("db", dbUp ? "UP" : "DOWN");

        String overallStatus = dbUp ? "UP" : "DOWN";
        result.put("status", overallStatus);
        result.put("components", components);

        int httpStatus = dbUp ? 200 : 503;
        return ResponseEntity.status(httpStatus).body(result);
    }

    private boolean checkDatabase() {
        try (var conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    public record UserInfoDto(String username, String role) {}
}
