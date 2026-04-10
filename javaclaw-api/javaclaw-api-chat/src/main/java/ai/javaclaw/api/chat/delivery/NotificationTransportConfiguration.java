package ai.javaclaw.api.chat.delivery;

import ai.javaclaw.delivery.NotificationTransport;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Registers {@link PgNotificationTransport} when the datasource URL indicates PostgreSQL.
 *
 * <p>Activation: {@code spring.datasource.url} starts with {@code jdbc:postgresql}.
 * When active, this bean takes precedence over the
 * {@link ai.javaclaw.delivery.InMemoryNotificationTransport} fallback
 * (which is annotated {@code @ConditionalOnMissingBean}).
 */
@Configuration
@Conditional(NotificationTransportConfiguration.PostgresCondition.class)
public class NotificationTransportConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NotificationTransportConfiguration.class);

    @Bean
    @Primary
    public NotificationTransport pgNotificationTransport(DataSource dataSource) {
        log.info("PostgreSQL detected — activating PgNotificationTransport for multi-pod notifications");
        return new PgNotificationTransport(dataSource);
    }

    /**
     * Custom condition that checks whether {@code spring.datasource.url} starts with
     * {@code jdbc:postgresql} (direct PostgreSQL connection). Excludes Testcontainers
     * URLs ({@code jdbc:tc:postgresql}) which use an in-process wrapper and don't
     * support real PG NOTIFY across pods.
     */
    static class PostgresCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String url = context.getEnvironment().getProperty("spring.datasource.url", "");
            return url.startsWith("jdbc:postgresql");
        }
    }
}
