package ai.javaclaw.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AgentQuotaQueryRepositorySqliteImplTest {

    private static final String USER_ID = "quota-sqlite-user";

    private AgentQuotaQueryRepositorySqliteImpl repository;
    private NamedParameterJdbcTemplate jdbc;
    private SingleConnectionDataSource dataSource;

    @BeforeEach
    void setUp() {
        this.dataSource = SqliteTestSupport.newInMemoryDataSource();
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        CREATE TABLE agent_quotas (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            user_id     TEXT NOT NULL,
                            daily_limit INTEGER NOT NULL DEFAULT 100,
                            daily_used  INTEGER NOT NULL DEFAULT 0,
                            reset_date  TEXT NOT NULL,
                            created_at  TEXT NOT NULL DEFAULT (datetime('now')),
                            updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
                        )
                        """);
        jdbc.update(
                """
                INSERT INTO agent_quotas (user_id, daily_limit, daily_used, reset_date)
                VALUES (:uid, 100, 0, :d)
                """,
                Map.of("uid", USER_ID, "d", LocalDate.now().minusDays(1).toString()));
        this.repository = new AgentQuotaQueryRepositorySqliteImpl(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void incrementDailyUsedAddsOne() {
        repository.incrementDailyUsed(USER_ID);
        repository.incrementDailyUsed(USER_ID);

        Number used = jdbc.queryForObject(
                "SELECT daily_used FROM agent_quotas WHERE user_id = :uid", Map.of("uid", USER_ID), Number.class);
        assertThat(used).isNotNull();
        assertThat(used.intValue()).isEqualTo(2);
    }

    @Test
    void resetDailyZeroesUsageAndStampsToday() {
        repository.incrementDailyUsed(USER_ID);
        repository.resetDaily(USER_ID);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT daily_used, reset_date FROM agent_quotas WHERE user_id = :uid", Map.of("uid", USER_ID));
        assertThat(((Number) row.get("daily_used")).intValue()).isZero();
        assertThat(row.get("reset_date").toString()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void updateDailyLimitReplacesLimit() {
        repository.updateDailyLimit(USER_ID, 250);

        Number limit = jdbc.queryForObject(
                "SELECT daily_limit FROM agent_quotas WHERE user_id = :uid", Map.of("uid", USER_ID), Number.class);
        assertThat(limit).isNotNull();
        assertThat(limit.intValue()).isEqualTo(250);
    }
}
