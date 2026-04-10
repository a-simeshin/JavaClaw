package ai.javaclaw.agent.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SpringEventBusTest {

    private SpringEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new SpringEventBus();
    }

    @Test
    @DisplayName("T38: emit → all subscribers receive the event")
    void emitDeliversToAllSubscribers() {
        var event = AgentEvent.of(EventKind.TASK_CREATED, AgentEvent.EventMeta.ofSession("session-1"));

        StepVerifier.create(eventBus.subscribeAll().take(1))
                .then(() -> eventBus.emit(event))
                .assertNext(received -> {
                    assertThat(received.kind()).isEqualTo(EventKind.TASK_CREATED);
                    assertThat(received.meta().sessionKey()).isEqualTo("session-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("T39: subscribe with session filter → only matching events")
    void subscribeFiltersEventsBySessionKey() {
        var matchingEvent = AgentEvent.of(EventKind.TURN_START, AgentEvent.EventMeta.ofSession("session-A"));

        var nonMatchingEvent = AgentEvent.of(EventKind.TURN_START, AgentEvent.EventMeta.ofSession("session-B"));

        StepVerifier.create(eventBus.subscribe("session-A").take(1))
                .then(() -> {
                    eventBus.emit(nonMatchingEvent);
                    eventBus.emit(matchingEvent);
                })
                .assertNext(received -> assertThat(received.meta().sessionKey()).isEqualTo("session-A"))
                .verifyComplete();
    }

    @Test
    @DisplayName("T40: multiple events emitted → subscribeAll receives all")
    void subscribeAllReceivesMultipleEvents() {
        var event1 = AgentEvent.of(EventKind.LLM_REQUEST, AgentEvent.EventMeta.ofSession("s1"));
        var event2 = AgentEvent.of(EventKind.LLM_RESPONSE, AgentEvent.EventMeta.ofSession("s2"));

        StepVerifier.create(eventBus.subscribeAll().take(2))
                .then(() -> {
                    eventBus.emit(event1);
                    eventBus.emit(event2);
                })
                .assertNext(e -> assertThat(e.kind()).isEqualTo(EventKind.LLM_REQUEST))
                .assertNext(e -> assertThat(e.kind()).isEqualTo(EventKind.LLM_RESPONSE))
                .verifyComplete();
    }

    @Test
    @DisplayName("AgentEvent factory sets timestamp and empty payload")
    void agentEventFactoryDefaults() {
        var event = AgentEvent.of(EventKind.ERROR, AgentEvent.EventMeta.ofTask("s1", "task-1"));

        assertThat(event.timestamp()).isNotNull();
        assertThat(event.payload()).isEmpty();
        assertThat(event.meta().taskId()).isEqualTo("task-1");
        assertThat(event.meta().sessionKey()).isEqualTo("s1");
    }

    @Test
    @DisplayName("AgentEvent factory with payload preserves data")
    void agentEventFactoryWithPayload() {
        var payload = Map.<String, Object>of("progress", 42, "message", "processing");
        var event = AgentEvent.of(EventKind.PROGRESS_UPDATE, AgentEvent.EventMeta.ofSession("s1"), payload);

        assertThat(event.payload()).containsEntry("progress", 42);
        assertThat(event.payload()).containsEntry("message", "processing");
    }
}
