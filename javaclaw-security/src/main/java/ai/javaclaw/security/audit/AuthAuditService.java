package ai.javaclaw.security.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Async
    public void log(AuthEventType eventType, String username, String remoteAddr, String requestUri, String detail) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO auth_audit_log (event_type, username, remote_addr, request_uri, detail, created_at) "
                            + "VALUES (:eventType, :username, :remoteAddr, :requestUri, :detail, :createdAt)",
                    new MapSqlParameterSource()
                            .addValue("eventType", eventType.name().toLowerCase())
                            .addValue("username", username)
                            .addValue("remoteAddr", remoteAddr)
                            .addValue("requestUri", requestUri)
                            .addValue("detail", detail)
                            .addValue("createdAt", OffsetDateTime.now(ZoneOffset.UTC)));
        } catch (Exception e) {
            log.warn("Failed to write auth audit event {}: {}", eventType, e.getMessage());
        }
    }
}
