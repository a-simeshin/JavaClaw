package ai.javaclaw.api.chat.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.javaclaw.channels.ChannelRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

class SseStreamingServiceTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChannelRegistry registry = new ChannelRegistry();

    @Test
    void createEmitterReturnsNullWhenCapacityExhausted() {
        SseProperties props = new SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 1);
        SseStreamingService service = new SseStreamingService(
                chatClient,
                registry,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());

        ResponseBodyEmitter first = service.createEmitter();
        ResponseBodyEmitter second = service.createEmitter();

        assertThat(first).isNotNull();
        assertThat(second).isNull();
        assertThat(service.activeEmitters()).isEqualTo(1);
        assertThat(service.availablePermits()).isZero();
    }

    @Test
    void createEmitterTracksConfiguredTimeout() {
        SseProperties props = new SseProperties(Duration.ofMillis(4242), Duration.ofSeconds(1), 5);
        SseStreamingService service = new SseStreamingService(
                chatClient,
                registry,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());

        ResponseBodyEmitter emitter = service.createEmitter();

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(4242L);
    }

    @Test
    void availablePermitsDecrementsOnCreate() {
        SseProperties props = new SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 3);
        SseStreamingService service = new SseStreamingService(
                chatClient,
                registry,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());

        service.createEmitter();
        service.createEmitter();

        assertThat(service.availablePermits()).isEqualTo(1);
        assertThat(service.activeEmitters()).isEqualTo(2);
    }

    @Test
    void exhaustingAllPermitsReturnsNullAfterMaxReached() {
        SseProperties props = new SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 2);
        SseStreamingService service = new SseStreamingService(
                chatClient,
                registry,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());

        assertThat(service.createEmitter()).isNotNull();
        assertThat(service.createEmitter()).isNotNull();
        assertThat(service.createEmitter()).isNull();
        assertThat(service.availablePermits()).isZero();
    }
}
