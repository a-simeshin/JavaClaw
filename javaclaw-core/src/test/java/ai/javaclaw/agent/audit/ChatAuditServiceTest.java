package ai.javaclaw.agent.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;

class ChatAuditServiceTest {

    private ChatAuditLogRepository repository;
    private ChatAuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(ChatAuditLogRepository.class);
        service = new ChatAuditService(repository);
    }

    @Test
    void log_withAllFields_persistsAllFields() {
        // Given
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(100);
        when(usage.getCompletionTokens()).thenReturn(50);
        when(usage.getTotalTokens()).thenReturn(150);

        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-1", "function", "myTool", "{\"key\":\"val\"}");
        List<AssistantMessage.ToolCall> toolCalls = List.of(toolCall);

        Prompt prompt = new Prompt("Hello, test!");

        // When
        service.log("conv-42", "stream", prompt, "OK response", 123L, "user-uuid", usage, toolCalls);

        // Then
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(log -> {
            assertThat(log.userId()).isEqualTo("user-uuid");
            assertThat(log.tokenUsage()).contains("promptTokens").contains("100");
            assertThat(log.toolCallsDetail()).contains("myTool").contains("tc-1");
            assertThat(log.conversationId()).isEqualTo("conv-42");
            assertThat(log.responseText()).isEqualTo("OK response");
            assertThat(log.durationMs()).isEqualTo(123L);
            return true;
        }));
    }

    @Test
    void log_withNullUsageAndNullToolCalls_persistsNullJsonFields() {
        Prompt prompt = new Prompt("Hello!");

        service.log("conv-1", "stream", prompt, "response", 50L, "user-1", null, null);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(log -> {
            assertThat(log.userId()).isEqualTo("user-1");
            assertThat(log.tokenUsage()).isNull();
            assertThat(log.toolCallsDetail()).isNull();
            return true;
        }));
    }

    @Test
    void logError_withThrowable_persistsErrorFields() {
        Prompt prompt = new Prompt("Failing request");
        RuntimeException error = new RuntimeException("Something went wrong");

        service.logError("conv-99", "stream", prompt, error, 200L, "user-xyz");

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(log -> {
            assertThat(log.userId()).isEqualTo("user-xyz");
            assertThat(log.errorMessage()).isEqualTo("Something went wrong");
            assertThat(log.errorTrace()).contains("RuntimeException").contains("Something went wrong");
            assertThat(log.responseText()).isNull();
            assertThat(log.conversationId()).isEqualTo("conv-99");
            return true;
        }));
    }

    @Test
    void logError_withNullUserId_persistsNullUserId() {
        Prompt prompt = new Prompt("request");
        RuntimeException error = new RuntimeException("err");

        service.logError("conv-x", "stream", prompt, error, 10L, null);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(log -> {
            assertThat(log.userId()).isNull();
            assertThat(log.errorMessage()).isEqualTo("err");
            return true;
        }));
    }

    @Test
    void log_withMultipleToolCalls_serializesAll() {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(usage.getTotalTokens()).thenReturn(30);

        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall("id-1", "function", "toolA", "{\"a\":1}"),
                new AssistantMessage.ToolCall("id-2", "function", "toolB", "{\"b\":2}"));

        service.log("conv-multi", "stream", null, "result", 77L, "uid", usage, toolCalls);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(log -> {
            assertThat(log.toolCallsDetail())
                    .contains("toolA")
                    .contains("toolB")
                    .contains("id-1")
                    .contains("id-2");
            assertThat(log.tokenUsage()).contains("\"totalTokens\":30");
            return true;
        }));
    }
}
