package ai.javaclaw.provider.openai.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReasoningChunkParserTest {

    private ReasoningChunkParser parser;

    @BeforeEach
    void setUp() {
        parser = new ReasoningChunkParser(new ObjectMapper());
    }

    @Test
    void parse_reasoningDetailsArray_extractsText() {
        String line =
                "data: {\"choices\":[{\"delta\":{\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":\"думаю\",\"id\":\"r1\"}]}}]}";
        Optional<ReasoningChunkParser.ParsedChunk> result = parser.parse(line);

        assertThat(result).isPresent();
        assertThat(result.get().reasoningText()).contains("думаю");
        assertThat(result.get().reasoningId()).contains("r1");
    }

    @Test
    void parse_reasoningLegacyField_extractsText() {
        String line = "data: {\"choices\":[{\"delta\":{\"reasoning\":\"legacy text\"}}]}";
        Optional<ReasoningChunkParser.ParsedChunk> result = parser.parse(line);

        assertThat(result).isPresent();
        assertThat(result.get().reasoningText()).contains("legacy text");
    }

    @Test
    void parse_reasoningContentField_extractsText() {
        String line = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"minimax reasoning\"}}]}";
        Optional<ReasoningChunkParser.ParsedChunk> result = parser.parse(line);

        assertThat(result).isPresent();
        assertThat(result.get().reasoningText()).contains("minimax reasoning");
    }

    @Test
    void parse_contentOnlyChunk_noReasoning() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}";
        Optional<ReasoningChunkParser.ParsedChunk> result = parser.parse(line);

        assertThat(result).isPresent();
        assertThat(result.get().contentText()).contains("hello");
        assertThat(result.get().reasoningText()).isEmpty();
    }

    @Test
    void parse_mixedChunk_preferDetailsOverLegacy() {
        String line =
                "data: {\"choices\":[{\"delta\":{\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":\"from details\",\"id\":\"r2\"}],\"reasoning\":\"from legacy\"}}]}";
        Optional<ReasoningChunkParser.ParsedChunk> result = parser.parse(line);

        assertThat(result).isPresent();
        assertThat(result.get().reasoningText()).contains("from details");
        assertThat(result.get().reasoningText().get()).doesNotContain("from legacy");
    }

    @Test
    void parse_toolCallDelta_extractsToolCalls() {
        String line =
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"tc1\",\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"q\\\":\\\"test\\\"}\"}}]}}]}";
        Optional<ReasoningChunkParser.ParsedChunk> result = parser.parse(line);

        assertThat(result).isPresent();
        assertThat(result.get().hasTools()).isTrue();
        assertThat(result.get().tools()).hasSize(1);
        assertThat(result.get().tools().get(0).name()).isEqualTo("search");
        assertThat(result.get().tools().get(0).id()).isEqualTo("tc1");
    }

    @Test
    void parse_finishChunk_withUsage() {
        String line =
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
        Optional<ReasoningChunkParser.ParsedChunk> result = parser.parse(line);

        assertThat(result).isPresent();
        assertThat(result.get().finishReason()).contains("stop");
        assertThat(result.get().usage()).isPresent();
        assertThat(result.get().usage().get().promptTokens()).isEqualTo(10);
        assertThat(result.get().usage().get().completionTokens()).isEqualTo(5);
        assertThat(result.get().usage().get().totalTokens()).isEqualTo(15);
    }

    @Test
    void parse_doneMarker_returnsEmpty() {
        String line = "data: [DONE]";
        Optional<ReasoningChunkParser.ParsedChunk> result = parser.parse(line);

        assertThat(result).isEmpty();
    }
}
