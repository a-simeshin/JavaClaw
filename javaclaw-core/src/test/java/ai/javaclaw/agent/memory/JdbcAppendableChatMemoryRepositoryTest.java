package ai.javaclaw.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
class JdbcAppendableChatMemoryRepositoryTest {

    @Mock
    JdbcChatMemoryRepository delegateMock;

    @Mock
    JdbcTemplate jdbcTemplate;

    JdbcAppendableChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcAppendableChatMemoryRepository(delegateMock, jdbcTemplate);
    }

    @Test
    void appendAllInsertsOnlyNewMessagesPreservingTimestamps() throws SQLException {
        String conversationId = "conv-1";
        Message newMsg = new AssistantMessage("Hi there!");

        repository.appendAll(conversationId, List.of(newMsg));

        // No fetch of existing history — new rows are INSERTed directly so prior
        // timestamps remain untouched.
        verify(delegateMock, Mockito.never()).findByConversationId(anyString());
        verify(delegateMock, Mockito.never()).saveAll(anyString(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<Message>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate)
                .batchUpdate(
                        eq("INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type) VALUES (?, ?, ?)"),
                        eq(List.of(newMsg)),
                        anyInt(),
                        setterCaptor.capture());

        // Verify the setter maps (conversation_id, content, type) columns.
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, newMsg);
        verify(ps).setString(1, "conv-1");
        verify(ps).setString(2, "Hi there!");
        verify(ps).setString(3, "ASSISTANT");
    }

    @Test
    void appendAllOnEmptyListIsNoOp() {
        repository.appendAll("conv", List.of());
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(delegateMock);
    }

    @Test
    void appendAllOnNullListIsNoOp() {
        repository.appendAll("conv", null);
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(delegateMock);
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
        repository.deleteByConversationId("conv-1");
        verify(delegateMock).deleteByConversationId("conv-1");
    }

    @Test
    void saveAllDelegatesToJdbc() {
        List<Message> messages = List.of(new UserMessage("msg"));
        repository.saveAll("conv-1", messages);
        verify(delegateMock).saveAll("conv-1", messages);
    }

    @Test
    void findConversationIdsDelegatesToJdbc() {
        when(delegateMock.findConversationIds()).thenReturn(List.of("conv-1", "conv-2"));
        assertThat(repository.findConversationIds()).containsExactly("conv-1", "conv-2");
    }
}
