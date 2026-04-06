package ai.javaclaw.api.chat.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SsePropertiesTest {

    @Test
    void appliesDefaultsForNullsAndNonPositives() {
        ChatRestConfiguration.SseProperties props = new ChatRestConfiguration.SseProperties(null, null, 0);
        assertThat(props.timeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(props.heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(props.maxConcurrent()).isEqualTo(1000);
    }

    @Test
    void preservesExplicitValues() {
        ChatRestConfiguration.SseProperties props =
                new ChatRestConfiguration.SseProperties(Duration.ofMinutes(5), Duration.ofSeconds(3), 42);
        assertThat(props.timeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.heartbeatInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(props.maxConcurrent()).isEqualTo(42);
    }
}
