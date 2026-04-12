package ai.javaclaw.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * In-memory SQLite unit test for {@link ChannelContextQueryRepositorySqliteImpl}.
 *
 * <p>Creates the minimal schema required by the query in {@code @BeforeEach}, then
 * verifies the {@code ON CONFLICT ... DO UPDATE} upsert behaviour against a real
 * SQLite engine (sqlite-jdbc is on the test classpath).
 */
class ChannelContextQueryRepositorySqliteImplTest {

    private ChannelContextQueryRepositorySqliteImpl repository;
    private NamedParameterJdbcTemplate jdbc;
    private SingleConnectionDataSource dataSource;

    @BeforeEach
    void setUp() {
        this.dataSource = SqliteTestSupport.newInMemoryDataSource();
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        CREATE TABLE conversation_channel_context (
                            conversation_id TEXT PRIMARY KEY,
                            channel_name    TEXT NOT NULL,
                            routing_data    TEXT,
                            updated_at      TEXT NOT NULL
                        )
                        """);
        this.repository = new ChannelContextQueryRepositorySqliteImpl(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void upsertInsertsNewRow() {
        repository.upsert("conv-1", "TelegramChannel", "{\"chatId\":\"1\"}", Instant.parse("2025-01-01T00:00:00Z"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT channel_name, routing_data FROM conversation_channel_context WHERE conversation_id = :id",
                Map.of("id", "conv-1"));
        assertThat(row.get("channel_name")).isEqualTo("TelegramChannel");
        assertThat(row.get("routing_data")).isEqualTo("{\"chatId\":\"1\"}");
    }

    @Test
    void upsertUpdatesExistingRow() {
        repository.upsert("conv-1", "TelegramChannel", "{\"chatId\":\"1\"}", Instant.parse("2025-01-01T00:00:00Z"));
        repository.upsert("conv-1", "DiscordChannel", "{\"channelId\":\"abc\"}", Instant.parse("2025-02-02T00:00:00Z"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT channel_name, routing_data FROM conversation_channel_context WHERE conversation_id = :id",
                Map.of("id", "conv-1"));
        assertThat(row.get("channel_name")).isEqualTo("DiscordChannel");
        assertThat(row.get("routing_data")).isEqualTo("{\"channelId\":\"abc\"}");

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation_channel_context WHERE conversation_id = :id",
                Map.of("id", "conv-1"),
                Long.class);
        assertThat(count).isEqualTo(1L);
    }
}
