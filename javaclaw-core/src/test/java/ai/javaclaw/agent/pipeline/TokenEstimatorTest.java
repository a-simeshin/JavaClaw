package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Unit tests for {@link TokenEstimator}.
 *
 * <p>Verifies the character-based token estimation heuristic for individual messages and lists.
 */
class TokenEstimatorTest {

    /** The instance under test. */
    private TokenEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new TokenEstimator();
    }

    @Test
    @DisplayName("estimate(Message): message with empty text returns 0")
    void estimate_message_emptyText_returnsZero() {
        final Message message = new UserMessage("");

        final int result = estimator.estimate(message);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("estimate(Message): null message returns 0")
    void estimate_message_null_returnsZero() {
        final int result = estimator.estimate((Message) null);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("estimate(Message): known text returns ceil(length / 3.5)")
    void estimate_message_knownText_returnsCeil() {
        // "Hello" = 5 chars → ceil(5 / 3.5) = ceil(1.428...) = 2
        final Message message = new UserMessage("Hello");

        final int result = estimator.estimate(message);

        assertThat(result).isEqualTo((int) Math.ceil(5 / 3.5));
    }

    @Test
    @DisplayName("estimate(Message): 7-char text exactly divisible gives exact integer")
    void estimate_message_sevenChars_returnsTwo() {
        // 7 chars → ceil(7 / 3.5) = ceil(2.0) = 2
        final Message message = new AssistantMessage("1234567");

        final int result = estimator.estimate(message);

        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("estimate(List): null list returns 0")
    void estimate_list_null_returnsZero() {
        final int result = estimator.estimate((List<Message>) null);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("estimate(List): empty list returns 0")
    void estimate_list_empty_returnsZero() {
        final int result = estimator.estimate(List.of());

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("estimate(List): list of messages returns sum of individual estimates")
    void estimate_list_multipleMessages_returnsSum() {
        final Message m1 = new UserMessage("Hello"); // ceil(5/3.5) = 2
        final Message m2 = new AssistantMessage("1234567"); // ceil(7/3.5) = 2
        final int expected = estimator.estimate(m1) + estimator.estimate(m2);

        final int result = estimator.estimate(List.of(m1, m2));

        assertThat(result).isEqualTo(expected);
    }
}
