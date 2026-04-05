package ai.javaclaw.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class DefaultAgent implements Agent {

    private final ChatClient chatClient;
    private final SystemPromptProvider systemPromptProvider;

    public DefaultAgent(final ChatClient chatClient, final SystemPromptProvider systemPromptProvider) {
        Assert.notNull(chatClient, "chatClient must not be null");
        Assert.notNull(systemPromptProvider, "systemPromptProvider must not be null");
        this.chatClient = chatClient;
        this.systemPromptProvider = systemPromptProvider;
    }

    @Override
    public String respondTo(final String conversationId, final String question) {
        return chatClient
                .prompt(question)
                .system(systemPromptProvider.load())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    @Override
    public <T> T prompt(final String conversationId, final String input, final Class<T> result) {
        return chatClient
                .prompt(input)
                .system(systemPromptProvider.load())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(result);
    }
}
