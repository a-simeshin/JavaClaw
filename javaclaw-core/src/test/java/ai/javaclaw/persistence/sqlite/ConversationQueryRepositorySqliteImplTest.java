package ai.javaclaw.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.persistence.api.ConversationQueryRepository.ChatMessageRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class ConversationQueryRepositorySqliteImplTest {

    private static final String CONV_ID = "conv-sqlite-test";

    private ConversationQueryRepositorySqliteImpl repository;
    private NamedParameterJdbcTemplate jdbc;
    private SingleConnectionDataSource dataSource;

    @BeforeEach
    void setUp() {
        this.dataSource = SqliteTestSupport.newInMemoryDataSource();
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        CREATE TABLE conversations (
                            id         TEXT PRIMARY KEY,
                            user_id    TEXT,
                            title      TEXT,
                            created_at TEXT NOT NULL,
                            updated_at TEXT NOT NULL
                        )
                        """);
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        CREATE TABLE SPRING_AI_CHAT_MEMORY (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            conversation_id TEXT NOT NULL,
                            content         TEXT,
                            type            TEXT NOT NULL,
                            created_at      TEXT NOT NULL
                        )
                        """);
        jdbc.update(
                "INSERT INTO conversations (id, title, created_at, updated_at) VALUES (:id, NULL, datetime('now'), datetime('now'))",
                Map.of("id", CONV_ID));
        this.repository = new ConversationQueryRepositorySqliteImpl(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void touchTitleIfMissingSetsTitleWhenNull() {
        int affected = repository.touchTitleIfMissing(CONV_ID, "Hello SQLite");
        assertThat(affected).isEqualTo(1);

        Map<String, Object> row =
                jdbc.queryForMap("SELECT title FROM conversations WHERE id = :id", Map.of("id", CONV_ID));
        assertThat(row.get("title")).isEqualTo("Hello SQLite");
    }

    @Test
    void touchTitleIfMissingDoesNotOverwriteExistingTitle() {
        jdbc.update("UPDATE conversations SET title = :t WHERE id = :id", Map.of("t", "Existing", "id", CONV_ID));

        repository.touchTitleIfMissing(CONV_ID, "Replacement");

        Map<String, Object> row =
                jdbc.queryForMap("SELECT title FROM conversations WHERE id = :id", Map.of("id", CONV_ID));
        assertThat(row.get("title")).isEqualTo("Existing");
    }

    @Test
    void findMessagesOrderedReturnsOldestFirst() {
        insertMessage(CONV_ID, "first", "USER", "2025-01-01T10:00:00Z");
        insertMessage(CONV_ID, "second", "ASSISTANT", "2025-01-01T10:00:01Z");
        insertMessage(CONV_ID, "third", "USER", "2025-01-01T10:00:02Z");

        List<ChatMessageRow> rows = repository.findMessagesOrdered(CONV_ID, 0, 10);
        assertThat(rows).extracting(ChatMessageRow::content).containsExactly("first", "second", "third");
    }

    @Test
    void findMessagesOrderedAppliesPagination() {
        for (int i = 0; i < 5; i++) {
            insertMessage(CONV_ID, "msg-" + i, "USER", "2025-01-01T10:00:0" + i + "Z");
        }

        List<ChatMessageRow> rows = repository.findMessagesOrdered(CONV_ID, 1, 2);
        assertThat(rows).extracting(ChatMessageRow::content).containsExactly("msg-1", "msg-2");
    }

    @Test
    void findMessagesOrderedUsesRowidAsStableTiebreaker() {
        // Two rows with identical timestamps — second insert should come after the first
        // thanks to ORDER BY rowid ASC.
        insertMessage(CONV_ID, "a", "USER", "2025-01-01T10:00:00Z");
        insertMessage(CONV_ID, "b", "USER", "2025-01-01T10:00:00Z");

        List<ChatMessageRow> rows = repository.findMessagesOrdered(CONV_ID, 0, 10);
        assertThat(rows).extracting(ChatMessageRow::content).containsExactly("a", "b");
    }

    private void insertMessage(final String convId, final String content, final String type, final String iso) {
        final MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", convId)
                .addValue("content", content)
                .addValue("type", type)
                .addValue("ts", iso);
        jdbc.update(
                """
                INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, created_at)
                VALUES (:id, :content, :type, :ts)
                """,
                params);
    }
}
