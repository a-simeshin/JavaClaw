package ai.javaclaw.agent.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class SpringEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(SpringEventBus.class);

    private final Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer(256);

    @Override
    public void emit(AgentEvent event) {
        var result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Failed to emit event {}: {}", event.kind(), result);
        }
    }

    @Override
    public Flux<AgentEvent> subscribe(String sessionKey) {
        return sink.asFlux().filter(event -> sessionKey.equals(event.meta().sessionKey()));
    }

    @Override
    public Flux<AgentEvent> subscribeAll() {
        return sink.asFlux();
    }
}
