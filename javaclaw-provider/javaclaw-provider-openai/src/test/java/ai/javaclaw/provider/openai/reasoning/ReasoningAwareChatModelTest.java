package ai.javaclaw.provider.openai.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link ReasoningAwareChatModel} — HTTP mocked via MockWebServer, delegate mocked via Mockito.
 * Проверяем: порядок chunks (reasoning → signature → content/tool), bypass по modelPattern / enabled=false,
 * error fallback, call() delegation.
 */
class ReasoningAwareChatModelTest {

    private MockWebServer mockWebServer;
    private ChatModel delegate;
    private ReasoningAwareChatModel model;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient =
                WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();

        delegate = Mockito.mock(ChatModel.class);

        ObjectMapper objectMapper = new ObjectMapper();
        ReasoningProperties props = ReasoningProperties.defaults();
        ReasoningRequestBuilder requestBuilder = new ReasoningRequestBuilder(objectMapper);
        ReasoningChunkParser parser = new ReasoningChunkParser(objectMapper);

        model = new ReasoningAwareChatModel(delegate, webClient, props, requestBuilder, parser);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private Prompt promptWithModel(String modelName) {
        OpenAiChatOptions opts = OpenAiChatOptions.builder().model(modelName).build();
        return new Prompt(List.of(new UserMessage("hello")), opts);
    }

