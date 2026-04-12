package ai.javaclaw.e2e.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.e2e.support.LiveTestBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Live integration tests for chat memory isolation and persistence
 * across conversations and context restarts.
 */
class ChatMemoryLiveE2ETest extends LiveTestBase {

    @Autowired
    Agent agent;

    @Autowired
    ChatMemory chatMemoryRepository;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void messagesFromDifferentConversationsDoNotMix() {
        String conv1 = "live-test-isolation-1-" + System.nanoTime();
        String conv2 = "live-test-isolation-2-" + System.nanoTime();

        agent.respondTo(conv1, "My secret code is ALPHA.");
        agent.respondTo(conv2, "My secret code is BRAVO.");

        var messagesConv1 = chatMemoryRepository.findByConversationId(conv1);
        var messagesConv2 = chatMemoryRepository.findByConversationId(conv2);

        assertThat(messagesConv1).isNotEmpty();
        assertThat(messagesConv2).isNotEmpty();

        String conv1Text = messagesConv1.stream().map(m -> m.getText()).reduce("", String::concat);
        String conv2Text = messagesConv2.stream().map(m -> m.getText()).reduce("", String::concat);

        assertThat(conv1Text).contains("ALPHA");
        assertThat(conv1Text).doesNotContain("BRAVO");
        assertThat(conv2Text).contains("BRAVO");
        assertThat(conv2Text).doesNotContain("ALPHA");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void memoryPersistsAfterContextRestart() {
        String conversationId = "live-test-persist-" + System.nanoTime();
        agent.respondTo(conversationId, "Remember this unique phrase: ZEPHYR-42.");

        var messages = chatMemoryRepository.findByConversationId(conversationId);
        assertThat(messages).isNotEmpty();

        String allText = messages.stream().map(m -> m.getText()).reduce("", String::concat);
        assertThat(allText).contains("ZEPHYR-42");
    }
}
