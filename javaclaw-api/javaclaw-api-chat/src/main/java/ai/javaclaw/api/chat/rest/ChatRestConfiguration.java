package ai.javaclaw.api.chat.rest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link SseProperties} binding from {@code javaclaw.chat.sse.*}. */
@Configuration
@EnableConfigurationProperties(SseProperties.class)
public class ChatRestConfiguration {}
