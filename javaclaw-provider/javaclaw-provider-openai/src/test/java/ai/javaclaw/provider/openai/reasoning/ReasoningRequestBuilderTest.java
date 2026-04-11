package ai.javaclaw.provider.openai.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

class ReasoningRequestBuilderTest {

    private ReasoningRequestBuilder builder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        builder = new ReasoningRequestBuilder(objectMapper);
    }

    // 1. effort=medium → reasoning block с effort, без max_tokens в reasoning
    @Test
    void build_withEffortMedium_injectsReasoningBlock() throws Exception {
        ReasoningProperties props = new ReasoningProperties(true, "medium", false, null, null);
        Prompt prompt = new Prompt(new UserMessage("hello"));

        String json = builder.build(prompt, props);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.has("reasoning")).isTrue();
        JsonNode reasoning = root.get("reasoning");
        assertThat(reasoning.get("effort").asText()).isEqualTo("medium");
        assertThat(reasoning.has("max_tokens")).isFalse();
    }

    // 2. maxTokens=2000 → reasoning block с max_tokens, без effort
    @Test
    void build_withMaxTokens_injectsMaxTokens() throws Exception {
        ReasoningProperties props = new ReasoningProperties(true, "medium", false, 2000, null);
        Prompt prompt = new Prompt(new UserMessage("hello"));

        String json = builder.build(prompt, props);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.has("reasoning")).isTrue();
        JsonNode reasoning = root.get("reasoning");
        assertThat(reasoning.get("max_tokens").asInt()).isEqualTo(2000);
        assertThat(reasoning.has("effort")).isFalse();
    }

    // 3. exclude=true → reasoning содержит "exclude": true
    @Test
    void build_withExcludeTrue_injectsExclude() throws Exception {
        ReasoningProperties props = new ReasoningProperties(true, "high", true, null, null);
        Prompt prompt = new Prompt(new UserMessage("hello"));

        String json = builder.build(prompt, props);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.has("reasoning")).isTrue();
        JsonNode reasoning = root.get("reasoning");
        assertThat(reasoning.get("exclude").asBoolean()).isTrue();
    }

    // 4. enabled=false → нет поля "reasoning"
    @Test
    void build_reasoningDisabled_noReasoningField() throws Exception {
        ReasoningProperties props = new ReasoningProperties(false, "medium", false, null, null);
        Prompt prompt = new Prompt(new UserMessage("hello"));

        String json = builder.build(prompt, props);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.has("reasoning")).isFalse();
    }

    // 5. model и messages корректно пробрасываются
    @Test
    void build_preservesModelAndMessages() throws Exception {
        ReasoningProperties props = new ReasoningProperties(false, "medium", false, null, null);

        OpenAiChatOptions options =
                OpenAiChatOptions.builder().model("minimax/minimax-m2.7").build();

        Prompt prompt = new Prompt(List.of(new SystemMessage("You are helpful."), new UserMessage("Hi!")), options);

        String json = builder.build(prompt, props);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("model").asText()).isEqualTo("minimax/minimax-m2.7");
        assertThat(root.get("stream").asBoolean()).isTrue();

        JsonNode messages = root.get("messages");
        assertThat(messages).isNotNull();
        assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("You are helpful.");
        assertThat(messages.get(1).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).get("content").asText()).isEqualTo("Hi!");
    }
}
