package ai.javaclaw.agent.memory.adapter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.memory.ChatMemory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
 * Integration tests for {@link JdbcChatMemory} against a real PostgreSQL container.
 *
 * <p>Schema is applied via Flyway from this module's main classpath: a single squashed
 * V2 creates {@code SPRING_AI_CHAT_MEMORY} in its final shape (surrogate BIGINT IDENTITY PK,
 * nullable content, TIMESTAMPTZ created_at). The foreign key from conversation_id to
 * conversations(id) is owned by javaclaw-core V5 and is intentionally absent from the memory
 * module's test classpath — these tests cover the adapter contract in isolation, without
 * a conversations table.
 *
 * <p>{@code @BeforeEach} clears chat-memory rows for the three test conversation ids to
 * guarantee per-test isolation.
 */
@DataJdbcTest
@Testcontainers
@ActiveProfiles("test")
@SuppressWarnings("deprecation")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(JdbcChatMemory.class)
class JdbcChatMemoryPostgresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static final String CONV_A = "conv-pg-a";
    private static final String CONV_B = "conv-pg-b";
    private static final String CONV_C = "conv-pg-c";

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void setUp() {
        namedJdbc.update(
                "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id IN (:ids)",
                Map.of("ids", List.of(CONV_A, CONV_B, CONV_C)));
    }

    // ── Test 1: appendAll accumulates ────────────────────────────────────────

    @Test
    void appendAll_addsToExistingMessages() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("first")));
        chatMemory.appendAll(CONV_A, List.of(new AssistantMessage("second")));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo("first");
        assertThat(result.get(1).getText()).isEqualTo("second");
    }

    // ── Test 2: order preserved within single appendAll ──────────────────────

    @Test
    void appendAll_multipleMessages_preservesCreatedAtOrder() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("a"), new AssistantMessage("b"), new UserMessage("c")));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);
        assertThat(result).extracting(Message::getText).containsExactly("a", "b", "c");
    }

    // ── Test 3: two batches accumulate ──────────────────────────────────────

    @Test
    void appendAll_twoBatches_allMessagesAccumulate() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("1"), new AssistantMessage("2")));
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("3"), new AssistantMessage("4")));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);
        assertThat(result).hasSize(4);
        assertThat(result).extracting(Message::getText).containsExactly("1", "2", "3", "4");
    }

    // ── Test 4: saveAll replaces ──────────────────────────────────────────────

    @Test
    @SuppressWarnings("deprecation") // pins the deprecated saveAll contract on purpose
    void saveAll_replacesExistingMessages() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("old1"), new UserMessage("old2")));

        chatMemory.saveAll(
                CONV_A, List.of(new UserMessage("new1"), new AssistantMessage("new2"), new UserMessage("new3")));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Message::getText).containsExactly("new1", "new2", "new3");
    }

    // ── Test 5: saveAll empty clears ─────────────────────────────────────────

    @Test
    @SuppressWarnings("deprecation") // pins the deprecated saveAll contract on purpose
    void saveAll_emptyList_clearsAllMessages() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("to be cleared")));

        chatMemory.saveAll(CONV_A, List.of());

        assertThat(chatMemory.findByConversationId(CONV_A)).isEmpty();
    }

    // ── Test 6: all four message types ───────────────────────────────────────

    @Test
    void findByConversationId_mapsAllFourTypes() {
        chatMemory.appendAll(
                CONV_A,
                List.of(
                        new UserMessage("user-text"),
                        new AssistantMessage("assistant-text"),
                        new SystemMessage("system-text"),
                        ToolResponseMessage.builder().responses(List.of()).build()));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);
        assertThat(result).hasSize(4);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(result.get(2)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(3)).isInstanceOf(ToolResponseMessage.class);
    }

    // ── Test 7: null content round-trip ──────────────────────────────────────

    @Test
    void findByConversationId_nullContent_toolMessage() {
        chatMemory.appendAll(
                CONV_A,
                List.of(ToolResponseMessage.builder().responses(List.of()).build()));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isInstanceOf(ToolResponseMessage.class);
    }

    // ── Test 8: isolation between conversations ───────────────────────────────

    @Test
    void findByConversationId_isolationBetweenConversations() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("for-a")));
        chatMemory.appendAll(CONV_B, List.of(new UserMessage("for-b")));

        final List<Message> resultA = chatMemory.findByConversationId(CONV_A);
        final List<Message> resultB = chatMemory.findByConversationId(CONV_B);

        assertThat(resultA).hasSize(1).extracting(Message::getText).containsExactly("for-a");
        assertThat(resultB).hasSize(1).extracting(Message::getText).containsExactly("for-b");
    }

    // ── Test 9: delete only target ───────────────────────────────────────────

    @Test
    void deleteByConversationId_removesOnlyTargetConversation() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("msg-a")));
        chatMemory.appendAll(CONV_B, List.of(new UserMessage("msg-b")));

        chatMemory.deleteByConversationId(CONV_A);

        assertThat(chatMemory.findByConversationId(CONV_A)).isEmpty();
        assertThat(chatMemory.findByConversationId(CONV_B)).hasSize(1);
    }

    // ── Test 10: findConversationIds returns distinct IDs ─────────────────────

    @Test
    void findConversationIds_returnsDistinctIds() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("msg")));
        chatMemory.appendAll(CONV_B, List.of(new UserMessage("msg")));
        chatMemory.appendAll(CONV_C, List.of(new UserMessage("msg")));

        final List<String> ids = chatMemory.findConversationIds();

        assertThat(ids).contains(CONV_A, CONV_B, CONV_C);
    }

    // ── Test 11: deleted conv not in findConversationIds ─────────────────────

    @Test
    void findConversationIds_doesNotIncludeEmptyConversations() {
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("temp")));
        chatMemory.deleteByConversationId(CONV_A);

        final List<String> ids = chatMemory.findConversationIds();

        assertThat(ids).doesNotContain(CONV_A);
    }

    // ── Test 12: Instant round-trip (microsecond precision for PostgreSQL) ────

    @Test
    void instantRoundTrip_preservesPrecision() {
        // PostgreSQL TIMESTAMPTZ has microsecond precision; truncate to micros.
        // pgjdbc cannot infer the SQL type for a raw Instant parameter binding
        // (Spring JDBC calls setObject without an explicit type), so we wrap as
        // OffsetDateTime at UTC — pgjdbc maps that to TIMESTAMPTZ natively. Production
        // code goes through Spring Data JDBC's JdbcAggregateTemplate which has its own
        // Instant → Timestamp converter and does not hit this code path.
        final Instant expected = Instant.now().truncatedTo(ChronoUnit.MICROS);
        namedJdbc.update(
                "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, created_at) "
                        + "VALUES (:conv, :content, :type, :ts)",
                Map.of(
                        "conv",
                        CONV_A,
                        "content",
                        "ts-test",
                        "type",
                        "USER",
                        "ts",
                        OffsetDateTime.ofInstant(expected, ZoneOffset.UTC)));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isInstanceOf(UserMessage.class);
        assertThat(result.getFirst().getText()).isEqualTo("ts-test");
    }
}
