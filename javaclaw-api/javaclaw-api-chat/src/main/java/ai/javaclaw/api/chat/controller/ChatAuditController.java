package ai.javaclaw.api.chat.controller;

import ai.javaclaw.agent.audit.ChatAuditLog;
import ai.javaclaw.agent.audit.ChatAuditLogRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/audit")
public class ChatAuditController {

    private final ChatAuditLogRepository chatAuditLogRepository;

    @GetMapping("/chat")
    public List<ChatAuditLog> chatAudit(
            @RequestParam final String conversationId, @RequestParam(defaultValue = "20") final int limit) {
        final List<ChatAuditLog> all = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(0, limit);
    }
}
