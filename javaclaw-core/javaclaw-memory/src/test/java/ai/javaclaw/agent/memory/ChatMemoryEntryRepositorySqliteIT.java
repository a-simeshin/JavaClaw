package ai.javaclaw.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.ai.memory.AppendableChatMemoryRepository;
import java.sql.JDBCType;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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
import org.springframework.data.jdbc.core.dialect.JdbcArrayColumns;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.core.mapping.JdbcValue;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.IdGeneration;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
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
 * Integration tests for {@link SpringDataChatMemoryRepository} against an in-memory SQLite database.
 *
 * <p>Follows the pattern of the project's {@code SqliteRepositoryIntegrationTest}: a pure
 * {@link SpringJUnitConfig} bootstrap with a static inner {@link TestContext} that creates a
 * {@link SingleConnectionDataSource}, runs Flyway migrations, and wires all Spring Data JDBC
 * beans manually without relying on Spring Boot auto-configuration.
 *
 * <p>Schema applied: V1 (conversations), V2 (SPRING_AI_CHAT_MEMORY pre-V43), V43 (add id PK,
 * rename timestamp → created_at TEXT ISO-8601) — all from the test classpath.
 *
 * <p>Instant write/read uses inline converter classes ({@link InstantToJdbcValueConverter} and
 * {@link StringToInstantConverter}) that replicate the production behaviour of
 * {@code SqliteJdbcConfiguration} without creating a circular module dependency.
 */
@SpringJUnitConfig
@ContextConfiguration(classes = ChatMemoryEntryRepositorySqliteIT.TestContext.class)
@TestPropertySource(properties = "javaclaw.persistence.dialect=sqlite")
class ChatMemoryEntryRepositorySqliteIT {

    private static final String CONV_A = "conv-sqlite-a";
    private static final String CONV_B = "conv-sqlite-b";
    private static final String CONV_C = "conv-sqlite-c";

    @Autowired
    private AppendableChatMemoryRepository chatMemoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT OR IGNORE INTO conversations (id) VALUES (?)", CONV_A);
        jdbcTemplate.update("INSERT OR IGNORE INTO conversations (id) VALUES (?)", CONV_B);
        jdbcTemplate.update("INSERT OR IGNORE INTO conversations (id) VALUES (?)", CONV_C);
        jdbcTemplate.update(
                "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id IN (?, ?, ?)", CONV_A, CONV_B, CONV_C);
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
        chatMemoryRepository.appendAll(CONV_A, List.of(new UserMessage("old")));

        chatMemoryRepository.saveAll(CONV_A, List.of(new UserMessage("new1"), new AssistantMessage("new2")));

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Message::getText).containsExactly("new1", "new2");
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

    // ── Test 10: findConversationIds ─────────────────────────────────────────

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

        final List<Message> result = chatMemoryRepository.findByConversationId(CONV_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo("ts-test");
    }

    // ── Inline SQLite-compatible dialect ─────────────────────────────────────

    /** Minimal ANSI-based JdbcDialect for SQLite IT tests (no LIMIT clause needed here). */
    static final class TestSqliteDialect extends AnsiDialect implements JdbcDialect {

        static final TestSqliteDialect INSTANCE = new TestSqliteDialect();

        private TestSqliteDialect() {}

        @Override
        public JdbcArrayColumns getArraySupport() {
            return JdbcArrayColumns.Unsupported.INSTANCE;
        }

        @Override
        public IdentifierProcessing getIdentifierProcessing() {
            return IdentifierProcessing.ANSI;
        }

        @Override
        public IdGeneration getIdGeneration() {
            return IdGeneration.DEFAULT;
        }
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
     * resources + V43 from main resources), wires Spring Data JDBC with the inline SQLite dialect
     * and Instant converters, and exposes {@link SpringDataChatMemoryRepository} as a bean.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableJdbcRepositories(basePackages = "ai.javaclaw.agent.memory")
    @EnableTransactionManagement
    static class TestContext extends AbstractJdbcConfiguration {

        @Override
        public JdbcDialect jdbcDialect(final NamedParameterJdbcOperations operations) {
            return TestSqliteDialect.INSTANCE;
        }

        @Override
        protected List<?> userConverters() {
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
        SpringDataChatMemoryRepository springDataChatMemoryRepository(
                final ChatMemoryEntryRepository repo, final NamedParameterJdbcTemplate namedJdbc) {
            return new SpringDataChatMemoryRepository(repo, namedJdbc);
        }
    }
}
