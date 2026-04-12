package ai.javaclaw.agent.memory.adapter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.persistence.dialect.sqlite.SqliteJdbcDialect;
import java.sql.JDBCType;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.core.mapping.JdbcValue;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Integration tests for {@link JdbcChatMemory} against an in-memory SQLite database.
 *
 * <p>Follows the pattern of the project's {@code SqliteRepositoryIntegrationTest}: a pure
 * {@link SpringJUnitConfig} bootstrap with a static inner {@link TestContext} that creates a
 * {@link SingleConnectionDataSource}, runs Flyway migrations, and wires all Spring Data JDBC
 * beans manually without relying on Spring Boot auto-configuration.
 *
 * <p>Schema is applied from this module's main classpath: a single squashed V2 creates
 * {@code SPRING_AI_CHAT_MEMORY} in its final SQLite shape (surrogate INTEGER PK
 * AUTOINCREMENT, nullable content, ISO-8601 TEXT created_at, CHECK on type). The foreign
 * key from conversation_id to conversations(id) is owned by javaclaw-core V5 and is
 * intentionally absent from the memory module's test classpath.
 *
 * <p>Instant write/read uses inline converter classes ({@link InstantToJdbcValueConverter} and
 * {@link StringToInstantConverter}) that replicate the production behaviour of
 * {@code SqliteJdbcConfiguration} without creating a circular module dependency.
 */
@SpringJUnitConfig
@SuppressWarnings("deprecation")
@ContextConfiguration(classes = JdbcChatMemorySqliteIT.TestContext.class)
@TestPropertySource(properties = "javaclaw.persistence.dialect=sqlite")
class JdbcChatMemorySqliteIT {

    private static final String CONV_A = "conv-sqlite-a";
    private static final String CONV_B = "conv-sqlite-b";
    private static final String CONV_C = "conv-sqlite-c";

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id IN (?, ?, ?)", CONV_A, CONV_B, CONV_C);
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
        chatMemory.appendAll(CONV_A, List.of(new UserMessage("old")));

        chatMemory.saveAll(CONV_A, List.of(new UserMessage("new1"), new AssistantMessage("new2")));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Message::getText).containsExactly("new1", "new2");
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

    // ── Test 10: findConversationIds ─────────────────────────────────────────

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

    // ── Test 12: Instant ISO-8601 round-trip in SQLite ───────────────────────

    @Test
    void instantRoundTrip_preservesPrecision() {
        // ISO_INSTANT preserves nanoseconds; SQLite stores as ISO-8601 TEXT.
        final Instant expected = Instant.ofEpochSecond(1_700_000_000L, 123_456_789);
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
                        DateTimeFormatter.ISO_INSTANT.format(expected)));

        final List<Message> result = chatMemory.findByConversationId(CONV_A);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getText()).isEqualTo("ts-test");
    }

    // ── Inline Instant ↔ ISO-8601 TEXT converters ────────────────────────────

    /**
     * Writes {@link Instant} as ISO-8601 TEXT via {@link JdbcValue} to bypass Spring Data JDBC's
     * default {@code Instant → Timestamp} mapping which breaks on SQLite TEXT columns.
     */
    @WritingConverter
    static final class InstantToJdbcValueConverter implements Converter<Instant, JdbcValue> {

        private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

        @Override
        public JdbcValue convert(final Instant source) {
            final String iso = source == null ? null : ISO.format(source);
            return JdbcValue.of(iso, JDBCType.VARCHAR);
        }
    }

    /** Reads ISO-8601 TEXT from SQLite back to {@link Instant}. */
    @ReadingConverter
    static final class StringToInstantConverter implements Converter<String, Instant> {

        private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

        @Override
        public Instant convert(final String source) {
            return (source == null || source.isBlank()) ? null : Instant.from(ISO.parse(source));
        }
    }

    // ── Test Spring context ───────────────────────────────────────────────────

    /**
     * Self-contained test context.
     *
     * <p>Creates an in-memory SQLite DataSource, runs Flyway migrations (V1 + V2 from test
     * resources + V43 from main resources), wires Spring Data JDBC with the shared
     * {@link SqliteJdbcDialect} (from the {@code javaclaw-persistence-sqlite-dialect} module)
     * and inline Instant converters, and exposes {@link JdbcChatMemory} as a bean.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableJdbcRepositories(basePackages = "ai.javaclaw.agent.memory.adapter.jdbc")
    @EnableTransactionManagement
    static class TestContext extends AbstractJdbcConfiguration {

        @Override
        public @NonNull JdbcDialect jdbcDialect(final @NonNull NamedParameterJdbcOperations operations) {
            return SqliteJdbcDialect.INSTANCE;
        }

        @Override
        protected @NonNull List<?> userConverters() {
            return List.of(new InstantToJdbcValueConverter(), new StringToInstantConverter());
        }

        @Bean
        DataSource dataSource() {
            final SingleConnectionDataSource ds = new SingleConnectionDataSource();
            ds.setDriverClassName("org.sqlite.JDBC");
            ds.setUrl("jdbc:sqlite::memory:");
            ds.setSuppressClose(true);
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration/sqlite")
                    .load()
                    .migrate();
            return ds;
        }

        @Bean
        PlatformTransactionManager transactionManager(final DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(final DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate(final DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean
        JdbcChatMemory jdbcChatMemory(final ChatMemoryEntryJdbcRepository repo) {
            return new JdbcChatMemory(repo);
        }
    }
}
