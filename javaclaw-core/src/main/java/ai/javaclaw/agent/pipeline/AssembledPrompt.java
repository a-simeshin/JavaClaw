package ai.javaclaw.agent.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Assembled prompt for LLM — sectioned system message + clean history + user message.
 *
 * <p>Produced by {@link MessageAssembler} and consumed by {@link ChatService} to build the final
 * {@link org.springframework.ai.chat.prompt.Prompt}.
 *
 * @param systemMessage assembled system message containing all prompt sections
 * @param history conversation history, already filtered of any SystemMessage instances
 * @param userMessage current user message to send to the LLM
 */
public record AssembledPrompt(SystemMessage systemMessage, List<Message> history, UserMessage userMessage) {

    /**
     * Returns the full message list in order: [system, ...history, user].
     *
     * @return unmodifiable list of messages ready for the LLM
     */
    public List<Message> toMessageList() {
        final List<Message> messages = new ArrayList<>(1 + history.size() + 1);
        messages.add(systemMessage);
        messages.addAll(history);
        messages.add(userMessage);
        return Collections.unmodifiableList(messages);
    }
}
