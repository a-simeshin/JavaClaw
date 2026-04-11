package ai.javaclaw.provider.openai.reasoning;

import ai.javaclaw.agent.pipeline.ReasoningCapableChatModel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Decorator {@link ChatModel} добавляющий поддержку reasoning-токенов для OpenAI-compat провайдеров.
 * Реализует маркерный интерфейс {@link ReasoningCapableChatModel} для DI приоритизации
 * в ChatServiceConfiguration.
 *
 * <p>Non-stream путь ({@code call}) — делегируется обёрнутому ChatModel.
 *
 * <p>Stream путь ({@code stream}) — при включённом reasoning и матчинге modelPattern
 * открывает собственный WebClient SSE канал на {@code /v1/chat/completions}, парсит
 * reasoning-поля через {@link ReasoningChunkParser}, эмитит {@link ChatResponse} с
 * метадатой {@code thinking=true} для reasoning-токенов и сигнатурным чанком перед
 * первым non-reasoning токеном для закрытия блока в SseStreamingService.
 *
 * <p>При любых ошибках (HTTP, parsing) — onErrorResume делегирует на обёрнутый ChatModel.
 */
public class ReasoningAwareChatModel implements ChatModel, ReasoningCapableChatModel {

    private static final Logger log = LoggerFactory.getLogger(ReasoningAwareChatModel.class);

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final ChatModel delegate;
    private final WebClient webClient;
    private final ReasoningProperties props;
    private final ReasoningRequestBuilder requestBuilder;
    private final ReasoningChunkParser parser;
    private final Pattern modelPattern;
    private final String defaultModel;

    public ReasoningAwareChatModel(
            ChatModel delegate,
            WebClient webClient,
            ReasoningProperties props,
            ReasoningRequestBuilder requestBuilder,
            ReasoningChunkParser parser) {
        this(delegate, webClient, props, requestBuilder, parser, null);
    }

    public ReasoningAwareChatModel(
            ChatModel delegate,
            WebClient webClient,
            ReasoningProperties props,
            ReasoningRequestBuilder requestBuilder,
            ReasoningChunkParser parser,
            String defaultModel) {
        this.delegate = delegate;
        this.webClient = webClient;
        this.props = props;
        this.requestBuilder = requestBuilder;
        this.parser = parser;
        this.modelPattern = Pattern.compile(props.modelPattern());
        this.defaultModel = defaultModel;
        log.debug(
                "ReasoningAwareChatModel initialized: enabled={}, pattern={}, defaultModel={}, delegate={}",
                props.enabled(),
                props.modelPattern(),
                defaultModel,
                delegate.getClass().getSimpleName());
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate.call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        if (!shouldHandleReasoning(prompt)) {
            return delegate.stream(prompt);
        }
        if (log.isDebugEnabled()) {
            log.debug("ReasoningAwareChatModel reasoning path engaged: model={}", resolveModel(prompt));
        }

        final String body;
        try {
            body = requestBuilder.build(prompt, props, resolveModel(prompt));
        } catch (Exception e) {
            log.warn("Failed to build reasoning request body; falling back to delegate", e);
            return delegate.stream(prompt);
        }

        final AtomicReference<String> currentReasoningId = new AtomicReference<>(null);
        final AtomicBoolean inReasoning = new AtomicBoolean(false);

        return webClient
                .post()
                .uri("/v1/chat/completions")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(SSE_TYPE)
                .flatMap(event -> {
                    String data = event.data();
                    if (data == null || data.isBlank()) {
                        return Flux.empty();
                    }
                    // ReasoningChunkParser ожидает формат "data: {json}"
                    String sseLine = "data: " + data;
                    Optional<ReasoningChunkParser.ParsedChunk> parsed = parser.parse(sseLine);
                    if (parsed.isEmpty()) {
                        return Flux.empty();
                    }
                    ReasoningChunkParser.ParsedChunk chunk = parsed.get();
                    if (!chunk.hasMeaningfulData()) {
                        return Flux.empty();
                    }
                    return mapChunkToResponses(chunk, inReasoning, currentReasoningId);
                })
                .onErrorResume(e -> {
                    log.warn("Reasoning stream failed: {}. Falling back to delegate.", e.getMessage());
                    return delegate.stream(prompt);
                });
    }

