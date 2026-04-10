package ai.javaclaw.agent.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Stateless component that windows a flat list of chat {@link Message}s by whole conversation
 * turns, never splitting a turn in half.
 *
 * <p>A <em>turn</em> consists of one {@link UserMessage} followed by all subsequent
 * {@link org.springframework.ai.chat.messages.AssistantMessage} and
 * {@link org.springframework.ai.chat.messages.ToolResponseMessage} objects until the next
 * {@link UserMessage}. Any messages that appear before the very first {@link UserMessage} (e.g.
 * injected tool-result preambles) are treated as a single implicit preamble turn.
 *
 * <p>Windowing works by removing the oldest turn until the total message count fits within the
 * configured budget. At least one turn is always retained so the list is never returned empty when
 * the input was non-empty.
 */
@Component
public class TurnBoundaryWindower {

    /** Logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(TurnBoundaryWindower.class);

    /**
     * Windows {@code messages} to at most {@code maxMessages} by dropping the oldest whole turns.
     *
     * <p>If the list already fits within the budget it is returned unchanged (same instance).
     * The returned list is always unmodifiable.
     *
     * @param messages    full message history, must not be {@code null}
     * @param maxMessages maximum number of messages to keep; must be &gt; 0
     * @return unmodifiable list of messages that fits within {@code maxMessages}
     * @throws IllegalArgumentException if {@code messages} is {@code null} or
     *                                  {@code maxMessages} is not positive
     */
    public List<Message> window(final List<Message> messages, final int maxMessages) {
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be greater than 0");
        }
        if (messages.size() <= maxMessages) {
            return Collections.unmodifiableList(new ArrayList<>(messages));
        }

        final List<List<Message>> turns = groupIntoTurns(messages);
        final List<List<Message>> retained = dropOldestTurns(turns, maxMessages);
        return flatten(retained);
    }

    /**
     * Windows {@code messages} to fit within {@code maxTokens} by dropping the oldest whole turns.
     *
     * <p>Turn grouping follows the same algorithm as {@link #window(List, int)}: turns are groups
     * starting at each {@link UserMessage}. The {@link TokenEstimator} is used to compute the
     * total token cost of the current candidate window; the oldest turn is removed until the cost
     * fits or only one turn remains.
     *
     * <p>If the list already fits within the budget it is returned unchanged (same instance).
     * The returned list is always unmodifiable.
     *
     * @param messages  full message history, must not be {@code null}
     * @param maxTokens maximum token budget; must be &gt; 0
     * @param estimator token estimator used to measure message cost, must not be {@code null}
     * @return unmodifiable list of messages whose total estimated token count fits within
     *         {@code maxTokens}
     * @throws IllegalArgumentException if {@code messages} or {@code estimator} is {@code null},
     *                                  or {@code maxTokens} is not positive
     */
    public List<Message> window(final List<Message> messages, final int maxTokens, final TokenEstimator estimator) {
        return windowWithResult(messages, maxTokens, estimator).retained();
    }

    /**
     * Windows {@code messages} by token budget and returns both retained and dropped messages.
     *
     * @param messages  full message history, must not be {@code null}
     * @param maxTokens maximum token budget; must be &gt; 0
     * @param estimator token estimator, must not be {@code null}
     * @return {@link WindowingResult} with retained and dropped message lists
     */
    public WindowingResult windowWithResult(
            final List<Message> messages, final int maxTokens, final TokenEstimator estimator) {
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be greater than 0");
        }
        if (estimator == null) {
            throw new IllegalArgumentException("estimator must not be null");
        }

        final List<List<Message>> turns = groupIntoTurns(messages);
        final List<List<Message>> dropped = new ArrayList<>();
        final List<List<Message>> retained = dropOldestTurnsByTokens(turns, maxTokens, estimator, dropped);
        return new WindowingResult(flatten(retained), flatten(dropped));
    }

    /**
     * Removes the oldest turns from {@code turns} until the total estimated token count is within
     * {@code maxTokens} or only a single turn remains.
     *
     * @param turns     mutable list of turns (oldest first)
     * @param maxTokens maximum token budget
     * @param estimator token estimator
     * @return remaining turns (at least one)
     */
    private List<List<Message>> dropOldestTurnsByTokens(
            final List<List<Message>> turns, final int maxTokens, final TokenEstimator estimator) {
        return dropOldestTurnsByTokens(turns, maxTokens, estimator, null);
    }

    private List<List<Message>> dropOldestTurnsByTokens(
            final List<List<Message>> turns,
            final int maxTokens,
            final TokenEstimator estimator,
            @Nullable final List<List<Message>> droppedCollector) {
        final List<List<Message>> retained = new ArrayList<>(turns);
        while (retained.size() > 1) {
            final List<Message> flat = new ArrayList<>();
            for (final List<Message> turn : retained) {
                flat.addAll(turn);
            }
            if (estimator.estimate(flat) <= maxTokens) {
                break;
            }
            final List<Message> dropped = retained.remove(0);
            if (droppedCollector != null) {
                droppedCollector.add(dropped);
            }
            log.debug(
                    "TurnBoundaryWindower: dropped oldest turn by token budget ({} messages), {} turns remain",
                    dropped.size(),
                    retained.size());
        }
        return retained;
    }

    /**
     * Groups a flat message list into turns.
     *
     * <p>Each turn begins with a {@link UserMessage}. Messages that precede the first
     * {@link UserMessage} are collected into a "preamble" turn at index 0.
     *
     * @param messages flat list of messages
     * @return list of turns; each turn is an unmodifiable list
     */
    private List<List<Message>> groupIntoTurns(final List<Message> messages) {
        final List<List<Message>> turns = new ArrayList<>();
        List<Message> currentTurn = new ArrayList<>();

        for (final Message message : messages) {
            if (message instanceof UserMessage && !currentTurn.isEmpty()) {
                turns.add(Collections.unmodifiableList(currentTurn));
                currentTurn = new ArrayList<>();
            }
            currentTurn.add(message);
        }
        if (!currentTurn.isEmpty()) {
            turns.add(Collections.unmodifiableList(currentTurn));
        }
        return turns;
    }

    /**
     * Removes the oldest turns from {@code turns} until the total message count is within
     * {@code maxMessages} or only a single turn remains.
     *
     * @param turns       mutable list of turns (oldest first)
     * @param maxMessages maximum number of messages to keep
     * @return remaining turns (at least one)
     */
    private List<List<Message>> dropOldestTurns(final List<List<Message>> turns, final int maxMessages) {
        final List<List<Message>> retained = new ArrayList<>(turns);
        while (totalSize(retained) > maxMessages && retained.size() > 1) {
            final List<Message> dropped = retained.remove(0);
            log.debug(
                    "TurnBoundaryWindower: dropped oldest turn ({} messages), {} turns remain",
                    dropped.size(),
                    retained.size());
        }
        return retained;
    }

    /**
     * Returns the total number of messages across all turns.
     *
     * @param turns list of turns
     * @return sum of message counts
     */
    private int totalSize(final List<List<Message>> turns) {
        int total = 0;
        for (final List<Message> turn : turns) {
            total += turn.size();
        }
        return total;
    }

    /**
     * Flattens a list of turns into a single unmodifiable flat list.
     *
     * @param turns list of turns to flatten
     * @return unmodifiable flat list of all messages
     */
    private List<Message> flatten(final List<List<Message>> turns) {
        final List<Message> result = new ArrayList<>();
        for (final List<Message> turn : turns) {
            result.addAll(turn);
        }
        return Collections.unmodifiableList(result);
    }
}
