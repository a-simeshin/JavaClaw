package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class FallbackChatModelTest {

    private static final ChatResponse OK_RESPONSE =
            new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));

    @Test
    void callDelegatesToPrimaryWhenSuccessful() {
        final ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(any(Prompt.class))).thenReturn(OK_RESPONSE);

        final var model = new FallbackChatModel(delegate, disabledProps());
        final Prompt prompt = testPrompt();

        final ChatResponse result = model.call(prompt);

        assertThat(result).isSameAs(OK_RESPONSE);
        verify(delegate, times(1)).call(any(Prompt.class));
    }

    @Test
    void callThrowsWhenPrimaryFailsAndFallbackDisabled() {
        final ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(any(Prompt.class))).thenThrow(new RuntimeException("model down"));

        final var model = new FallbackChatModel(delegate, disabledProps());

        assertThatThrownBy(() -> model.call(testPrompt()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("model down");
    }

    @Test
    void callFallsBackToSecondModelOnPrimaryFailure() {
        final ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("primary down"))
                .thenReturn(OK_RESPONSE);

        final var props = new ModelFallbackProperties(true, List.of("fallback/model-a"), 1);
        final var model = new FallbackChatModel(delegate, props);

        final ChatResponse result = model.call(testPrompt());

        assertThat(result).isSameAs(OK_RESPONSE);
        verify(delegate, times(2)).call(any(Prompt.class));
    }

    @Test
    void callTriesAllFallbackModelsBeforeExhausted() {
        final ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("primary down"))
                .thenThrow(new RuntimeException("fallback-a down"))
                .thenThrow(new RuntimeException("fallback-b down"));

        final var props = new ModelFallbackProperties(true, List.of("fallback/a", "fallback/b"), 1);
        final var model = new FallbackChatModel(delegate, props);

        assertThatThrownBy(() -> model.call(testPrompt()))
                .isInstanceOf(ModelFallbackExhaustedException.class)
                .hasMessageContaining("All fallback models exhausted")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void callRetriesPerModelAccordingToConfig() {
        final ChatModel delegate = mock(ChatModel.class);
        when(delegate.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("primary down"))
                .thenThrow(new RuntimeException("retry 1"))
                .thenReturn(OK_RESPONSE);

        final var props = new ModelFallbackProperties(true, List.of("fallback/model"), 2);
        final var model = new FallbackChatModel(delegate, props);

        final ChatResponse result = model.call(testPrompt());

        assertThat(result).isSameAs(OK_RESPONSE);
        verify(delegate, times(3)).call(any(Prompt.class));
    }

    @Test
    void streamFallsBackOnError() {
        final ChatModel delegate = mock(ChatModel.class);
        when(delegate.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("stream failed")))
                .thenReturn(Flux.just(OK_RESPONSE));

        final var props = new ModelFallbackProperties(true, List.of("fallback/stream-model"), 1);
        final var model = new FallbackChatModel(delegate, props);

        StepVerifier.create(model.stream(testPrompt())).expectNext(OK_RESPONSE).verifyComplete();
    }

    @Test
    void streamDelegatesToPrimaryWhenFallbackDisabled() {
        final ChatModel delegate = mock(ChatModel.class);
        when(delegate.stream(any(Prompt.class))).thenReturn(Flux.just(OK_RESPONSE));

        final var model = new FallbackChatModel(delegate, disabledProps());

        StepVerifier.create(model.stream(testPrompt())).expectNext(OK_RESPONSE).verifyComplete();
    }

    @Test
    void withModelPreservesToolCallbacks() {
        final var toolCallbacks = new org.springframework.ai.tool.ToolCallback[0];
        final var options = ToolCallingChatOptions.builder()
                .model("original")
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(true)
                .build();
        final var prompt = new Prompt(List.of(new UserMessage("test")), options);

        final Prompt result = FallbackChatModel.withModel(prompt, "fallback/new");

        assertThat(result.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        final var resultOpts = (ToolCallingChatOptions) result.getOptions();
        assertThat(resultOpts.getModel()).isEqualTo("fallback/new");
        assertThat(resultOpts.getInternalToolExecutionEnabled()).isTrue();
        assertThat(result.getInstructions()).hasSize(1);
    }

    @Test
    @DisplayName("streamWithFallback retries maxRetriesPerModel times per model")
    void streamWithFallback_retriesMaxTimes() {
        final ChatModel delegate = mock(ChatModel.class);
        when(delegate.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("primary down")))
                .thenReturn(Flux.error(new RuntimeException("fallback try 1")))
                .thenReturn(Flux.error(new RuntimeException("fallback try 2")))
                .thenReturn(Flux.just(OK_RESPONSE));

        final var props = new ModelFallbackProperties(true, List.of("fallback/a"), 3);
        final var model = new FallbackChatModel(delegate, props);

        StepVerifier.create(model.stream(testPrompt())).expectNext(OK_RESPONSE).verifyComplete();

        verify(delegate, times(4)).stream(any(Prompt.class));
    }

    @Test
    @DisplayName("withModel preserves temperature and topP for plain ChatOptions")
    void withModel_preservesOptions_plainChatOptions() {
        final Prompt original = new Prompt(
                "test",
                ChatOptions.builder()
                        .model("old")
                        .temperature(0.7)
                        .topP(0.9)
                        .maxTokens(100)
                        .build());

        final Prompt result = FallbackChatModel.withModel(original, "new-model");

        final ChatOptions opts = result.getOptions();
        assertThat(opts.getModel()).isEqualTo("new-model");
        assertThat(opts.getTemperature()).isEqualTo(0.7);
        assertThat(opts.getTopP()).isEqualTo(0.9);
        assertThat(opts.getMaxTokens()).isEqualTo(100);
    }

    @Test
    @DisplayName("withModel preserves temperature and topP for ToolCallingChatOptions")
    void withModel_preservesOptions_toolCallingChatOptions() {
        final var options = ToolCallingChatOptions.builder()
                .model("old")
                .temperature(0.5)
                .topP(0.8)
                .maxTokens(200)
                .internalToolExecutionEnabled(true)
                .build();
        final Prompt original = new Prompt(List.of(new UserMessage("hi")), options);

        final Prompt result = FallbackChatModel.withModel(original, "new-model");

        assertThat(result.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        final var resultOpts = (ToolCallingChatOptions) result.getOptions();
        assertThat(resultOpts.getModel()).isEqualTo("new-model");
        assertThat(resultOpts.getTemperature()).isEqualTo(0.5);
        assertThat(resultOpts.getTopP()).isEqualTo(0.8);
        assertThat(resultOpts.getMaxTokens()).isEqualTo(200);
        assertThat(resultOpts.getInternalToolExecutionEnabled()).isTrue();
    }

    @Test
    void propertiesDefaultsHandleNullModels() {
        final var props = new ModelFallbackProperties(false, null, 0);
        assertThat(props.models()).isEmpty();
        assertThat(props.maxRetriesPerModel()).isEqualTo(1);
    }

    private static Prompt testPrompt() {
        return new Prompt(
                List.of(new UserMessage("hello")),
                ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(true)
                        .build());
    }

    private static ModelFallbackProperties disabledProps() {
        return new ModelFallbackProperties(false, List.of(), 1);
    }
}
