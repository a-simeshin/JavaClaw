package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Unit tests for {@link TurnBoundaryWindower}.
 *
 * <p>Verifies that windowing by turn boundary works correctly across all edge cases:
 * under budget, over budget, single-turn preservation, empty input, exact budget,
 * and that tool-call chains within a turn are never split.
 */
class TurnBoundaryWindowerTest {

    /** The instance under test. */
    private TurnBoundaryWindower windower;

    @BeforeEach
    void setUp() {
        windower = new TurnBoundaryWindower();
    }

    /**
     * Helper: creates a simple user message.
     */
    private static UserMessage user(final String text) {
        return new UserMessage(text);
    }

    /**
     * Helper: creates a simple assistant message.
     */
    private static AssistantMessage assistant(final String text) {
        return new AssistantMessage(text);
    }

    @Test
    @DisplayName("Under budget: all messages returned unchanged")
    void window_underBudget_returnsAllMessages() {
        final List<Message> messages = List.of(
                user("q1"), assistant("a1"),
                user("q2"), assistant("a2"),
                user("q3"), assistant("a3"),
                user("q4"), assistant("a4"),
                user("q5"), assistant("a5"));

        final List<Message> result = windower.window(messages, 20);

        assertThat(result).hasSize(10);
        assertThat(result).containsExactlyElementsOf(messages);
    }

    @Test
    @DisplayName("Over budget: oldest turns are dropped, last turn(s) kept")
    void window_overBudget_dropsOldestTurns() {
        // 3 turns, each with 2 messages → 6 total; maxMessages=3 should keep last 2 turns (4 msgs)
        // but since dropping turn-0 (2 msgs) leaves 4 > 3, drop turn-1 too → leaves last turn (2)
        final UserMessage u1 = user("q1");
        final AssistantMessage a1 = assistant("a1");
        final UserMessage u2 = user("q2");
        final AssistantMessage a2 = assistant("a2");
        final UserMessage u3 = user("q3");
        final AssistantMessage a3 = assistant("a3");

        final List<Message> messages = List.of(u1, a1, u2, a2, u3, a3);
        final List<Message> result = windower.window(messages, 3);

        // Only last turn should remain
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(u3);
        assertThat(result.get(1)).isSameAs(a3);
    }

    @Test
    @DisplayName("Tool chain preserved: [User, Assistant, Assistant] is one turn and never split")
    void window_toolChainPreserved_turnNotSplit() {
        // Turn 1: user + two assistant replies (simulates tool-call chain within a turn)
        final UserMessage u1 = user("old question");
        final AssistantMessage toolCall = assistant("[tool call]");
        final AssistantMessage toolReply = assistant("[tool result]");
        // Turn 2: the real current turn
        final UserMessage u2 = user("real question");
        final AssistantMessage a2 = assistant("real answer");

        // 5 messages total, budget 3 → turn-1 (3 msgs) dropped, turn-2 (2 msgs) kept
        final List<Message> messages = List.of(u1, toolCall, toolReply, u2, a2);
        final List<Message> result = windower.window(messages, 3);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(u2);
        assertThat(result.get(1)).isSameAs(a2);
    }

    @Test
    @DisplayName("Single turn: even if over budget, the last turn is always kept")
    void window_singleTurnOverBudget_keepsTurn() {
        final UserMessage u1 = user("big question");
        final AssistantMessage a1 = assistant("long answer 1");
        final AssistantMessage a2 = assistant("long answer 2");
        final AssistantMessage a3 = assistant("long answer 3");
        final AssistantMessage a4 = assistant("long answer 4");

        // 5 messages in a single turn, budget = 2 — must still return all 5
        final List<Message> messages = List.of(u1, a1, a2, a3, a4);
        final List<Message> result = windower.window(messages, 2);

        assertThat(result).hasSize(5);
        assertThat(result).containsExactlyElementsOf(messages);
    }

