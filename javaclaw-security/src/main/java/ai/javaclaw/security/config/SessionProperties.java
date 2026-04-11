package ai.javaclaw.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "javaclaw.security.session")
public record SessionProperties(@DefaultValue("PT24H") Duration ttl, @DefaultValue("PT1H") Duration cleanupInterval) {}
