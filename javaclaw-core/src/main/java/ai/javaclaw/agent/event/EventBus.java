package ai.javaclaw.agent.event;

import reactor.core.publisher.Flux;

public interface EventBus {

    void emit(AgentEvent event);

    Flux<AgentEvent> subscribe(String sessionKey);

    Flux<AgentEvent> subscribeAll();
}
