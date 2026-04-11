package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.pipeline.FallbackChatModel;
import ai.javaclaw.agent.pipeline.ModelFallbackProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Pure unit test for {@link FallbackChatModel} covering bugs #14, #15, #16 —
 * retry loop exhausts each model {@code maxRetriesPerModel} times and only then
 * switches to the next fallback model.
 */
@Tag("e2e")
class FallbackChainE2ETest {

    @Test
    @DisplayName("Fallback chain retries primary once, then fake/a twice, then fake/b succeeds")
    void fallbackChainRetriesPerModelAndFailsOver() {
        ChatModel delegate = mock(ChatModel.class);
        // call(prompt) invocations: primary fails once (real primary call at first)
        // Then fallbackModels = [fake/a, fake/b], maxRetriesPerModel = 2.
        // Sequence: primary (fail), fake/a#1 (fail), fake/a#2 (fail), fake/b#1 (success)
        ChatResponse success = new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        when(delegate.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("primary fail"))
                .thenThrow(new RuntimeException("fake/a #1"))
                .thenThrow(new RuntimeException("fake/a #2"))
                .thenReturn(success);

        ModelFallbackProperties props = new ModelFallbackProperties(true, List.of("fake/a", "fake/b"), 2);
        FallbackChatModel fallback = new FallbackChatModel(delegate, props);

        Prompt prompt = new Prompt("hello");
        ChatResponse result = fallback.call(prompt);

        assertThat(result).isSameAs(success);
        // Total: 1 primary + 2 (fake/a) + 1 (fake/b) = 4 invocations
        verify(delegate, times(4)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("When fallback disabled, exception propagates without retries")
    void fallbackDisabledPropagates() {
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

        ModelFallbackProperties props = new ModelFallbackProperties(false, List.of("fake/a"), 3);
        FallbackChatModel fallback = new FallbackChatModel(delegate, props);

        try {
            fallback.call(new Prompt("hi"));
            org.assertj.core.api.Assertions.fail("expected exception");
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage()).isEqualTo("boom");
        }
        verify(delegate, atLeast(1)).call(any(Prompt.class));
    }
}
