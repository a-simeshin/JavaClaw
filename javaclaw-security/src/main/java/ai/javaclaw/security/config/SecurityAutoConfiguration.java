package ai.javaclaw.security.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties({CookieProperties.class, SessionProperties.class})
public class SecurityAutoConfiguration {}