    @Test
    @DisplayName("Empty input: returns empty list")
    void window_emptyInput_returnsEmpty() {
        final List<Message> result = windower.window(Collections.emptyList(), 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Exact budget: returns all messages unchanged")
    void window_exactBudget_returnsAll() {
        final List<Message> messages = List.of(user("q1"), assistant("a1"), user("q2"), assistant("a2"), user("q3"));

        final List<Message> result = windower.window(messages, 5);

        assertThat(result).hasSize(5);
        assertThat(result).containsExactlyElementsOf(messages);
    }

    @Test
    @DisplayName("Result is unmodifiable")
    void window_resultIsUnmodifiable() {
        final List<Message> messages = List.of(user("q1"), assistant("a1"));
        final List<Message> result = windower.window(messages, 10);

        assertThatThrownBy(() -> result.add(user("extra"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Null messages throws IllegalArgumentException")
    void window_nullMessages_throws() {
        assertThatThrownBy(() -> windower.window(null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages must not be null");
    }

    @Test
    @DisplayName("Non-positive maxMessages throws IllegalArgumentException")
    void window_nonPositiveMaxMessages_throws() {
        assertThatThrownBy(() -> windower.window(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessages must be greater than 0");
    }

    @Test
    @DisplayName("Token-based window: drops oldest turns until token budget fits")
    void window_tokenBased_dropsOldestTurns() {
        final TokenEstimator estimator = new TokenEstimator();

        // Turn 1: "aaa" + "bbb" → ceil(3/3.5)*2 = 2 tokens
        // Turn 2: "ccc" + "ddd" → 2 tokens
        // Turn 3: "eee" + "fff" → 2 tokens
        // Total = 6 tokens; budget = 3 → drop until fits; keep at least 1 turn
        final UserMessage u1 = user("aaa");
        final AssistantMessage a1 = assistant("bbb");
        final UserMessage u2 = user("ccc");
        final AssistantMessage a2 = assistant("ddd");
        final UserMessage u3 = user("eee");
        final AssistantMessage a3 = assistant("fff");

        // Budget = 3 tokens: turn 3 fits (2 tokens ≤ 3), turn 2+3 = 4 > 3
        final List<Message> result = windower.window(List.of(u1, a1, u2, a2, u3, a3), 3, estimator);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(u3);
        assertThat(result.get(1)).isSameAs(a3);
    }

    @Test
    @DisplayName("Token-based window: keeps all messages when already within budget")
    void window_tokenBased_underBudget_returnsAll() {
        final TokenEstimator estimator = new TokenEstimator();
        final List<Message> messages = List.of(user("hi"), assistant("hello"));

        final List<Message> result = windower.window(messages, 100_000, estimator);

        assertThat(result).containsExactlyElementsOf(messages);
    }

    @Test
    @DisplayName("Token-based window: single turn always kept even over budget")
    void window_tokenBased_singleTurnOverBudget_keepsTurn() {
        final TokenEstimator estimator = new TokenEstimator();
        // Very long message — well over budget of 1 token
        final UserMessage u = user("This is a fairly long message that surely exceeds 1 token");
        final AssistantMessage a = assistant("And the answer is also quite lengthy indeed");

        final List<Message> result = windower.window(List.of(u, a), 1, estimator);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(u);
        assertThat(result.get(1)).isSameAs(a);
    }

    @Test
    @DisplayName("Token-based window: null messages throws IllegalArgumentException")
    void window_tokenBased_nullMessages_throws() {
        final TokenEstimator estimator = new TokenEstimator();
        assertThatThrownBy(() -> windower.window(null, 1000, estimator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages must not be null");
    }

    @Test
    @DisplayName("Token-based window: non-positive maxTokens throws IllegalArgumentException")
    void window_tokenBased_nonPositiveMaxTokens_throws() {
        final TokenEstimator estimator = new TokenEstimator();
        assertThatThrownBy(() -> windower.window(List.of(), 0, estimator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTokens must be greater than 0");
    }

    @Test
    @DisplayName("Token-based window: null estimator throws IllegalArgumentException")
    void window_tokenBased_nullEstimator_throws() {
        assertThatThrownBy(() -> windower.window(List.of(), 1000, (TokenEstimator) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("estimator must not be null");
    }

    @Test
    @DisplayName("Token-based window: result is unmodifiable")
    void window_tokenBased_resultIsUnmodifiable() {
        final TokenEstimator estimator = new TokenEstimator();
        final List<Message> result = windower.window(List.of(user("q"), assistant("a")), 100, estimator);

        assertThatThrownBy(() -> result.add(user("extra"))).isInstanceOf(UnsupportedOperationException.class);
    }
}
