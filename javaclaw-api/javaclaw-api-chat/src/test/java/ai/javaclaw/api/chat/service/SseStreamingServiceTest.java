package ai.javaclaw.api.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.javaclaw.agent.pipeline.ChatService;
import ai.javaclaw.api.chat.configuration.ChatRestConfiguration;
import ai.javaclaw.channels.ChannelContextService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * Unit tests for {@link SseStreamingService} admission control and emitter lifecycle.
 *
 * <p>Verifies semaphore-based concurrency limiting and emitter timeout configuration
 * without starting a real LLM connection.
 */
class SseStreamingServiceTest {

    /** Мок ChatService — streaming/call не вызываются в этих тестах. */
    private final ChatService chatService = mock(ChatService.class);

    /** Мок ChannelContextService — saveContext не вызывается в этих тестах. */
    private final ChannelContextService channelContextService = mock(ChannelContextService.class);

    @Test
    void createEmitterReturnsNullWhenCapacityExhausted() {
        final ChatRestConfiguration.SseProperties props =
                new ChatRestConfiguration.SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 1);
        final SseStreamingService service = new SseStreamingService(
                chatService,
                channelContextService,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());

        final ResponseBodyEmitter first = service.createEmitter();
        final ResponseBodyEmitter second = service.createEmitter();

        assertThat(first).isNotNull();
        assertThat(second).isNull();
        assertThat(service.activeEmitters()).isEqualTo(1);
        assertThat(service.availablePermits()).isZero();
    }

    @Test
    void createEmitterTracksConfiguredTimeout() {
        final ChatRestConfiguration.SseProperties props =
                new ChatRestConfiguration.SseProperties(Duration.ofMillis(4242), Duration.ofSeconds(1), 5);
        final SseStreamingService service = new SseStreamingService(
                chatService,
                channelContextService,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());

        final ResponseBodyEmitter emitter = service.createEmitter();

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(4242L);
    }

    @Test
    void availablePermitsDecrementsOnCreate() {
        final ChatRestConfiguration.SseProperties props =
                new ChatRestConfiguration.SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 3);
        final SseStreamingService service = new SseStreamingService(
                chatService,
                channelContextService,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());

        service.createEmitter();
        service.createEmitter();

        assertThat(service.availablePermits()).isEqualTo(1);
        assertThat(service.activeEmitters()).isEqualTo(2);
    }

    @Test
    void exhaustingAllPermitsReturnsNullAfterMaxReached() {
        final ChatRestConfiguration.SseProperties props =
                new ChatRestConfiguration.SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 2);
        final SseStreamingService service = new SseStreamingService(
                chatService,
                channelContextService,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());

        assertThat(service.createEmitter()).isNotNull();
        assertThat(service.createEmitter()).isNotNull();
        assertThat(service.createEmitter()).isNull();
        assertThat(service.availablePermits()).isZero();
    }
}
