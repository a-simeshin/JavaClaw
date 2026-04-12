package ai.javaclaw.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.persistence.api.ConversationQueryRepository.ChatMessageRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
@Import(ConversationQueryRepositoryPgImpl.class)
class ConversationQueryRepositoryPgImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    ConversationQueryRepositoryPgImpl repository;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    private static final String CONV_ID = "conv-pg-test";

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = :id", Map.of("id", CONV_ID));
        jdbc.update("DELETE FROM conversations WHERE id = :id", Map.of("id", CONV_ID));

        // Insert a conversation with NULL title so touchTitleIfMissing can set it.
        jdbc.update(
                "INSERT INTO conversations (id, title, created_at, updated_at) VALUES (:id, NULL, now(), now())",
                Map.of("id", CONV_ID));
    }

    @Test
    void touchTitleIfMissingSetsTitleAndBumpsUpdatedAt() {
        int affected = repository.touchTitleIfMissing(CONV_ID, "Hello world");
        assertThat(affected).isEqualTo(1);

        Map<String, Object> row =
                jdbc.queryForMap("SELECT title FROM conversations WHERE id = :id", Map.of("id", CONV_ID));
        assertThat(row.get("title")).isEqualTo("Hello world");
    }

    @Test
    void touchTitleIfMissingDoesNotOverwriteExistingTitle() {
        jdbc.update("UPDATE conversations SET title = :t WHERE id = :id", Map.of("t", "Original", "id", CONV_ID));

        repository.touchTitleIfMissing(CONV_ID, "Replacement");

        Map<String, Object> row =
                jdbc.queryForMap("SELECT title FROM conversations WHERE id = :id", Map.of("id", CONV_ID));
        assertThat(row.get("title")).isEqualTo("Original");
    }

    @Test
    void findMessagesOrderedReturnsStableOldestFirstOrder() {
        insertMessage(CONV_ID, "first", "USER", Instant.parse("2025-01-01T10:00:00Z"));
        insertMessage(CONV_ID, "second", "ASSISTANT", Instant.parse("2025-01-01T10:00:01Z"));
        insertMessage(CONV_ID, "third", "USER", Instant.parse("2025-01-01T10:00:02Z"));

        List<ChatMessageRow> rows = repository.findMessagesOrdered(CONV_ID, 0, 10);
        assertThat(rows).extracting(ChatMessageRow::content).containsExactly("first", "second", "third");
    }

    @Test
    void findMessagesOrderedAppliesOffsetAndLimit() {
        for (int i = 0; i < 5; i++) {
            insertMessage(CONV_ID, "msg-" + i, "USER", Instant.parse("2025-01-01T10:00:0" + i + "Z"));
        }

        List<ChatMessageRow> rows = repository.findMessagesOrdered(CONV_ID, 1, 2);
        assertThat(rows).extracting(ChatMessageRow::content).containsExactly("msg-1", "msg-2");
    }

    private void insertMessage(final String convId, final String content, final String type, final Instant when) {
        final Map<String, Object> params = new HashMap<>();
        params.put("id", convId);
        params.put("content", content);
        params.put("type", type);
        params.put("ts", Timestamp.from(when));
        jdbc.update(
                """
                INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, created_at)
                VALUES (:id, :content, :type, :ts)
                """,
                params);
    }
}
