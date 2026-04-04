package ai.javaclaw.providers;

import java.util.SequencedCollection;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AgentProvider {

    private final Environment environment;
    private final SequencedCollection<ChatModel> chatModelProviders;

    public AgentProvider(Environment environment, SequencedCollection<ChatModel> chatModelProviders) {
        this.environment = environment;
        this.chatModelProviders =
                chatModelProviders.stream().filter(this::isConfigured).toList();
    }

    private boolean isConfigured(ChatModel chatModel) {
        return false;
    }

    public ChatModel getDefaultChatModel() {
        return chatModelProviders.getFirst();
    }
}
