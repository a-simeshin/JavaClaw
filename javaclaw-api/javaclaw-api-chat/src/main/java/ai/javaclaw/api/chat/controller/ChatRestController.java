package ai.javaclaw.api.chat.controller;

import ai.javaclaw.api.chat.controller.dto.ChatSendRequest;
import ai.javaclaw.api.chat.service.SseStreamingService;
import ai.javaclaw.conversations.ConversationEnsurer;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * Chat REST API emitting the Vercel AI SDK v4 data stream protocol.
 *
 * <p>Response format: plain text, newline-delimited {@code code:json\n} lines.
 * Adds the {@code x-vercel-ai-data-stream: v1} header so {@code @ai-sdk/react}
 * {@code useChat} with {@code streamProtocol: "data"} negotiates the parser.
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/chat")
public class ChatRestController {

    static final String VERCEL_STREAM_HEADER = "x-vercel-ai-data-stream";
    static final String VERCEL_STREAM_VERSION = "v1";
    static final String DEFAULT_CONVERSATION_ID = "web";

    private final SseStreamingService streamingService;
    private final ConversationEnsurer conversationEnsurer;

    @PostMapping(value = "/send", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<ResponseBodyEmitter> send(@Valid @RequestBody final ChatSendRequest request) {
        final ResponseBodyEmitter emitter = streamingService.createEmitter();
        if (emitter == null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        final String conversationId = resolveConversationId(request.conversationId());
        conversationEnsurer.ensureExists(conversationId);
        conversationEnsurer.touch(conversationId, request.content());
        streamingService.stream(emitter, conversationId, request.content());
        return ResponseEntity.ok()
                .header(VERCEL_STREAM_HEADER, VERCEL_STREAM_VERSION)
                .header("x-conversation-id", conversationId)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(emitter);
    }

    /**
     * Reconnect endpoint — opens a bare data-stream channel tied to a conversation.
     */
    @PostMapping(value = "/stream/{conversationId}", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<ResponseBodyEmitter> reconnect(@PathVariable final String conversationId) {
        final ResponseBodyEmitter emitter = streamingService.createEmitter();
        if (emitter == null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        conversationEnsurer.ensureExists(conversationId);
        return ResponseEntity.ok()
                .header(VERCEL_STREAM_HEADER, VERCEL_STREAM_VERSION)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(emitter);
    }

    private static String resolveConversationId(final String id) {
        if (id == null || id.isBlank()) {
            return DEFAULT_CONVERSATION_ID + "-" + UUID.randomUUID();
        }
        return id;
    }
}
