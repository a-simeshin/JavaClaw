package ai.javaclaw.provider.openai.reasoning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Строит OpenAI-compatible request body из Spring AI Prompt + ReasoningProperties.
 * Добавляет блок "reasoning": {"effort": ..., "max_tokens": ..., "exclude": ...}
 * поверх стандартного OpenAI chat completions request.
 */
public class ReasoningRequestBuilder {

    private final ObjectMapper objectMapper;

    public ReasoningRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the full request body as a JSON string.
     *
     * @param prompt Spring AI Prompt (contains messages + options)
     * @param props  reasoning configuration
     * @return JSON string suitable for HTTP POST body
     */
    public String build(Prompt prompt, ReasoningProperties props) {
        return build(prompt, props, null);
    }

    /**
     * Overload that accepts an explicitly resolved model name, used by
     * {@code ReasoningAwareChatModel} когда prompt.options не содержит модель
     * (ChatService.buildPrompt строит ToolCallingChatOptions без model).
     */
    public String build(Prompt prompt, ReasoningProperties props, String resolvedModel) {
        ObjectNode root = objectMapper.createObjectNode();

        // Extract model — resolvedModel (from delegate defaults) → prompt.options.model → fallback.
        String model = resolvedModel;
        ChatOptions options = prompt.getOptions();
        if ((model == null || model.isBlank()) && options != null) {
            model = options.getModel();
        }
        if (model == null || model.isBlank()) model = "minimax/minimax-m2.7"; // fallback
        root.put("model", model);

        // Build messages array
        var messagesArray = root.putArray("messages");
        for (var msg : prompt.getInstructions()) {
            var msgNode = messagesArray.addObject();
            msgNode.put(
                    "role",
                    switch (msg.getMessageType()) {
                        case USER -> "user";
                        case ASSISTANT -> "assistant";
                        case SYSTEM -> "system";
                        case TOOL -> "tool";
                    });
            msgNode.put("content", msg.getText());
        }

        // Add stream
        root.put("stream", true);
        root.set("stream_options", objectMapper.createObjectNode().put("include_usage", true));

        // Add options — пытаемся сначала через OpenAiChatOptions (для обратной совместимости),
        // затем через базовый ChatOptions (temperature/topP/maxTokens доступны в нём начиная с Spring AI 1.0).
        if (options instanceof OpenAiChatOptions opts) {
            if (opts.getTemperature() != null)
                root.put("temperature", opts.getTemperature().doubleValue());
            if (opts.getMaxTokens() != null) root.put("max_tokens", opts.getMaxTokens());
            if (opts.getTopP() != null) root.put("top_p", opts.getTopP().doubleValue());
        } else if (options != null) {
            try {
                if (options.getTemperature() != null)
                    root.put("temperature", options.getTemperature().doubleValue());
            } catch (Exception ignored) {
                // некоторые реализации могут не поддерживать — пропускаем
            }
            try {
                if (options.getMaxTokens() != null) root.put("max_tokens", options.getMaxTokens());
            } catch (Exception ignored) {
                // пропускаем
            }
            try {
                if (options.getTopP() != null)
                    root.put("top_p", options.getTopP().doubleValue());
            } catch (Exception ignored) {
                // пропускаем
            }
        }

        // Add reasoning block
        if (props.enabled()) {
            ObjectNode reasoningNode = objectMapper.createObjectNode();
            if (props.maxTokens() != null) {
                reasoningNode.put("max_tokens", props.maxTokens());
            } else {
                reasoningNode.put("effort", props.effort());
            }
            if (props.exclude()) {
                reasoningNode.put("exclude", true);
            }
            root.set("reasoning", reasoningNode);
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize request body", e);
        }
    }
}
