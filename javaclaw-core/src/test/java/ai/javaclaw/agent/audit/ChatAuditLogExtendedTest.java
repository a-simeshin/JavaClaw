package ai.javaclaw.agent.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.prompt.Prompt;

class ChatAuditLogExtendedTest {

    @Test
    void record_includesNewFields_userId_toolCallsDetail_tokenUsage() {
        ChatAuditLog log = new ChatAuditLog(
                null,
                "conv-1",
                Instant.now(),
                "stream",
                "system prompt",
                "history",
                "user content",
                "tool1, tool2",
                "response",
                null,
                null,
                100L,
                "user-42",
                "[{\"name\":\"getWeather\",\"args\":\"{}\",\"result\":\"sunny\",\"duration_ms\":50}]",
                "{\"prompt_tokens\":100,\"completion_tokens\":50,\"total\":150}");

        assertThat(log.userId()).isEqualTo("user-42");
        assertThat(log.toolCallsDetail()).contains("getWeather");
        assertThat(log.tokenUsage()).contains("prompt_tokens");
    }

    @Test
    void record_newFieldsCanBeNull() {
        ChatAuditLog log = new ChatAuditLog(
                null,
                "conv-2",
                Instant.now(),
                "call",
                null,
                null,
                "hello",
                null,
                "world",
                null,
                null,
                50L,
                null,
                null,
                null);

        assertThat(log.userId()).isNull();
        assertThat(log.toolCallsDetail()).isNull();
        assertThat(log.tokenUsage()).isNull();
    }

    @Test
    void record_backwardCompatible_oldFieldsPreserved() {
        Instant now = Instant.now();
        ChatAuditLog log = new ChatAuditLog(
                1L, "conv-3", now, "stream", "sys", "hist", "user", "tools", "resp", "err", "trace", 200L, "user-1",
                "detail", "usage");

        assertThat(log.id()).isEqualTo(1L);
        assertThat(log.conversationId()).isEqualTo("conv-3");
        assertThat(log.createdAt()).isEqualTo(now);
        assertThat(log.method()).isEqualTo("stream");
        assertThat(log.systemPrompt()).isEqualTo("sys");
        assertThat(log.history()).isEqualTo("hist");
        assertThat(log.userContent()).isEqualTo("user");
        assertThat(log.toolNames()).isEqualTo("tools");
        assertThat(log.responseText()).isEqualTo("resp");
        assertThat(log.errorMessage()).isEqualTo("err");
        assertThat(log.errorTrace()).isEqualTo("trace");
        assertThat(log.durationMs()).isEqualTo(200L);
    }

    @Test
    void service_logSuccessExtended_passesNewFieldsToRepository() {
        ChatAuditLogRepository repo = mock(ChatAuditLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ChatAuditService service = new ChatAuditService(repo);

        Prompt prompt = new Prompt("test message");
        service.logSuccess(
                "conv-1", "call", prompt, "response", 100L, "user-42", "[{\"name\":\"tool1\"}]", "{\"total\":150}");

        ArgumentCaptor<ChatAuditLog> captor = ArgumentCaptor.forClass(ChatAuditLog.class);
        verify(repo).save(captor.capture());

        ChatAuditLog saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo("user-42");
        assertThat(saved.toolCallsDetail()).isEqualTo("[{\"name\":\"tool1\"}]");
        assertThat(saved.tokenUsage()).isEqualTo("{\"total\":150}");
        assertThat(saved.conversationId()).isEqualTo("conv-1");
        assertThat(saved.responseText()).isEqualTo("response");
    }

    @Test
    void service_logErrorExtended_passesNewFieldsToRepository() {
        ChatAuditLogRepository repo = mock(ChatAuditLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ChatAuditService service = new ChatAuditService(repo);

        Prompt prompt = new Prompt("test message");
        RuntimeException error = new RuntimeException("boom");
        service.logError("conv-2", "stream", prompt, error, 200L, "user-99", null, "{\"total\":50}");

        ArgumentCaptor<ChatAuditLog> captor = ArgumentCaptor.forClass(ChatAuditLog.class);
        verify(repo).save(captor.capture());

        ChatAuditLog saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo("user-99");
        assertThat(saved.toolCallsDetail()).isNull();
        assertThat(saved.tokenUsage()).isEqualTo("{\"total\":50}");
        assertThat(saved.errorMessage()).isEqualTo("boom");
        assertThat(saved.errorTrace()).contains("RuntimeException");
    }

    @Test
    void service_logSuccessLegacy_setsNewFieldsToNull() {
        ChatAuditLogRepository repo = mock(ChatAuditLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ChatAuditService service = new ChatAuditService(repo);

        Prompt prompt = new Prompt("test message");
        service.logSuccess("conv-3", "call", prompt, "response", 100L);

        ArgumentCaptor<ChatAuditLog> captor = ArgumentCaptor.forClass(ChatAuditLog.class);
        verify(repo).save(captor.capture());

        ChatAuditLog saved = captor.getValue();
        assertThat(saved.userId()).isNull();
        assertThat(saved.toolCallsDetail()).isNull();
        assertThat(saved.tokenUsage()).isNull();
    }

    @Test
    void service_logErrorLegacy_setsNewFieldsToNull() {
        ChatAuditLogRepository repo = mock(ChatAuditLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ChatAuditService service = new ChatAuditService(repo);

        Prompt prompt = new Prompt("test message");
        service.logError("conv-4", "call", prompt, new RuntimeException("err"), 50L);

        ArgumentCaptor<ChatAuditLog> captor = ArgumentCaptor.forClass(ChatAuditLog.class);
        verify(repo).save(captor.capture());

        ChatAuditLog saved = captor.getValue();
        assertThat(saved.userId()).isNull();
        assertThat(saved.toolCallsDetail()).isNull();
        assertThat(saved.tokenUsage()).isNull();
    }
}
