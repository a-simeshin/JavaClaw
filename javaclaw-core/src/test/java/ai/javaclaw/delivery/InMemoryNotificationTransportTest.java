package ai.javaclaw.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class InMemoryNotificationTransportTest {

    private InMemoryNotificationTransport transport;
    private List<NotificationEvent> publishedEvents;

    @BeforeEach
    void setUp() {
        publishedEvents = new ArrayList<>();
        // ApplicationEventPublisher that captures events and feeds them back to the transport's @EventListener
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof NotificationEvent ne) {
                publishedEvents.add(ne);
                // Simulate Spring's event dispatch — call the @EventListener method directly
                transport.onNotificationEvent(ne);
            }
        };
        transport = new InMemoryNotificationTransport(publisher);
    }

    /** T46: InMemoryTransport.broadcast() publishes ApplicationEvent. */
    @Test
    void broadcastPublishesApplicationEvent() {
        transport.broadcast("conv-1", "{\"status\":\"completed\"}");

        assertThat(publishedEvents).hasSize(1);
        NotificationEvent event = publishedEvents.getFirst();
        assertThat(event.getConversationId()).isEqualTo("conv-1");
        assertThat(event.getPayload()).isEqualTo("{\"status\":\"completed\"}");
    }

    /** T47: InMemoryTransport.subscribe() receives events from broadcast. */
    @Test
    void subscribeReceivesBroadcastedEvents() {
        Flux<String> flux = transport.subscribe("conv-1");

        StepVerifier.create(flux.take(1))
                .then(() -> transport.broadcast("conv-1", "{\"status\":\"done\"}"))
                .expectNext("{\"status\":\"done\"}")
                .verifyComplete();
    }

    @Test
    void subscribeOnlyReceivesEventsForMatchingConversation() {
        Flux<String> flux1 = transport.subscribe("conv-1");
        Flux<String> flux2 = transport.subscribe("conv-2");

        List<String> received1 = new ArrayList<>();
        List<String> received2 = new ArrayList<>();
        flux1.subscribe(received1::add);
        flux2.subscribe(received2::add);

        transport.broadcast("conv-1", "msg-for-1");
        transport.broadcast("conv-2", "msg-for-2");

        assertThat(received1).containsExactly("msg-for-1");
        assertThat(received2).containsExactly("msg-for-2");
    }

    @Test
    void broadcastWithNoSubscribersDoesNotFail() {
        // No subscriber for conv-99 — should not throw
        transport.broadcast("conv-99", "ignored");
        assertThat(publishedEvents).hasSize(1);
    }

    @Test
    void multipleSubscribersSameConversationAllReceive() {
        Flux<String> flux = transport.subscribe("conv-1");

        List<String> received1 = new ArrayList<>();
        List<String> received2 = new ArrayList<>();
        flux.subscribe(received1::add);
        flux.subscribe(received2::add);

        transport.broadcast("conv-1", "hello");

        assertThat(received1).containsExactly("hello");
        assertThat(received2).containsExactly("hello");
    }

    @Test
    void multipleEventsDeliveredInOrder() {
        Flux<String> flux = transport.subscribe("conv-1");

        StepVerifier.create(flux.take(3))
                .then(() -> {
                    transport.broadcast("conv-1", "first");
                    transport.broadcast("conv-1", "second");
                    transport.broadcast("conv-1", "third");
                })
                .expectNext("first", "second", "third")
                .verifyComplete();
    }

    @Test
    void notificationEventCarriesCorrectFields() {
        NotificationEvent event = new NotificationEvent(this, "conv-42", "{\"key\":\"value\"}");
        assertThat(event.getConversationId()).isEqualTo("conv-42");
        assertThat(event.getPayload()).isEqualTo("{\"key\":\"value\"}");
        assertThat(event.getSource()).isSameAs(this);
    }
}
