package ai.javaclaw.agent.pipeline;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

/**
 * Декоратор {@link ChatModel}, реализующий fallback-цепочку моделей.
 *
 * <p>При ошибке основной модели (любое исключение кроме {@link UnsupportedOperationException})
 * последовательно пробуются fallback-модели из сконфигурированного списка. Каждая модель
 * получает до {@code maxRetriesPerModel} попыток.
 *
 * <p>Работает прозрачно: если fallback отключён или список пуст — делегирует напрямую.
 */
public class FallbackChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatModel.class);

    private final ChatModel delegate;
    private final ModelFallbackProperties properties;

    public FallbackChatModel(final ChatModel delegate, final ModelFallbackProperties properties) {
        this.delegate = delegate;
        this.properties = properties;
    }

    @Override
    public ChatResponse call(final Prompt prompt) {
        try {
            return delegate.call(prompt);
        } catch (final Exception e) {
            if (!properties.enabled() || properties.models().isEmpty()) {
                throw e;
            }
            log.warn("Primary model failed: {}. Trying fallback chain...", e.getMessage());
            return callWithFallback(prompt, e);
        }
    }

    @Override
    public Flux<ChatResponse> stream(final Prompt prompt) {
        if (!properties.enabled() || properties.models().isEmpty()) {
            return delegate.stream(prompt);
        }
        return delegate.stream(prompt).onErrorResume(e -> {
            log.warn("Primary model stream failed: {}. Trying fallback chain...", e.getMessage());
            return streamWithFallback(prompt, (Exception) e);
        });
    }

    private ChatResponse callWithFallback(final Prompt original, final Exception primaryError) {
        final List<String> fallbackModels = properties.models();
        Exception lastError = primaryError;

        for (final String model : fallbackModels) {
            for (int attempt = 0; attempt < properties.maxRetriesPerModel(); attempt++) {
                try {
                    log.info(
                            "Fallback attempt: model={}, attempt={}/{}",
                            model,
                            attempt + 1,
                            properties.maxRetriesPerModel());
                    final Prompt fallbackPrompt = withModel(original, model);
                    return delegate.call(fallbackPrompt);
                } catch (final Exception e) {
                    log.warn(
                            "Fallback model {} attempt {}/{} failed: {}",
                            model,
                            attempt + 1,
                            properties.maxRetriesPerModel(),
                            e.getMessage());
                    lastError = e;
                }
            }
        }

        throw new ModelFallbackExhaustedException(
                "All fallback models exhausted. Last error: " + lastError.getMessage(), lastError);
    }

    private Flux<ChatResponse> streamWithFallback(final Prompt original, final Exception primaryError) {
        final List<String> fallbackModels = properties.models();
        if (fallbackModels.isEmpty()) {
            return Flux.error(primaryError);
        }

        Flux<ChatResponse> chain = Flux.error(primaryError);
        for (final String model : fallbackModels) {
            for (int attempt = 0; attempt < properties.maxRetriesPerModel(); attempt++) {
                final String fallbackModel = model;
                final int att = attempt + 1;
                chain = chain.onErrorResume(e -> {
                    log.info(
                            "Fallback stream: model={}, attempt={}/{}",
                            fallbackModel,
                            att,
                            properties.maxRetriesPerModel());
                    final Prompt fallbackPrompt = withModel(original, fallbackModel);
                    return delegate.stream(fallbackPrompt);
                });
            }
        }
        return chain;
    }

    /**
     * Создаёт копию Prompt с переопределённой моделью в ChatOptions.
     * Сохраняет tool callbacks и internalToolExecutionEnabled из оригинала.
     */
    static Prompt withModel(final Prompt original, final String model) {
        final ChatOptions originalOptions = original.getOptions();

        if (originalOptions instanceof ToolCallingChatOptions toolOpts) {
            final ToolCallingChatOptions newOptions = ToolCallingChatOptions.builder()
                    .model(model)
                    .temperature(toolOpts.getTemperature())
                    .topP(toolOpts.getTopP())
                    .topK(toolOpts.getTopK())
                    .maxTokens(toolOpts.getMaxTokens())
                    .stopSequences(toolOpts.getStopSequences())
                    .frequencyPenalty(toolOpts.getFrequencyPenalty())
                    .presencePenalty(toolOpts.getPresencePenalty())
                    .toolCallbacks(toolOpts.getToolCallbacks())
                    .internalToolExecutionEnabled(toolOpts.getInternalToolExecutionEnabled())
                    .build();
            return new Prompt(original.getInstructions(), newOptions);
        }

        if (originalOptions == null) {
            return new Prompt(
                    original.getInstructions(),
                    ChatOptions.builder().model(model).build());
        }

        return new Prompt(
                original.getInstructions(),
                ChatOptions.builder()
                        .model(model)
                        .temperature(originalOptions.getTemperature())
                        .topP(originalOptions.getTopP())
                        .topK(originalOptions.getTopK())
                        .maxTokens(originalOptions.getMaxTokens())
                        .stopSequences(originalOptions.getStopSequences())
                        .frequencyPenalty(originalOptions.getFrequencyPenalty())
                        .presencePenalty(originalOptions.getPresencePenalty())
                        .build());
    }

    /** Возвращает wrapped delegate для тестирования. */
    ChatModel getDelegate() {
        return delegate;
    }
}
