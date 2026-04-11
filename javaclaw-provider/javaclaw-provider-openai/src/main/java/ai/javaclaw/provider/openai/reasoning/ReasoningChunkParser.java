package ai.javaclaw.provider.openai.reasoning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * Парсер SSE-строк из OpenAI-compatible streaming response.
 * Поддерживает три формата reasoning-полей:
 * 1. choices[0].delta.reasoning_details[] (OpenRouter новый формат)
 * 2. choices[0].delta.reasoning (OpenRouter legacy)
 * 3. choices[0].delta.reasoning_content (MiniMax/DeepSeek)
 */
public class ReasoningChunkParser {

    private final ObjectMapper objectMapper;

    public ReasoningChunkParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses a single SSE line.
     * @param sseLine raw SSE line, e.g. "data: {...}" or "data: [DONE]"
     * @return parsed chunk, or empty if line is [DONE], empty, or not data:
     */
    public Optional<ParsedChunk> parse(String sseLine) {
        if (sseLine == null || sseLine.isBlank()) return Optional.empty();
        if (!sseLine.startsWith("data:")) return Optional.empty();

        String json = sseLine.substring(5).strip();
        if ("[DONE]".equals(json)) return Optional.empty();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || choices.isEmpty()) {
                return Optional.of(buildUsageOnly(root));
            }

            JsonNode delta = choices.get(0).path("delta");
            String finishReason = choices.get(0).path("finish_reason").asText(null);

            Optional<String> reasoningText = extractReasoning(delta);
            Optional<String> reasoningId = extractReasoningId(delta);

            Optional<String> contentText = Optional.empty();
            JsonNode contentNode = delta.path("content");
            if (!contentNode.isMissingNode()
                    && !contentNode.isNull()
                    && !contentNode.asText("").isEmpty()) {
                contentText = Optional.of(contentNode.asText());
            }

            List<AssistantMessage.ToolCall> tools = extractToolCalls(delta);
            Optional<UsageData> usage = extractUsage(root);

            return Optional.of(new ParsedChunk(
                    reasoningText,
                    reasoningId,
                    contentText,
                    tools,
                    Optional.ofNullable(finishReason).filter(s -> !s.isBlank() && !"null".equals(s)),
                    usage));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> extractReasoning(JsonNode delta) {
        // Priority 1: reasoning_details[]
        JsonNode details = delta.path("reasoning_details");
        if (!details.isMissingNode() && details.isArray() && !details.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode detail : details) {
                String type = detail.path("type").asText("");
                if ("reasoning.text".equals(type)) {
                    String text = detail.path("text").asText("");
                    if (!text.isEmpty()) sb.append(text);
                }
            }
            if (sb.length() > 0) return Optional.of(sb.toString());
        }
        // Priority 2: reasoning (legacy OpenRouter)
        JsonNode reasoning = delta.path("reasoning");
        if (!reasoning.isMissingNode() && !reasoning.isNull()) {
            String text = reasoning.asText("");
            if (!text.isEmpty()) return Optional.of(text);
        }
        // Priority 3: reasoning_content (MiniMax/DeepSeek)
        JsonNode reasoningContent = delta.path("reasoning_content");
        if (!reasoningContent.isMissingNode() && !reasoningContent.isNull()) {
            String text = reasoningContent.asText("");
            if (!text.isEmpty()) return Optional.of(text);
        }
        return Optional.empty();
    }

    private Optional<String> extractReasoningId(JsonNode delta) {
        JsonNode details = delta.path("reasoning_details");
        if (!details.isMissingNode() && details.isArray() && !details.isEmpty()) {
            JsonNode id = details.get(0).path("id");
            if (!id.isMissingNode() && !id.isNull()) return Optional.of(id.asText());
        }
        return Optional.empty();
    }

    private List<AssistantMessage.ToolCall> extractToolCalls(JsonNode delta) {
        List<AssistantMessage.ToolCall> result = new ArrayList<>();
        JsonNode toolCallsNode = delta.path("tool_calls");
        if (toolCallsNode.isMissingNode() || !toolCallsNode.isArray()) return result;
        for (JsonNode tc : toolCallsNode) {
            String id = tc.path("id").asText("");
            String name = tc.path("function").path("name").asText("");
            String args = tc.path("function").path("arguments").asText("");
            if (!id.isBlank() || !name.isBlank() || !args.isBlank()) {
                result.add(new AssistantMessage.ToolCall(id, "function", name, args));
            }
        }
        return result;
    }

    private Optional<UsageData> extractUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) return Optional.empty();
        return Optional.of(new UsageData(
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                usage.path("total_tokens").asInt(0)));
    }

    private ParsedChunk buildUsageOnly(JsonNode root) {
        return new ParsedChunk(
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty(), extractUsage(root));
    }

    /** Parsed data from a single SSE chunk. */
    public record ParsedChunk(
            Optional<String> reasoningText,
            Optional<String> reasoningId,
            Optional<String> contentText,
            List<AssistantMessage.ToolCall> tools,
            Optional<String> finishReason,
            Optional<UsageData> usage) {
        public boolean hasReasoning() {
            return reasoningText.isPresent();
        }

        public boolean hasContent() {
            return contentText.isPresent();
        }

        public boolean hasTools() {
            return !tools.isEmpty();
        }

        public boolean hasMeaningfulData() {
            return hasReasoning() || hasContent() || hasTools() || finishReason.isPresent();
        }
    }

    public record UsageData(int promptTokens, int completionTokens, int totalTokens) {}
}