    private boolean shouldHandleReasoning(Prompt prompt) {
        if (!props.enabled()) return false;
        String model = resolveModel(prompt);
        if (model == null) return false;
        return modelPattern.matcher(model).matches();
    }

    /**
     * Определяет имя модели, которое будет использовано в запросе.
     * Приоритет: prompt.options.model → delegate.defaultOptions.model → configured defaultModel.
     *
     * <p>ChatService.buildPrompt строит ToolCallingChatOptions без явного указания модели
     * (если нет override), поэтому fallback на defaultOptions/defaultModel критичен.
     */
    private String resolveModel(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        if (options != null) {
            String m = options.getModel();
            if (m != null && !m.isBlank()) return m;
        }
        // Fallback: default options обёрнутой модели (OpenAiChatModel хранит здесь
        // spring.ai.openai.chat.options.model).
        try {
            ChatOptions delegateDefaults = delegate.getDefaultOptions();
            if (delegateDefaults != null) {
                String m = delegateDefaults.getModel();
                if (m != null && !m.isBlank()) return m;
            }
        } catch (Exception ignored) {
            // не критично
        }
        return defaultModel;
    }

    private Flux<ChatResponse> mapChunkToResponses(
            ReasoningChunkParser.ParsedChunk chunk,
            AtomicBoolean inReasoning,
            AtomicReference<String> currentReasoningId) {

        // 1. Reasoning chunk — эмит AssistantMessage с thinking=true
        if (chunk.hasReasoning()) {
            if (!inReasoning.get()) {
                inReasoning.set(true);
                chunk.reasoningId().ifPresent(currentReasoningId::set);
            }
            AssistantMessage msg = AssistantMessage.builder()
                    .content(chunk.reasoningText().orElse(""))
                    .properties(Map.of("thinking", true))
                    .build();
            return Flux.just(new ChatResponse(List.of(new Generation(msg))));
        }

        // 2. Первый non-reasoning чанк после reasoning — эмит signature для закрытия блока
        Flux<ChatResponse> signaturePrefix = Flux.empty();
        if (inReasoning.get()
                && (chunk.hasContent()
                        || chunk.hasTools()
                        || chunk.finishReason().isPresent())) {
            String sigId = currentReasoningId.get() != null ? currentReasoningId.get() : "reasoning-default";
            AssistantMessage sigMsg = AssistantMessage.builder()
                    .content("")
                    .properties(Map.of("signature", sigId))
                    .build();
            signaturePrefix = Flux.just(new ChatResponse(List.of(new Generation(sigMsg))));
            inReasoning.set(false);
            currentReasoningId.set(null);
        }

        // 3. Content / tool_calls / finish
        if (chunk.hasContent()) {
            AssistantMessage msg = new AssistantMessage(chunk.contentText().orElse(""));
            return signaturePrefix.concatWith(Flux.just(new ChatResponse(List.of(new Generation(msg)))));
        }
        if (chunk.hasTools()) {
            AssistantMessage msg = AssistantMessage.builder()
                    .content("")
                    .toolCalls(chunk.tools())
                    .build();
            return signaturePrefix.concatWith(Flux.just(new ChatResponse(List.of(new Generation(msg)))));
        }
        if (chunk.finishReason().isPresent()) {
            AssistantMessage msg = new AssistantMessage("");
            return signaturePrefix.concatWith(Flux.just(new ChatResponse(List.of(new Generation(msg)))));
        }
        // usage-only chunk — пропускаем
        return signaturePrefix;
    }
}
