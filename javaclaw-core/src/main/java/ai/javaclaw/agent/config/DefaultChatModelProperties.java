package ai.javaclaw.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.ai.openai.chat.options")
public record DefaultChatModelProperties(String model) {}
