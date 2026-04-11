package ai.javaclaw.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "javaclaw.security.cookie")
public record CookieProperties(
        @DefaultValue("JCLAW_SESSION") String name,
        @DefaultValue("/") String path,
        @DefaultValue("") String domain,
        @DefaultValue("true") boolean secure,
        @DefaultValue("Lax") String sameSite,
        @DefaultValue("86400") int maxAgeSeconds) {}
