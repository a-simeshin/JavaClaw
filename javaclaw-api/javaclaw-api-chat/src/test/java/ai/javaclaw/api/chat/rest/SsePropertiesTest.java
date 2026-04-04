package ai.javaclaw.api.chat.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SsePropertiesTest {

    @Test
    void appliesDefaultsForNullsAndNonPositives() {
        SseProperties props = new SseProperties(null, null, 0);
        assertThat(props.timeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(props.heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(props.maxConcurrent()).isEqualTo(1000);
    }

    @Test
    void preservesExplicitValues() {
        SseProperties props = new SseProperties(Duration.ofMinutes(5), Duration.ofSeconds(3), 42);
        assertThat(props.timeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.heartbeatInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(props.maxConcurrent()).isEqualTo(42);
    }
}
