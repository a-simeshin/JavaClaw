package ai.javaclaw.agent.memory.adapter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Unit tests for {@link JdbcChatMemory}. Mocks all external collaborators.
 *
 * <p>{@code @SuppressWarnings("deprecation")} is applied class-wide because
 * {@link ai.javaclaw.agent.memory.ChatMemory#saveAll(String, java.util.List)} is
 * intentionally deprecated (see its Javadoc) but must still be exercised here to pin
 * the delete-then-append contract of the adapter.
 */
@SuppressWarnings("deprecation")
@ExtendWith(MockitoExtension.class)
class JdbcChatMemoryTest {

    @Mock
    private ChatMemoryEntryJdbcRepository repository;

    private JdbcChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        chatMemory = new JdbcChatMemory(repository);
    }

    // ── appendAll ────────────────────────────────────────────────────────────

    @Test
    void appendAll_null_isNoOp() {
        chatMemory.appendAll("conv-1", null);
        verifyNoInteractions(repository);
    }

    @Test
    void appendAll_emptyList_isNoOp() {
        chatMemory.appendAll("conv-1", List.of());
        verifyNoInteractions(repository);
    }

    @Test
    void appendAll_singleMessage_createsOneEntry() {
        final String conversationId = "conv-1";
        final UserMessage message = new UserMessage("hello");

        chatMemory.appendAll(conversationId, List.of(message));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<ChatMemoryEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        final List<ChatMemoryEntry> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        final ChatMemoryEntry entry = saved.getFirst();
        assertThat(entry.id()).isNull();
        assertThat(entry.conversationId()).isEqualTo(conversationId);
        assertThat(entry.content()).isEqualTo("hello");
        assertThat(entry.type()).isEqualTo("USER");
    }

    @Test
    void appendAll_multipleMessages_savedAsSingleBatch() {
        chatMemory.appendAll(
                "conv-1", List.of(new UserMessage("msg1"), new AssistantMessage("msg2"), new UserMessage("msg3")));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<ChatMemoryEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        verify(repository, never()).save(any());

        final List<ChatMemoryEntry> saved = captor.getValue();
        assertThat(saved).extracting(ChatMemoryEntry::content).containsExactly("msg1", "msg2", "msg3");
        assertThat(saved).extracting(ChatMemoryEntry::type).containsExactly("USER", "ASSISTANT", "USER");
        // Ordering guaranteed by surrogate id ASC; all entries in one batch share one Instant.
        assertThat(saved.get(0).createdAt()).isEqualTo(saved.get(1).createdAt());
        assertThat(saved.get(1).createdAt()).isEqualTo(saved.get(2).createdAt());
    }

    @Test
    void appendAll_setsCorrectFields() {
        chatMemory.appendAll("conv-1", List.of(new UserMessage("user-text"), new AssistantMessage("assistant-text")));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<ChatMemoryEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        final List<ChatMemoryEntry> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).content()).isEqualTo("user-text");
        assertThat(saved.get(0).type()).isEqualTo("USER");
        assertThat(saved.get(1).content()).isEqualTo("assistant-text");
        assertThat(saved.get(1).type()).isEqualTo("ASSISTANT");
    }

    // ── saveAll ──────────────────────────────────────────────────────────────

    @Test
    void saveAll_callsDeleteThenAppendAll() {
        final InOrder inOrder = inOrder(repository);

        chatMemory.saveAll("conv-1", List.of(new UserMessage("hi")));

        inOrder.verify(repository).deleteByConversationId("conv-1");
        inOrder.verify(repository).saveAll(anyList());
    }

    @Test
    void saveAll_emptyMessages_stillCallsDelete() {
        chatMemory.saveAll("conv-1", List.of());

        verify(repository).deleteByConversationId("conv-1");
        verify(repository, never()).save(any());
        verify(repository, never()).saveAll(anyList());
    }

    // ── findByConversationId ─────────────────────────────────────────────────

    @Test
    void findByConversationId_emptyRepo_returnsEmptyList() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of());

        final List<Message> result = chatMemory.findByConversationId("conv-1");

        assertThat(result).isEmpty();
    }

    @Test
    void findByConversationId_mapsUserMessage() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of(entry("USER", "hello")));

        final List<Message> result = chatMemory.findByConversationId("conv-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isInstanceOf(UserMessage.class);
        assertThat(result.getFirst().getText()).isEqualTo("hello");
    }

    @Test
    void findByConversationId_mapsAssistantMessage() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of(entry("ASSISTANT", "answer")));

        final List<Message> result = chatMemory.findByConversationId("conv-1");

        assertThat(result.getFirst()).isInstanceOf(AssistantMessage.class);
        assertThat(result.getFirst().getText()).isEqualTo("answer");
    }

    @Test
    void findByConversationId_mapsSystemMessage() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of(entry("SYSTEM", "be helpful")));

        final List<Message> result = chatMemory.findByConversationId("conv-1");

        assertThat(result.getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(result.getFirst().getText()).isEqualTo("be helpful");
    }

    @Test
    void findByConversationId_mapsTool_withNullContent() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of(entry("TOOL", null)));

        final List<Message> result = chatMemory.findByConversationId("conv-1");

        assertThat(result.getFirst()).isInstanceOf(ToolResponseMessage.class);
    }

    @Test
    void findByConversationId_preservesOrder() {
        final List<ChatMemoryEntry> entries =
                List.of(entry("USER", "first"), entry("ASSISTANT", "second"), entry("USER", "third"));
        when(repository.findByConversationId("conv-1")).thenReturn(entries);

        final List<Message> result = chatMemory.findByConversationId("conv-1");

        assertThat(result).extracting(Message::getText).containsExactly("first", "second", "third");
    }

    // ── deleteByConversationId ───────────────────────────────────────────────

    @Test
    void deleteByConversationId_delegatesToRepo() {
        chatMemory.deleteByConversationId("conv-1");

        verify(repository).deleteByConversationId("conv-1");
    }

    // ── findConversationIds ──────────────────────────────────────────────────

    @Test
    void findConversationIds_delegatesToRepository() {
        final List<String> expected = List.of("conv-1", "conv-2");
        when(repository.findDistinctConversationIds()).thenReturn(expected);

        final List<String> result = chatMemory.findConversationIds();

        assertThat(result).containsExactlyElementsOf(expected);
        verify(repository).findDistinctConversationIds();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ChatMemoryEntry entry(final String type, final String content) {
        return new ChatMemoryEntry(1L, "conv-1", content, type, Instant.now());
    }
}
