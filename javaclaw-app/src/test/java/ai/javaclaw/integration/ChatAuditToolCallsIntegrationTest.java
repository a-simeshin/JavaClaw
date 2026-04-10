package ai.javaclaw.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.audit.ChatAuditLog;
import ai.javaclaw.agent.audit.ChatAuditLogRepository;
import ai.javaclaw.agent.audit.ChatAuditService;
import ai.javaclaw.agent.pipeline.ChatService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Integration tests for T59 gap closure: ChatAudit captures tool call details.
 *
 * <p>Covers:
 * <ul>
 *   <li>t59a: Single tool call → toolNames populated in audit log</li>
 *   <li>t59b: Multiple tool calls detail → all names serialized via direct audit service call</li>
 *   <li>t59c: Error path → logError() saves record with error_message != null</li>
 * </ul>
 */
class ChatAuditToolCallsIntegrationTest extends IntegrationTestBase {

    // Fixed conversation IDs registered in conversations table before each test
    private static final String CONV_T59A = "t59a-conv-audit";
    private static final String CONV_T59B = "t59b-conv-audit";
    private static final String CONV_T59C = "t59c-conv-audit";

    @TestBean
    ChatModel chatModel;

    static ChatModel chatModel() {
        return Mockito.mock(ChatModel.class);
    }

    @Autowired
    ChatService chatService;

    @Autowired
    ChatAuditService chatAuditService;

    @Autowired
    ChatAuditLogRepository chatAuditLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clean audit log
        chatAuditLogRepository.deleteAll();
        // Clean chat memory for our fixed conversation IDs (CASCADE deletes from spring_ai_chat_memory)
        jdbcTemplate.update("DELETE FROM conversations WHERE id IN (?, ?, ?)", CONV_T59A, CONV_T59B, CONV_T59C);
        // Ensure conversation rows exist so spring_ai_chat_memory FK is satisfied
        // user_id is nullable — we don't need a matching users row
        for (String convId : List.of(CONV_T59A, CONV_T59B, CONV_T59C)) {
            jdbcTemplate.update(
                    "INSERT INTO conversations (id, user_id, title) VALUES (?, NULL, ?) ON CONFLICT DO NOTHING",
                    convId,
                    "T59 test conversation " + convId);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // t59a: single call → toolNames populated in audit
    // ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("t59a — successful call populates toolNames in audit log with registered tools")
    void t59a_singleToolCall_toolNamesPopulated() {
        ChatResponse mockResponse =
                new ChatResponse(List.of(new Generation(new AssistantMessage("Tool executed successfully"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        chatService.call(CONV_T59A, "Please use a tool to help me");

        // ChatAuditService.logSuccess is @Async — wait for it
        await().atMost(5, SECONDS).until(() -> {
            List<ChatAuditLog> logs = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(CONV_T59A);
            return !logs.isEmpty();
        });

        List<ChatAuditLog> logs = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(CONV_T59A);
        assertThat(logs).hasSize(1);

        ChatAuditLog log = logs.getFirst();
        assertThat(log.conversationId()).isEqualTo(CONV_T59A);
        assertThat(log.responseText()).isEqualTo("Tool executed successfully");
        assertThat(log.errorMessage()).isNull();
        assertThat(log.method()).isEqualTo("call");
        // toolNames extracted from ToolCallingChatOptions in the Prompt built by ChatService.
        // The real ToolCallbackResolver registers multiple tools (TaskTool, CheckListTool, etc.)
        assertThat(log.toolNames()).isNotBlank();
    }

    // ──────────────────────────────────────────────────────────────────
    // t59b: multiple tool calls detail → all names serialized
    // ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("t59b — multiple tool calls detail serialized via extended audit service overload")
    void t59b_multipleToolCalls_allDetailsSerialized() {
        String toolCallsDetail = "[{\"name\":\"searchWeb\"},{\"name\":\"getWeather\"},{\"name\":\"sendEmail\"}]";

        // Call the extended 9-arg logSuccess overload directly to verify toolCallsDetail is persisted
        chatAuditService.logSuccess(
                CONV_T59B,
                "call",
                null,
                "All three tools executed",
                150L,
                "user-test",
                toolCallsDetail,
                "{\"prompt_tokens\":30,\"completion_tokens\":10}");

        await().atMost(5, SECONDS).until(() -> {
            List<ChatAuditLog> logs = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(CONV_T59B);
            return !logs.isEmpty() && logs.getFirst().toolCallsDetail() != null;
        });

        List<ChatAuditLog> logs = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(CONV_T59B);
        assertThat(logs).hasSize(1);

        ChatAuditLog log = logs.getFirst();
        assertThat(log.toolCallsDetail()).isNotNull();
        assertThat(log.toolCallsDetail()).contains("searchWeb");
        assertThat(log.toolCallsDetail()).contains("getWeather");
        assertThat(log.toolCallsDetail()).contains("sendEmail");
        assertThat(log.responseText()).isEqualTo("All three tools executed");
        assertThat(log.userId()).isEqualTo("user-test");
        assertThat(log.tokenUsage()).contains("prompt_tokens");
    }

    // ──────────────────────────────────────────────────────────────────
    // t59c: error path → logError records error_message != null
    // ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("t59c — error path: chatModel throws → audit log records error_message")
    void t59c_errorPath_auditStillLogged() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM service unavailable"));

        try {
            chatService.call(CONV_T59C, "This will fail");
        } catch (RuntimeException ignored) {
            // Expected — ChatService rethrows after logging
        }

        // ChatAuditService.logError is @Async — wait for the error audit entry
        await().atMost(5, SECONDS).until(() -> {
            List<ChatAuditLog> logs = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(CONV_T59C);
            return !logs.isEmpty() && logs.getFirst().errorMessage() != null;
        });

        List<ChatAuditLog> logs = chatAuditLogRepository.findByConversationIdOrderByCreatedAtDesc(CONV_T59C);
        assertThat(logs).hasSize(1);

        ChatAuditLog log = logs.getFirst();
        assertThat(log.errorMessage()).isEqualTo("LLM service unavailable");
        assertThat(log.errorTrace()).contains("RuntimeException");
        assertThat(log.responseText()).isNull();
        assertThat(log.conversationId()).isEqualTo(CONV_T59C);
        assertThat(log.method()).isEqualTo("call");
    }
}
