package ai.javaclaw.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AppUserQueryRepositorySqliteImplTest {

    private static final String USER_ID = "user-sqlite-test";

    private AppUserQueryRepositorySqliteImpl repository;
    private NamedParameterJdbcTemplate jdbc;
    private SingleConnectionDataSource dataSource;

    @BeforeEach
    void setUp() {
        this.dataSource = SqliteTestSupport.newInMemoryDataSource();
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        CREATE TABLE users (
                            id            TEXT PRIMARY KEY,
                            username      TEXT NOT NULL,
                            password_hash TEXT,
                            role          TEXT NOT NULL,
                            active        INTEGER NOT NULL DEFAULT 1,
                            created_at    TEXT NOT NULL,
                            updated_at    TEXT NOT NULL
                        )
                        """);
        jdbc.update(
                """
                INSERT INTO users (id, username, password_hash, role, active, created_at, updated_at)
                VALUES (:id, 'sqlite-user', 'orig-hash', 'USER', 1, datetime('now'), datetime('now'))
                """,
                Map.of("id", USER_ID));
        this.repository = new AppUserQueryRepositorySqliteImpl(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void deactivateFlipsActiveToZero() {
        int affected = repository.deactivate(USER_ID);
        assertThat(affected).isEqualTo(1);

        Map<String, Object> row = jdbc.queryForMap("SELECT active FROM users WHERE id = :id", Map.of("id", USER_ID));
        // SQLite stores booleans as integers; 0 = false.
        assertThat(((Number) row.get("active")).intValue()).isZero();
    }

    @Test
    void updatePasswordReplacesHash() {
        repository.updatePassword(USER_ID, "new-sqlite-hash");

        Map<String, Object> row =
                jdbc.queryForMap("SELECT password_hash FROM users WHERE id = :id", Map.of("id", USER_ID));
        assertThat(row.get("password_hash")).isEqualTo("new-sqlite-hash");
    }

    @Test
    void updateRoleReplacesRole() {
        repository.updateRole(USER_ID, "ADMIN");

        Map<String, Object> row = jdbc.queryForMap("SELECT role FROM users WHERE id = :id", Map.of("id", USER_ID));
        assertThat(row.get("role")).isEqualTo("ADMIN");
    }
}
