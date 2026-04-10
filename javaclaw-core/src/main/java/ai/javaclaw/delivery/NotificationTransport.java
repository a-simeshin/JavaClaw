package ai.javaclaw.delivery;

import reactor.core.publisher.Flux;

/**
 * Transport for real-time push notifications to connected clients.
 * Broadcasts notification payloads to all pods/subscribers listening for a given conversationId.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@code InMemoryNotificationTransport} — single-pod, uses Spring ApplicationEvent (dev/test)</li>
 *   <li>{@code PgNotificationTransport} — multi-pod, uses PostgreSQL NOTIFY/LISTEN (production)</li>
 * </ul>
 */
public interface NotificationTransport {

    /**
     * Broadcasts a notification payload to all subscribers of the given conversationId.
     * In multi-pod deployments, the broadcast reaches all pods.
     *
     * @param conversationId the conversation to notify
     * @param payload        JSON-serialized notification data
     */
    void broadcast(String conversationId, String payload);

    /**
     * Subscribes to real-time notifications for a specific conversationId.
     * Returns a hot Flux that emits payloads as they arrive via {@link #broadcast}.
     *
     * @param conversationId the conversation to subscribe to
     * @return a Flux of notification payloads
     */
    Flux<String> subscribe(String conversationId);
}
