package ai.javaclaw.api.chat.controller;

import ai.javaclaw.delivery.NotificationTransport;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@AllArgsConstructor
@RequestMapping("/api/chat/notifications")
public class NotificationController {

    private final NotificationTransport notificationTransport;

    @GetMapping(value = "/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> subscribe(@PathVariable final String conversationId) {
        return notificationTransport.subscribe(conversationId);
    }
}
