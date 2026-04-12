package ai.javaclaw.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.ai.memory.AppendableChatMemoryRepository;
import java.time.Instant;
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
 * Integration tests for {@link SpringDataChatMemoryRepository} against a real PostgreSQL container.
 *
 * <p>Schema is applied via Flyway: V1 (conversations), V2 (SPRING_AI_CHAT_MEMORY pre-V43),
 * V43 (add id PK, rename timestamp → created_at) — all from the test classpath.
 *
 * <p>{@code @BeforeEach} seeds conversations rows required to satisfy the FK constraint;
 * chat-memory entries are cleared before each test to ensure isolation.
 */
@DataJdbcTest
@Testcontainers
@ActiveProfiles("test")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(SpringDataChatMemoryRepository.class)
class ChatMemoryEntryRepositoryPostgresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static final String CONV_A = "conv-pg-a";
    private static final String CONV_B = "conv-pg-b";
    private static final String CONV_C = "conv-pg-c";

    @Autowired
    private AppendableChatMemoryRepository chatMemoryRepository;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void setUp() {
        namedJdbc.update("INSERT INTO conversations (id) VALUES (:id) ON CONFLICT DO NOTHING", Map.of("id", CONV_A));
        namedJdbc.update("INSERT INTO conversations (id) VALUES (:id) ON CONFLICT DO NOTHING", Map.of("id", CONV_B));
        namedJdbc.update("INSERT INTO conversations (id) VALUES (:id) ON CONFLICT DO NOTHING", Map.of("id", CONV_C));
        namedJdbc.update(
                "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id IN (:ids)",
                Map.of("ids", List.of(CONV_A, CONV_B, CONV_C)));
    }

    // ── Test 1: appendAll accumulates ────────────────────────────────────────

    @Test
    void appendAll_addsToExistingMessages() {
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("first")));
        chatMemoryRepository.appendAll(CONV_A, List.of(new AssistantMessage("second")));

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo("first");
        assertThat(result.get(1).getText()).isEqualTo("second");
    }

    // ── Test 2: order preserved within single appendAll ──────────────────────

    @Test
    void appendAll_multipleMessages_preservesCreatedAtOrder() {
        chatMemoryRepository.appendAll(
                CONV_A, List.of(new UserMessage("a"), new AssistantMessage("b"), new UserMessage("c")));

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);
        assertThat(result).extracting(Message::getText).containsExactly("a", "b", "c");
    }

    // ── Test 3: two batches accumulate ──────────────────────────────────────

    @Test
    void appendAll_twoBatches_allMessagesAccumulate() {
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("1"), new AssistantMessage("2")));
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("3"), new AssistantMessage("4")));

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);
        assertThat(result).hasSize(4);
        assertThat(result).extracting(Message::getText).containsExactly("1", "2", "3", "4");
    }

    // ── Test 4: saveAll replaces ──────────────────────────────────────────────

    @Test
    void saveAll_replacesExistingMessages() {
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("old1"), new UserMessage("old2")));

        chatMemoryRepository.saveAll(
                CONV_A, List.of(new UserMessage("new1"), new AssistantMessage("new2"), new UserMessage("new3")));

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Message::getText).containsExactly("new1", "new2", "new3");
    }

    // ── Test 5: saveAll empty clears ─────────────────────────────────────────

    @Test
    void saveAll_emptyList_clearsAllMessages() {
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("to be cleared")));

        chatMemoryRepository.saveAll(CONV_A, List.of());

        assertThat(chatMemoryRepository.findByConversationId(CONV_A)).isEmpty();
    }

    // ── Test 6: all four message types ───────────────────────────────────────

    @Test
    void findByConversationId_mapsAllFourTypes() {
        chatMemoryRepository.appendAll(
                CONV_A,
                List.of(
                        new UserMessage("user-text"),
                        new AssistantMessage("assistant-text"),
                        new SystemMessage("system-text"),
                        ToolResponseMessage.builder().responses(List.of()).build()));

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);
        assertThat(result).hasSize(4);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(result.get(2)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(3)).isInstanceOf(ToolResponseMessage.class);
    }

    // ── Test 7: null content round-trip ──────────────────────────────────────

    @Test
    void findByConversationId_nullContent_toolMessage() {
        chatMemoryRepository.appendAll(
                CONV_A,
                List.of(ToolResponseMessage.builder().responses(List.of()).build()));

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(ToolResponseMessage.class);
    }

    // ── Test 8: isolation between conversations ───────────────────────────────

    @Test
    void findByConversationId_isolationBetweenConversations() {
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("for-a")));
        chatMemoryRepository.appendAll(CONV_B, List.of(new UserMessage("for-b")));

        final List<Message> resultA = chatMemoryRepository.findByConversationId(CONV_A);
        final List<Message> resultB = chatMemoryRepository.findByConversationId(CONV_B);

        assertThat(resultA).hasSize(1).extracting(Message::getText).containsExactly("for-a");
        assertThat(resultB).hasSize(1).extracting(Message::getText).containsExactly("for-b");
    }

    // ── Test 9: delete only target ───────────────────────────────────────────

    @Test
    void deleteByConversationId_removesOnlyTargetConversation() {
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("msg-a")));
        chatMemoryRepository.appendAll(CONV_B, List.of(new UserMessage("msg-b")));

        chatMemoryRepository.deleteByConversationId(CONV_A);

        assertThat(chatMemoryRepository.findByConversationId(CONV_A)).isEmpty();
        assertThat(chatMemoryRepository.findByConversationId(CONV_B)).hasSize(1);
    }

    // ── Test 10: findConversationIds returns distinct IDs ─────────────────────

    @Test
    void findConversationIds_returnsDistinctIds() {
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("msg")));
        chatMemoryRepository.appendAll(CONV_B, List.of(new UserMessage("msg")));
        chatMemoryRepository.appendAll(CONV_C, List.of(new UserMessage("msg")));

        final List<String> ids = chatMemoryRepository.findConversationIds();

        assertThat(ids).contains(CONV_A, CONV_B, CONV_C);
    }

    // ── Test 11: deleted conv not in findConversationIds ─────────────────────

    @Test
    void findConversationIds_doesNotIncludeEmptyConversations() {
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("temp")));
        chatMemoryRepository.deleteByConversationId(CONV_A);

        final List<String> ids = chatMemoryRepository.findConversationIds();

        assertThat(ids).doesNotContain(CONV_A);
    }

    // ── Test 12: Instant round-trip (microsecond precision for PostgreSQL) ────

    @Test
    void instantRoundTrip_preservesPrecision() {
        // PostgreSQL TIMESTAMPTZ has microsecond precision; truncate to micros.
        final Instant expected = Instant.now().truncatedTo(ChronoUnit.MICROS);
        // Write via direct JdbcTemplate insert to bypass appendAll's Instant.ofEpochSecond base
        namedJdbc.update(
                "INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, created_at) "
                        + "VALUES (:conv, :content, :type, :ts)",
                Map.of("conv", CONV_A, "content", "ts-test", "type", "USER", "ts", expected));

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(0).getText()).isEqualTo("ts-test");
    }
}
