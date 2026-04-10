package ai.javaclaw.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Environment / whoami endpoints consumed by the SPA shell
 * ({@code GET /api/me}, {@code GET /api/health}).
 *
 * <p>With Spring Security active, {@code /api/me} reads the authenticated principal
 * and returns username + role (ADMIN or USER).
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final DataSource dataSource;

    public SystemController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/me")
    public UserInfoDto me(Authentication authentication) {
        String username = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("USER");
        return new UserInfoDto(username, role);
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
