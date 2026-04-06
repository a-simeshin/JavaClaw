package ai.javaclaw.agent.pipeline;

import static ai.javaclaw.agent.pipeline.MessageSanitizer.assistant;
import static ai.javaclaw.agent.pipeline.MessageSanitizer.assistantWithToolCalls;
import static ai.javaclaw.agent.pipeline.MessageSanitizer.toolResponse;
import static ai.javaclaw.agent.pipeline.MessageSanitizer.user;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * Unit tests for {@link MessageSanitizer}.
 *
 * <p>Each test targets a specific rule or combination of rules.  No Spring context is
 * required — the sanitizer is instantiated directly.
 */
class MessageSanitizerTest {

    /** Instance under test. */
    private MessageSanitizer sanitizer;

    /** Single tool call used across several test scenarios. */
    private static final AssistantMessage.ToolCall TOOL_CALL =
            new AssistantMessage.ToolCall("id-1", "function", "myTool", "{\"x\":1}");

    @BeforeEach
    void setUp() {
        sanitizer = new MessageSanitizer();
    }

    // -------------------------------------------------------------------------
    // Empty / trivial
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sanitize(): empty input → empty output")
    void sanitize_emptyInput_returnsEmptyList() {
        final List<Message> result = sanitizer.sanitize(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sanitize(): clean messages pass through unchanged (as new list)")
    void sanitize_cleanMessages_passthrough() {
        final List<Message> input = List.of(user("hi"), assistant("hello"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo("hi");
        assertThat(result.get(1).getText()).isEqualTo("hello");
    }

    @Test
    @DisplayName("sanitize(): result is unmodifiable")
    void sanitize_resultIsUnmodifiable() {
        final List<Message> result = sanitizer.sanitize(List.of(user("hi")));
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> result.add(user("extra")));
    }

    // -------------------------------------------------------------------------
    // Rule 1: Dedup
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 1 (Dedup): adjacent duplicate UserMessage is removed")
    void rule1_adjacentDuplicateUser_removed() {
        final List<Message> input = List.of(user("hi"), user("hi"), assistant("ok"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(result.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
    }

    @Test
    @DisplayName("Rule 1 (Dedup): same type but different text are both kept")
    void rule1_sameTypeDifferentText_bothKept() {
        final List<Message> input = List.of(user("hello"), user("world"));
        final List<Message> result = sanitizer.sanitize(input);

        // Rule 4 will also fire (consecutive users → keep last), so only "world" remains
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo("world");
    }

    // -------------------------------------------------------------------------
    // Rule 2: Orphan ToolResponseMessage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 2 (Orphan): ToolResponse after plain User is removed")
    void rule2_toolResponseAfterUser_removed() {
        final List<Message> input = List.of(user("q"), toolResponse("id-1", "myTool", "result"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.USER);
    }

    @Test
    @DisplayName("Rule 2 (Orphan): ToolResponse after AssistantMessage without tool calls is removed")
    void rule2_toolResponseAfterAssistantWithoutToolCalls_removed() {
        final List<Message> input = List.of(user("q"), assistant("sure"), toolResponse("id-1", "t", "r"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(result.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
    }

    @Test
    @DisplayName("Rule 2 (Orphan): ToolResponse at position 0 (no predecessor) is removed")
    void rule2_toolResponseAtStart_removed() {
        final List<Message> input = List.of(toolResponse("id-1", "t", "r"), user("hello"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.USER);
    }

    // -------------------------------------------------------------------------
    // Rule 3: Broken ToolCall
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 3 (Broken): AssistantMessage with tool calls not followed by ToolResponse → text kept")
    void rule3_brokenToolCall_textPreserved() {
        final AssistantMessage withTools = assistantWithToolCalls("I will call the tool", List.of(TOOL_CALL));
        final List<Message> input = List.of(user("do it"), withTools, assistant("done"));
        final List<Message> result = sanitizer.sanitize(input);

        // AssistantMessage with tool calls is stripped; becomes plain assistant("I will call the tool")
        assertThat(result).hasSize(3);
        final Message stripped = result.get(1);
        assertThat(stripped).isInstanceOf(AssistantMessage.class);
        assertThat(stripped.getText()).isEqualTo("I will call the tool");
        assertThat(((AssistantMessage) stripped).hasToolCalls()).isFalse();
    }

    @Test
    @DisplayName("Rule 3 (Broken): AssistantMessage with tool calls and no text not followed by ToolResponse → removed")
    void rule3_brokenToolCallNoText_removed() {
        final AssistantMessage withToolsNoText = assistantWithToolCalls("", List.of(TOOL_CALL));
        final List<Message> input = List.of(user("do it"), withToolsNoText, assistant("done"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(result.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(result.get(1).getText()).isEqualTo("done");
    }

    // -------------------------------------------------------------------------
    // Rule 4: Alternation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 4 (Alternation): two consecutive UserMessages → only last kept")
    void rule4_consecutiveUsers_onlyLastKept() {
        final List<Message> input = List.of(user("a"), user("b"), assistant("ok"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo("b");
        assertThat(result.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
    }

    @Test
    @DisplayName("Rule 4 (Alternation): three consecutive UserMessages → only last kept")
    void rule4_threeConsecutiveUsers_onlyLastKept() {
        final List<Message> input = List.of(user("a"), user("b"), user("c"), assistant("ok"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo("c");
    }

    // -------------------------------------------------------------------------
    // Tool chain preserved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tool chain [User, AssistantWithToolCall, ToolResponse, Assistant] → unchanged")
    void toolChain_preserved() {
        final AssistantMessage withTools = assistantWithToolCalls("calling tool", List.of(TOOL_CALL));
        final ToolResponseMessage toolResp = toolResponse("id-1", "myTool", "42");
        final List<Message> input = List.of(user("compute"), withTools, toolResp, assistant("the answer is 42"));
        final List<Message> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(4);
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(result.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(((AssistantMessage) result.get(1)).hasToolCalls()).isTrue();
        assertThat(result.get(2)).isInstanceOf(ToolResponseMessage.class);
        assertThat(result.get(3).getMessageType()).isEqualTo(MessageType.ASSISTANT);
    }

    // -------------------------------------------------------------------------
    // Mixed scenario
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Mixed: all four rules applied on messy sequence")
    void mixed_allRulesApplied() {
        /*
         * Input sequence (indexed):
         * 0: User("q")           — ok
         * 1: User("q")           — duplicate of 0, Rule 1 drops it
         * 2: ToolResponse("r")   — orphan (predecessor after dedup is User), Rule 2 drops it
         * 3: User("a")           — ok
         * 4: User("b")           — consecutive user, Rule 4 drops "a"
         * 5: Assistant("done")   — ok
         */
        final List<Message> input =
                List.of(user("q"), user("q"), toolResponse("id-1", "t", "r"), user("a"), user("b"), assistant("done"));

        final List<Message> result = sanitizer.sanitize(input);

        // After Rule 1 dedup: [User("q"), ToolResponse, User("a"), User("b"), Assistant("done")]
        // After Rule 2 orphan removal: [User("q"), User("a"), User("b"), Assistant("done")]
        // After Rule 3 (nothing to fix)
        // After Rule 4 alternation: User("q") followed by User("a") → drop "q";
        //   User("a") followed by User("b") → drop "a"; → [User("b"), Assistant("done")]
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo("b");
        assertThat(result.get(1).getText()).isEqualTo("done");
    }
}
