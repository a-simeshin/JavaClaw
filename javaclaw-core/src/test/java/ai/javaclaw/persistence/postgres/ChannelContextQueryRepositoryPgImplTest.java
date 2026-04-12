package ai.javaclaw.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

/**
 * Integration test for {@link ChannelContextQueryRepositoryPgImpl} against a real Postgres
 * container (so the {@code ON CONFLICT ... DO UPDATE} clause is actually verified).
 */
@DataJdbcTest
@Testcontainers
@ActiveProfiles("test")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(ChannelContextQueryRepositoryPgImpl.class)
class ChannelContextQueryRepositoryPgImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    ChannelContextQueryRepositoryPgImpl repository;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.update("DELETE FROM conversation_channel_context", Map.of());
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
