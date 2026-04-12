package ai.javaclaw.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * E2E migration test: applies real Flyway classpath migrations V1-V10
 * against a pristine PostgreSQL 17 container. No Spring context — raw Flyway + JDBC.
 *
 * <p>Two separate containers ensure complete isolation between tests (avoids search_path
 * and schema-qualification issues with the uppercase SPRING_AI_CHAT_MEMORY table).
 */
@Testcontainers
class DbSchemaV1FullMigrationE2ETest {

    /** Container for fullMigration test — fresh public schema. */
    @Container
    static PostgreSQLContainer<?> pgFull = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Container for seededUsers test — fresh public schema. */
    @Container
    static PostgreSQLContainer<?> pgSeed = new PostgreSQLContainer<>("postgres:17-alpine");

    // -----------------------------------------------------------------------
    // Helper — build Flyway against a given container (classpath migrations only)
    // -----------------------------------------------------------------------

    private Flyway buildFlyway(PostgreSQLContainer<?> pg) {
        return Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration/postgresql")
                .load();
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void fullMigration_V1toV10_onPristinePostgres() throws Exception {
        Flyway flyway = buildFlyway(pgFull);
        var result = flyway.migrate();

        assertThat(result.success).isTrue();

        // flyway_schema_history must contain exactly versions 1-13
        List<Integer> versions = new ArrayList<>();
        try (Connection c =
                        DriverManager.getConnection(pgFull.getJdbcUrl(), pgFull.getUsername(), pgFull.getPassword());
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT version FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rs.next()) {
                versions.add(rs.getInt(1));
            }
        }
        // V2 is owned by javaclaw-memory (squashed final schema); V3 and V43 no longer exist
        // after the squash (content null-ability and the id/created_at columns were folded into V2).
        assertThat(versions)
                .containsExactly(
                        1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                        30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42);
    }

    @Test
    void seededUsers_matchExpectedDefaults() throws Exception {
        Flyway flyway = buildFlyway(pgSeed);
        flyway.migrate();

        try (Connection c =
                        DriverManager.getConnection(pgSeed.getJdbcUrl(), pgSeed.getUsername(), pgSeed.getPassword());
                Statement s = c.createStatement();
                ResultSet rs =
                        s.executeQuery("SELECT username, role, active, password_hash FROM users ORDER BY username")) {

            List<String> usernames = new ArrayList<>();
            while (rs.next()) {
                String username = rs.getString("username");
                String role = rs.getString("role");
                boolean active = rs.getBoolean("active");
                String passwordHash = rs.getString("password_hash");

                usernames.add(username);

                if ("admin".equals(username)) {
                    assertThat(role).isEqualTo("ADMIN");
                    assertThat(active).isTrue();
                    assertThat(passwordHash).isEqualTo("{noop}admin");
                } else if ("user".equals(username)) {
                    assertThat(role).isEqualTo("USER");
                    assertThat(active).isTrue();
                    assertThat(passwordHash).isEqualTo("{noop}user");
                }
            }

            assertThat(usernames).contains("admin", "user");
        }
    }
}
