package ai.javaclaw.agent.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Stateless pipeline component that sanitizes a conversation history list before it is
 * sent to the LLM.
 *
 * <p>Four rules are applied in sequence, each producing a new list fed into the next:
 * <ol>
 *   <li><b>Dedup</b> — removes adjacent messages with the same type and text.</li>
 *   <li><b>Orphan tool-response</b> — removes {@link ToolResponseMessage} instances not
 *       immediately preceded by an {@link AssistantMessage} that contains tool calls.</li>
 *   <li><b>Broken tool-call</b> — strips tool calls from an {@link AssistantMessage} that
 *       is not immediately followed by a {@link ToolResponseMessage}; removes the message
 *       entirely when it has neither text nor tool calls after stripping.</li>
 *   <li><b>Alternation</b> — when two consecutive {@link UserMessage} instances appear,
 *       keeps only the last one.</li>
 * </ol>
 */
@Component
public class MessageSanitizer {

    /** Logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(MessageSanitizer.class);

    /**
     * Sanitizes the supplied message list by applying all four rules in order.
     *
     * @param messages the raw conversation history; must not be {@code null}
     * @return an unmodifiable, sanitized copy of the message list (may be empty)
     */
    public List<Message> sanitize(final List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Message> result = dedup(messages);
        result = removeOrphanToolResponses(result);
        result = fixBrokenToolCalls(result);
        result = removeConsecutiveDuplicateUsers(result);
        return Collections.unmodifiableList(result);
    }

    /**
     * Rule 1: removes adjacent messages that share the same {@link MessageType} and text.
     *
     * @param messages input list
     * @return deduplicated list
     */
    private List<Message> dedup(final List<Message> messages) {
        final List<Message> result = new ArrayList<>(messages.size());
        Message previous = null;
        for (final Message current : messages) {
            if (previous != null
                    && current.getMessageType() == previous.getMessageType()
                    && textOf(current).equals(textOf(previous))) {
                log.debug(
                        "Dedup: dropping adjacent duplicate [{}] \"{}\"",
                        current.getMessageType(),
                        truncate(textOf(current)));
                continue;
            }
            result.add(current);
            previous = current;
        }
        return result;
    }

    /**
     * Rule 2: removes a {@link ToolResponseMessage} that is not immediately preceded by an
     * {@link AssistantMessage} containing at least one tool call.
     *
     * @param messages input list
     * @return list with orphaned tool-response messages removed
     */
    private List<Message> removeOrphanToolResponses(final List<Message> messages) {
        final List<Message> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            final Message current = messages.get(i);
            if (!(current instanceof ToolResponseMessage)) {
                result.add(current);
                continue;
            }
            final Message predecessor = i > 0 ? messages.get(i - 1) : null;
            if (isAssistantWithToolCalls(predecessor)) {
                result.add(current);
            } else {
                log.debug("Orphan tool-response removed at index {}", i);
            }
        }
        return result;
    }

    /**
     * Rule 3: repairs an {@link AssistantMessage} that has tool calls but is not followed
     * by a {@link ToolResponseMessage}.  The tool calls are stripped and the text-only
     * variant is kept; the message is removed entirely when no text content remains.
     *
     * @param messages input list
     * @return list with broken tool-call messages repaired or removed
     */
    private List<Message> fixBrokenToolCalls(final List<Message> messages) {
        final List<Message> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            final Message current = messages.get(i);
            if (!(current instanceof AssistantMessage)) {
                result.add(current);
                continue;
            }
            final AssistantMessage assistant = (AssistantMessage) current;
            if (!assistant.hasToolCalls()) {
                result.add(current);
                continue;
            }
            final Message successor = i + 1 < messages.size() ? messages.get(i + 1) : null;
            if (successor instanceof ToolResponseMessage) {
                result.add(current);
                continue;
            }
            // Broken: AssistantMessage has tool calls but successor is not a ToolResponse
            final String text = textOf(assistant);
            if (text.isBlank()) {
                log.debug("Broken tool-call AssistantMessage with no text removed at index {}", i);
            } else {
                log.debug("Broken tool-call AssistantMessage stripped to text-only at index {}", i);
                result.add(new AssistantMessage(text));
            }
        }
        return result;
    }

    /**
     * Rule 4: when two consecutive {@link UserMessage} instances appear, removes the first
     * and keeps only the last.
     *
     * @param messages input list
     * @return list without consecutive user-message pairs
     */
    private List<Message> removeConsecutiveDuplicateUsers(final List<Message> messages) {
        final List<Message> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            final Message current = messages.get(i);
            if (current.getMessageType() == MessageType.USER
                    && i + 1 < messages.size()
                    && messages.get(i + 1).getMessageType() == MessageType.USER) {
                log.debug("Alternation: dropping consecutive UserMessage \"{}\"", truncate(textOf(current)));
                continue;
            }
            result.add(current);
        }
        return result;
    }

    /**
     * Returns {@code true} when {@code message} is an {@link AssistantMessage} that
     * contains at least one tool call.
     *
     * @param message the message to test; may be {@code null}
     * @return {@code true} if the message is an assistant message with tool calls
     */
    private boolean isAssistantWithToolCalls(final Message message) {
        if (!(message instanceof AssistantMessage)) {
            return false;
        }
        return ((AssistantMessage) message).hasToolCalls();
    }

    /**
     * Returns the text content of a message, falling back to an empty string when
     * {@link Message#getText()} returns {@code null}.
     *
     * @param message the message to read
     * @return non-null text content
     */
    private String textOf(final Message message) {
        final String text = message.getText();
        return text == null ? "" : text;
    }

    /**
     * Truncates a string to 60 characters for log output.
     *
     * @param text the text to truncate
     * @return truncated string
     */
    private String truncate(final String text) {
        if (text.length() <= 60) {
            return text;
        }
        return text.substring(0, 60) + "…";
    }

    /**
     * Creates a plain {@link UserMessage} — helper used only in tests indirectly via the
     * message constructors; kept here as a documented factory note.
     *
     * @param content text content
     * @return new user message
     */
    static UserMessage user(final String content) {
        return new UserMessage(content);
    }

    /**
     * Creates a plain {@link AssistantMessage} without tool calls — helper for tests.
     *
     * @param content text content
     * @return new assistant message
     */
    static AssistantMessage assistant(final String content) {
        return new AssistantMessage(content);
    }

    /**
     * Creates an {@link AssistantMessage} that carries tool calls — helper for tests.
     *
     * @param content   text content (may be blank)
     * @param toolCalls list of tool calls to embed
     * @return new assistant message with tool calls
     */
    static AssistantMessage assistantWithToolCalls(
            final String content, final List<AssistantMessage.ToolCall> toolCalls) {
        return AssistantMessage.builder().content(content).toolCalls(toolCalls).build();
    }

    /**
     * Creates a {@link ToolResponseMessage} with a single response — helper for tests.
     *
     * @param id           tool call id
     * @param name         tool name
     * @param responseData response payload
     * @return new tool-response message
     */
    static ToolResponseMessage toolResponse(final String id, final String name, final String responseData) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, name, responseData)))
                .build();
    }
}