    private MockResponse sseResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body);
    }

    /** Формирует SSE тело с корректными \n\n разделителями между событиями. */
    private String buildSseBody(String... dataLines) {
        StringBuilder sb = new StringBuilder();
        for (String line : dataLines) {
            sb.append("data: ").append(line).append("\n\n");
        }
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // 1. Reasoning → content — signature injected
    // -------------------------------------------------------------------------
    @Test
    void stream_reasoningThenContent_emitsSignatureBeforeContent() {
        String sse = buildSseBody(
                "{\"choices\":[{\"delta\":{\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":\"let me think\",\"id\":\"r-1\"}]}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"Answer: 42\"}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
        mockWebServer.enqueue(sseResponse(sse));

        Flux<ChatResponse> flux = model.stream(promptWithModel("minimax/minimax-m2.7"));

        StepVerifier.create(flux)
                .assertNext(resp -> {
                    AssistantMessage msg = resp.getResult().getOutput();
                    assertThat(msg.getText()).isEqualTo("let me think");
                    assertThat(msg.getMetadata()).containsEntry("thinking", true);
                })
                .assertNext(resp -> {
                    AssistantMessage msg = resp.getResult().getOutput();
                    assertThat(msg.getMetadata()).containsKey("signature");
                    assertThat(msg.getMetadata().get("signature")).isEqualTo("r-1");
                })
                .assertNext(resp -> {
                    AssistantMessage msg = resp.getResult().getOutput();
                    assertThat(msg.getText()).isEqualTo("Answer: 42");
                    assertThat(msg.getMetadata()).doesNotContainKey("thinking");
                })
                .assertNext(resp -> {
                    // finish_reason chunk → empty AssistantMessage
                    assertThat(resp.getResult().getOutput().getText()).isEmpty();
                })
                .verifyComplete();

        verify(delegate, never()).stream(any(Prompt.class));
    }

    // -------------------------------------------------------------------------
    // 2. Reasoning → tool_call — signature injected
    // -------------------------------------------------------------------------
    @Test
    void stream_reasoningThenToolCall_emitsSignatureBeforeToolCall() {
        String sse = buildSseBody(
                "{\"choices\":[{\"delta\":{\"reasoning\":\"plan tool\"}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"search\",\"arguments\":\"{}\"}}]}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}");
        mockWebServer.enqueue(sseResponse(sse));

        Flux<ChatResponse> flux = model.stream(promptWithModel("deepseek-chat"));

        StepVerifier.create(flux)
                .assertNext(resp -> {
                    AssistantMessage msg = resp.getResult().getOutput();
                    assertThat(msg.getText()).isEqualTo("plan tool");
                    assertThat(msg.getMetadata()).containsEntry("thinking", true);
                })
                .assertNext(resp -> {
                    AssistantMessage msg = resp.getResult().getOutput();
                    assertThat(msg.getMetadata()).containsKey("signature");
                })
                .assertNext(resp -> {
                    AssistantMessage msg = resp.getResult().getOutput();
                    assertThat(msg.hasToolCalls()).isTrue();
                    assertThat(msg.getToolCalls()).hasSize(1);
                    assertThat(msg.getToolCalls().get(0).name()).isEqualTo("search");
                })
                .assertNext(resp ->
                        assertThat(resp.getResult().getOutput().getText()).isEmpty()) // finish
                .verifyComplete();

        verify(delegate, never()).stream(any(Prompt.class));
    }

    // -------------------------------------------------------------------------
    // 3. Pure reasoning + finish — signature на finish
    // -------------------------------------------------------------------------
    @Test
    void stream_pureReasoningNoContent_finalizesCleanly() {
        String sse = buildSseBody(
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"only thoughts\"}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
        mockWebServer.enqueue(sseResponse(sse));

        Flux<ChatResponse> flux = model.stream(promptWithModel("gpt-5-reasoning"));

        StepVerifier.create(flux)
                .assertNext(resp -> {
                    AssistantMessage msg = resp.getResult().getOutput();
                    assertThat(msg.getText()).isEqualTo("only thoughts");
                    assertThat(msg.getMetadata()).containsEntry("thinking", true);
                })
                .assertNext(resp -> {
                    AssistantMessage msg = resp.getResult().getOutput();
                    assertThat(msg.getMetadata()).containsKey("signature");
                })
                .assertNext(resp ->
                        assertThat(resp.getResult().getOutput().getText()).isEmpty())
                .verifyComplete();

        verify(delegate, never()).stream(any(Prompt.class));
    }

    // -------------------------------------------------------------------------
    // 4. Model not matching → bypass to delegate
    // -------------------------------------------------------------------------
    @Test
    void stream_modelNotMatchingPattern_bypassesToDelegate() {
        Prompt prompt = promptWithModel("gpt-4");
        Flux<ChatResponse> delegated =
                Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("from delegate")))));
        when(delegate.stream(prompt)).thenReturn(delegated);

        Flux<ChatResponse> flux = model.stream(prompt);

        StepVerifier.create(flux)
                .assertNext(resp ->
                        assertThat(resp.getResult().getOutput().getText()).isEqualTo("from delegate"))
                .verifyComplete();

        verify(delegate, times(1)).stream(prompt);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // 4b. Claude Haiku (не поддерживает reasoning) → bypass to delegate
    // -------------------------------------------------------------------------
    @Test
    void stream_claudeHaiku_bypassesToDelegate() {
        Prompt prompt = promptWithModel("anthropic/claude-3-5-haiku");
        Flux<ChatResponse> delegated =
                Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("haiku response")))));
        when(delegate.stream(prompt)).thenReturn(delegated);

        StepVerifier.create(model.stream(prompt))
                .assertNext(resp ->
                        assertThat(resp.getResult().getOutput().getText()).isEqualTo("haiku response"))
                .verifyComplete();

        verify(delegate, times(1)).stream(prompt);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // 5. Reasoning disabled → bypass to delegate
    // -------------------------------------------------------------------------
    @Test
    void stream_reasoningDisabled_bypassesToDelegate() {
        WebClient webClient =
                WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        ChatModel disabledDelegate = Mockito.mock(ChatModel.class);
        ObjectMapper om = new ObjectMapper();
        ReasoningProperties disabledProps = new ReasoningProperties(false, "medium", false, null, null);
        ReasoningAwareChatModel disabledModel = new ReasoningAwareChatModel(
                disabledDelegate,
                webClient,
                disabledProps,
                new ReasoningRequestBuilder(om),
                new ReasoningChunkParser(om));

        Prompt prompt = promptWithModel("minimax/minimax-m2.7");
        Flux<ChatResponse> delegated =
                Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("delegated")))));
        when(disabledDelegate.stream(prompt)).thenReturn(delegated);

        StepVerifier.create(disabledModel.stream(prompt))
                .assertNext(resp ->
                        assertThat(resp.getResult().getOutput().getText()).isEqualTo("delegated"))
                .verifyComplete();

        verify(disabledDelegate, times(1)).stream(prompt);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // 6. Provider 500 error → fallback to delegate
    // -------------------------------------------------------------------------
    @Test
    void stream_providerError_propagatesToFallback() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"boom\"}"));

        Prompt prompt = promptWithModel("claude-opus-4");
        Flux<ChatResponse> delegated =
                Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("fallback response")))));
        when(delegate.stream(any(Prompt.class))).thenReturn(delegated);

        StepVerifier.create(model.stream(prompt))
                .assertNext(resp ->
                        assertThat(resp.getResult().getOutput().getText()).isEqualTo("fallback response"))
                .verifyComplete();

        verify(delegate, times(1)).stream(any(Prompt.class));
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // 7. call() — delegates to wrapped model
    // -------------------------------------------------------------------------
    @Test
    void call_delegatesToWrappedModel() {
        Prompt prompt = promptWithModel("minimax/minimax-m2.7");
        ChatResponse expected = new ChatResponse(List.of(new Generation(new AssistantMessage("non-stream result"))));
        when(delegate.call(prompt)).thenReturn(expected);

        ChatResponse actual = model.call(prompt);

        assertThat(actual).isSameAs(expected);
        verify(delegate, times(1)).call(prompt);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }
}
