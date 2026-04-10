package ai.javaclaw.delivery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-memory {@link NotificationTransport} for single-pod deployments (dev/test with H2/SQLite).
 * Uses Spring {@link ApplicationEventPublisher} for broadcast and Reactor {@link Sinks} for subscriptions.
 *
 * <p>Activated via {@code @ConditionalOnMissingBean} — if a production transport (e.g. PgNotificationTransport)
 * is registered, this bean is not created.
 */
@Component
@ConditionalOnMissingBean(value = NotificationTransport.class, ignored = InMemoryNotificationTransport.class)
public class InMemoryNotificationTransport implements NotificationTransport {

    private static final Logger log = LoggerFactory.getLogger(InMemoryNotificationTransport.class);

    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, Sinks.Many<String>> subscriptions = new ConcurrentHashMap<>();

    public InMemoryNotificationTransport(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void broadcast(String conversationId, String payload) {
        log.debug("Broadcasting notification for conversation {}: {}", conversationId, payload);
        eventPublisher.publishEvent(new NotificationEvent(this, conversationId, payload));
    }

    @Override
    public Flux<String> subscribe(String conversationId) {
        Sinks.Many<String> sink = subscriptions.computeIfAbsent(
                conversationId, k -> Sinks.many().multicast().onBackpressureBuffer(256));
        return sink.asFlux();
    }

    @EventListener
    public void onNotificationEvent(NotificationEvent event) {
        String conversationId = event.getConversationId();
        Sinks.Many<String> sink = subscriptions.get(conversationId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(event.getPayload());
            if (result.isFailure()) {
                log.warn("Failed to emit notification for conversation {}: {}", conversationId, result);
            }
        }
    }
}
