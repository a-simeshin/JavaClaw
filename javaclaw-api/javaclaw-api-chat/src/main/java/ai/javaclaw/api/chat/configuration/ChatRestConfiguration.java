package ai.javaclaw.api.chat.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link SseProperties} binding from {@code javaclaw.chat.sse.*}. */
@Configuration
@EnableConfigurationProperties(ChatRestConfiguration.SseProperties.class)
public class ChatRestConfiguration {

    /**
     * Configuration for the chat SSE transport.
     *
     * <ul>
     *   <li>{@code timeout} — per-connection emitter timeout (applied when constructing {@code SseEmitter}).</li>
     *   <li>{@code heartbeatInterval} — cadence for keep-alive comments used to detect disconnects.</li>
     *   <li>{@code maxConcurrent} — admission-control ceiling on simultaneous open streams (OOM guard).</li>
     * </ul>
     */
    @ConfigurationProperties("javaclaw.chat.sse")
    public record SseProperties(Duration timeout, Duration heartbeatInterval, int maxConcurrent) {

        public SseProperties {
            if (timeout == null) timeout = Duration.ofMinutes(30);
            if (heartbeatInterval == null) heartbeatInterval = Duration.ofSeconds(15);
            if (maxConcurrent <= 0) maxConcurrent = 1000;
        }
    }
}
