package ai.javaclaw.agent.memory.autoconfigure;

import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.agent.memory.adapter.jdbc.ChatMemoryEntryJdbcRepository;
import ai.javaclaw.agent.memory.adapter.jdbc.JdbcChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.RowMapper;

/**
 * Spring Boot auto-configuration for the {@code javaclaw-memory} module.
 *
 * <p>Registers the default {@link ChatMemory} implementation backed by Spring Data JDBC
 * ({@link JdbcChatMemory}) whenever Spring AI message types and Spring JDBC are on the
 * classpath, and no consumer-defined {@code ChatMemory} bean already exists.
 *
 * <h2>Discovery</h2>
 * Discovered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so the module is bootstrappable as a self-contained unit — it does not rely on the
 * consumer application's {@code @ComponentScan} reaching
 * {@code ai.javaclaw.agent.memory.*}.
 *
 * <h2>Override point</h2>
 * Declare your own {@link ChatMemory} bean in the consumer context and this
 * auto-configuration steps aside (per {@link ConditionalOnMissingBean}). This is the
 * idiomatic Spring Boot extension mechanism described in the framework's
 * <em>developing-auto-configuration</em> reference.
 *
 * <h2>Repository scanning</h2>
 * The Spring Data JDBC repository ({@link ChatMemoryEntryJdbcRepository}) is scanned
 * transitively by Spring Boot's {@code JdbcRepositoriesAutoConfiguration} based on the
 * consumer application's base package. For the JavaClaw monolith that base package is
 * {@code ai.javaclaw}, which already covers the memory module — no explicit
 * {@code @EnableJdbcRepositories} is needed here.
 *
 * <h2>Schema migrations</h2>
 * Flyway migrations under {@code classpath:db/migration/{vendor}} are picked up by
 * Spring Boot's {@code FlywayAutoConfiguration} as soon as a {@code DataSource} is
 * available — no explicit wiring required here.
 *
 * <h2>Classpath gating</h2>
 * {@link ConditionalOnClass} keys on {@link Message} (Spring AI) and {@link RowMapper}
 * (Spring JDBC). {@link RowMapper} is deliberately chosen over
 * {@code NamedParameterJdbcTemplate} because the adapter no longer touches the named
 * template directly — it goes through the repository's {@code rowMapperClass=} query
 * facility.
 */
@AutoConfiguration
@ConditionalOnClass({Message.class, RowMapper.class})
public class JavaClawMemoryAutoConfiguration {

    /**
     * Registers the default {@link ChatMemory} bean: the JDBC-backed adapter.
     *
     * <p>{@link ConditionalOnMissingBean} is intentionally typed on {@link ChatMemory},
     * not on {@link JdbcChatMemory}, so that <em>any</em> custom implementation
     * (in-memory, Redis, etc.) declared by the consumer transparently replaces the
     * default without needing {@code @Primary}.
     *
     * <p>The {@code repository} argument is resolved at bean-instantiation time, after
     * Spring Boot's {@code JdbcRepositoriesAutoConfiguration} has registered
     * {@link ChatMemoryEntryJdbcRepository}, so no explicit auto-configuration ordering
     * ({@code @AutoConfigureAfter}) is needed.
     *
     * @param repository the Spring Data JDBC repository backing the adapter; injected
     *     by the container
     * @return a JDBC-backed {@link ChatMemory} bean
     */
    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory jdbcChatMemory(final ChatMemoryEntryJdbcRepository repository) {
        return new JdbcChatMemory(repository);
    }
}
