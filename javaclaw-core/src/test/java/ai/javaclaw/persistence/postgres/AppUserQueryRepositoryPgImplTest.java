package ai.javaclaw.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJdbcTest
@Testcontainers
@ActiveProfiles("test")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(AppUserQueryRepositoryPgImpl.class)
class AppUserQueryRepositoryPgImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    AppUserQueryRepositoryPgImpl repository;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    private String userId;

    @BeforeEach
    void insertTestUser() {
        // Clean any prior test rows.
        jdbc.update("DELETE FROM users WHERE username = :u", Map.of("u", "pg-query-test"));
        userId = java.util.UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO users (id, username, password_hash, role, active, created_at, updated_at)
                VALUES (:id, 'pg-query-test', 'orig-hash', 'USER', true, now(), now())
                """,
                Map.of("id", userId));
    }

    @Test
    void deactivateFlipsActiveAndBumpsUpdatedAt() {
        int affected = repository.deactivate(userId);
        assertThat(affected).isEqualTo(1);

        Map<String, Object> row = jdbc.queryForMap("SELECT active FROM users WHERE id = :id", Map.of("id", userId));
        assertThat(row.get("active")).isEqualTo(false);
    }

    @Test
    void updatePasswordReplacesHash() {
        repository.updatePassword(userId, "new-hash");

        Map<String, Object> row =
                jdbc.queryForMap("SELECT password_hash FROM users WHERE id = :id", Map.of("id", userId));
        assertThat(row.get("password_hash")).isEqualTo("new-hash");
    }

    @Test
    void updateRoleReplacesRole() {
        repository.updateRole(userId, "ADMIN");

        Map<String, Object> row = jdbc.queryForMap("SELECT role FROM users WHERE id = :id", Map.of("id", userId));
        assertThat(row.get("role")).isEqualTo("ADMIN");
    }
}
