package ai.javaclaw.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
@Import(AgentQuotaQueryRepositoryPgImpl.class)
class AgentQuotaQueryRepositoryPgImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    AgentQuotaQueryRepositoryPgImpl repository;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    private String userId;

    @BeforeEach
    void seed() {
        userId = java.util.UUID.randomUUID().toString();
        // Insert a user because agent_quotas.user_id references users.id
        jdbc.update(
                """
                INSERT INTO users (id, username, password_hash, role, active, created_at, updated_at)
                VALUES (:id, 'quota-pg-' || :id, 'hash', 'USER', true, now(), now())
                """,
                Map.of("id", userId));
        jdbc.update(
                """
                INSERT INTO agent_quotas (user_id, daily_limit, daily_used, reset_date, created_at, updated_at)
                VALUES (:uid, 100, 0, :today, now(), now())
                """,
                Map.of(
                        "uid",
                        userId,
                        "today",
                        java.sql.Date.valueOf(LocalDate.now().minusDays(1))));
    }

    @Test
    void incrementDailyUsedAddsOne() {
        repository.incrementDailyUsed(userId);
        repository.incrementDailyUsed(userId);

        Integer used = jdbc.queryForObject(
                "SELECT daily_used FROM agent_quotas WHERE user_id = :uid", Map.of("uid", userId), Integer.class);
        assertThat(used).isEqualTo(2);
    }

    @Test
    void resetDailyZeroesUsageAndStampsTodaysDate() {
        repository.incrementDailyUsed(userId);
        repository.resetDaily(userId);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT daily_used, reset_date FROM agent_quotas WHERE user_id = :uid", Map.of("uid", userId));
        assertThat(row.get("daily_used")).isEqualTo(0);
        assertThat(((java.sql.Date) row.get("reset_date")).toLocalDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void updateDailyLimitReplacesLimit() {
        repository.updateDailyLimit(userId, 250);

        Integer limit = jdbc.queryForObject(
                "SELECT daily_limit FROM agent_quotas WHERE user_id = :uid", Map.of("uid", userId), Integer.class);
        assertThat(limit).isEqualTo(250);
    }
}
