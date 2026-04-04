package ai.javaclaw.api.chat.rest;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists / inspects / deletes conversations stored in the chat-memory table.
 *
 * <p>Backed by {@link ChatMemoryRepository} (the injected {@code @Primary} bean is
 * {@code JdbcAppendableChatMemoryRepository}). Titles and timestamps are derived
 * since the underlying schema does not track them natively — titles fall back to
 * the first user message preview, timestamps to {@link Instant#EPOCH} when
 * unknown.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ChatMemoryRepository chatMemoryRepository;

    public ConversationController(ChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @GetMapping
    public PageResponse<ConversationDto> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        List<String> ids = chatMemoryRepository.findConversationIds();
        List<ConversationDto> all = new ArrayList<>(ids.size());
        for (String id : ids) {
            List<Message> history = chatMemoryRepository.findByConversationId(id);
            all.add(toDto(id, history));
        }
        return PageResponse.of(all, page, size);
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageDto> messages(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Message> history = chatMemoryRepository.findByConversationId(id);
        List<MessageDto> mapped = new ArrayList<>(history.size());
        int index = 0;
        for (Message m : history) {
            mapped.add(new MessageDto(id + "#" + index, roleOf(m), textOf(m), Instant.EPOCH));
            index++;
        }
        return PageResponse.of(mapped, page, size);
    }

    @PostMapping
    public ConversationDto create(@Valid @RequestBody(required = false) CreateConversationRequest request) {
        String id = request != null && request.id() != null && !request.id().isBlank()
                ? request.id()
                : "web-" + UUID.randomUUID();
        // Initialize with an empty history so findConversationIds() picks it up.
        chatMemoryRepository.saveAll(id, List.of());
        String title = request != null && request.title() != null ? request.title() : "New conversation";
        Instant now = Instant.now();
        return new ConversationDto(id, title, now, now, 0);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        chatMemoryRepository.deleteByConversationId(id);
        return ResponseEntity.noContent().build();
    }

    private static ConversationDto toDto(String id, List<Message> history) {
        String title = history.stream()
                .filter(UserMessage.class::isInstance)
                .map(Message::getText)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .map(ConversationController::truncate)
                .orElse(id);
        return new ConversationDto(id, title, Instant.EPOCH, Instant.EPOCH, history.size());
    }

    private static String truncate(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() > 80 ? single.substring(0, 77) + "..." : single;
    }

    private static String roleOf(Message m) {
        if (m instanceof UserMessage) return "user";
        if (m instanceof AssistantMessage) return "assistant";
        if (m instanceof SystemMessage) return "system";
        if (m instanceof ToolResponseMessage) return "tool";
        return m.getMessageType() != null ? m.getMessageType().getValue() : "assistant";
    }

    private static String textOf(Message m) {
        String text = m.getText();
        return text == null ? "" : text;
    }
}
