package ai.javaclaw.agent.memory.autoconfigure;

import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.agent.memory.adapter.jdbc.ChatMemoryEntryJdbcRepository;
import ai.javaclaw.agent.memory.adapter.jdbc.JdbcChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Autoconfiguration for the javaclaw-memory module.
 *
 * <p>Registers the default {@link ChatMemory} implementation backed by Spring Data JDBC
 * ({@link JdbcChatMemory}) whenever Spring AI message types and Spring JDBC are on the
 * classpath, and no consumer-defined {@code ChatMemory} bean already exists.
 *
 * <p>Discovered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so the module is bootstrappable as a self-contained unit — it does not rely on the
 * consumer application's {@code @ComponentScan} reaching
 * {@code ai.javaclaw.agent.memory.*}.
 *
 * <p>Override point: define your own {@code ChatMemory} bean in the consumer context and
 * this auto-config steps aside (per {@link ConditionalOnMissingBean}), which is the
 * idiomatic Spring Boot extension mechanism described in the framework's
 * <em>developing-auto-configuration</em> reference.
 *
 * <p>The Spring Data JDBC repository ({@link ChatMemoryEntryJdbcRepository}) is scanned
 * transitively by Spring Boot's {@code JdbcRepositoriesAutoConfiguration} based on the
 * consumer application's base package. For the JavaClaw monolith that base package is
 * {@code ai.javaclaw}, which already covers the memory module.
 *
 * <p>Flyway migrations under {@code classpath:db/migration/{vendor}} are picked up by
 * Spring Boot's {@code FlywayAutoConfiguration} as soon as a {@code DataSource} is
 * available — no explicit wiring required here.
 */
@AutoConfiguration
@ConditionalOnClass({Message.class, NamedParameterJdbcTemplate.class})
public class JavaClawMemoryAutoConfiguration {

    /**
     * Default {@link ChatMemory} bean: the JDBC-backed adapter.
     *
     * <p>{@link ConditionalOnMissingBean} is intentionally typed on {@link ChatMemory}, not
     * on {@link JdbcChatMemory}, so that <em>any</em> custom implementation (in-memory, Redis,
     * etc.) declared by the consumer transparently replaces the default without needing
     * {@code @Primary}.
     *
     * <p>The method parameters are resolved at bean-instantiation time, after Spring Boot's
     * {@code JdbcRepositoriesAutoConfiguration} has registered {@link ChatMemoryEntryJdbcRepository}
     * and {@code NamedParameterJdbcTemplate}, so no explicit autoconfiguration ordering is needed.
     */
    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory jdbcChatMemory(
            final ChatMemoryEntryJdbcRepository repository, final NamedParameterJdbcTemplate namedJdbc) {
        return new JdbcChatMemory(repository, namedJdbc);
    }
}
