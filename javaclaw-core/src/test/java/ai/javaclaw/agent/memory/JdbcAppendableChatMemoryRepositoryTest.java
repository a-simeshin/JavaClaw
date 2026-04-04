package ai.javaclaw.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

@ExtendWith(MockitoExtension.class)
class JdbcAppendableChatMemoryRepositoryTest {

    @Mock
    JdbcChatMemoryRepository delegateMock;

    JdbcAppendableChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcAppendableChatMemoryRepository(delegateMock);
    }

    @Test
    void appendAllMergesExistingAndNewMessages() {
        String conversationId = "conv-1";
        Message existing = new UserMessage("Hello");
        Message newMsg = new AssistantMessage("Hi there!");

        when(delegateMock.findByConversationId(conversationId)).thenReturn(List.of(existing));

        repository.appendAll(conversationId, List.of(newMsg));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(delegateMock).saveAll(eq(conversationId), captor.capture());

        List<Message> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0)).isSameAs(existing);
        assertThat(saved.get(1)).isSameAs(newMsg);
    }

    @Test
    void appendAllOnEmptyConversationSavesOnlyNewMessages() {
        String conversationId = "conv-new";
        Message msg = new UserMessage("First message");

        when(delegateMock.findByConversationId(conversationId)).thenReturn(List.of());

        repository.appendAll(conversationId, List.of(msg));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(delegateMock).saveAll(eq(conversationId), captor.capture());

        assertThat(captor.getValue()).containsExactly(msg);
    }

    @Test
    void findByConversationIdDelegatesToJdbc() {
        String conversationId = "conv-1";
        Message msg = new UserMessage("test");
        when(delegateMock.findByConversationId(conversationId)).thenReturn(List.of(msg));

        List<Message> result = repository.findByConversationId(conversationId);

        assertThat(result).containsExactly(msg);
        verify(delegateMock).findByConversationId(conversationId);
    }

    @Test
    void deleteByConversationIdDelegatesToJdbc() {
        String conversationId = "conv-1";

        repository.deleteByConversationId(conversationId);

        verify(delegateMock).deleteByConversationId(conversationId);
    }

    @Test
    void saveAllDelegatesToJdbc() {
        String conversationId = "conv-1";
        List<Message> messages = List.of(new UserMessage("msg"));

        repository.saveAll(conversationId, messages);

        verify(delegateMock).saveAll(conversationId, messages);
    }

    @Test
    void findConversationIdsDelegatesToJdbc() {
        when(delegateMock.findConversationIds()).thenReturn(List.of("conv-1", "conv-2"));

        List<String> result = repository.findConversationIds();

        assertThat(result).containsExactly("conv-1", "conv-2");
    }
}
