package ai.javaclaw.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Unit tests for {@link SpringDataChatMemoryRepository}. Mocks all external collaborators. */
@ExtendWith(MockitoExtension.class)
class SpringDataChatMemoryRepositoryTest {

    @Mock
    private ChatMemoryEntryRepository repository;

    @Mock
    private NamedParameterJdbcTemplate namedJdbc;

    private SpringDataChatMemoryRepository chatMemoryRepository;

    @BeforeEach
    void setUp() {
        chatMemoryRepository = new SpringDataChatMemoryRepository(repository, namedJdbc);
    }

    // ── appendAll ────────────────────────────────────────────────────────────

    @Test
    void appendAll_null_isNoOp() {
        chatMemoryRepository.appendAll("conv-1", null);
        verifyNoInteractions(repository, namedJdbc);
    }

    @Test
    void appendAll_emptyList_isNoOp() {
        chatMemoryRepository.appendAll("conv-1", List.of());
        verifyNoInteractions(repository, namedJdbc);
    }

    @Test
    void appendAll_singleMessage_createsOneEntry() {
        final String conversationId = "conv-1";
        final UserMessage message = new UserMessage("hello");

        chatMemoryRepository.appendAll(conversationId, List.of(message));

        final ArgumentCaptor<ChatMemoryEntry> captor = ArgumentCaptor.forClass(ChatMemoryEntry.class);
        verify(repository).save(captor.capture());

        final ChatMemoryEntry entry = captor.getValue();
        assertThat(entry.id()).isNull();
        assertThat(entry.conversationId()).isEqualTo(conversationId);
        assertThat(entry.content()).isEqualTo("hello");
        assertThat(entry.type()).isEqualTo("USER");
    }

    @Test
    void appendAll_multipleMessages_timestampsAreMonotonic() {
        chatMemoryRepository.appendAll(
                "conv-1", List.of(new UserMessage("msg1"), new AssistantMessage("msg2"), new UserMessage("msg3")));

        final ArgumentCaptor<ChatMemoryEntry> captor = ArgumentCaptor.forClass(ChatMemoryEntry.class);
        verify(repository, times(3)).save(captor.capture());

        final List<ChatMemoryEntry> entries = captor.getAllValues();
        assertThat(entries).hasSize(3);

        final Instant t0 = entries.get(0).createdAt();
        final Instant t1 = entries.get(1).createdAt();
        final Instant t2 = entries.get(2).createdAt();
        assertThat(t1).isAfter(t0);
        assertThat(t2).isAfter(t1);
    }

    @Test
    void appendAll_setsCorrectFields() {
        chatMemoryRepository.appendAll(
                "conv-1", List.of(new UserMessage("user-text"), new AssistantMessage("assistant-text")));

        final ArgumentCaptor<ChatMemoryEntry> captor = ArgumentCaptor.forClass(ChatMemoryEntry.class);
        verify(repository, times(2)).save(captor.capture());

        final List<ChatMemoryEntry> entries = captor.getAllValues();
        assertThat(entries.get(0).content()).isEqualTo("user-text");
        assertThat(entries.get(0).type()).isEqualTo("USER");
        assertThat(entries.get(1).content()).isEqualTo("assistant-text");
        assertThat(entries.get(1).type()).isEqualTo("ASSISTANT");
    }

    // ── saveAll ──────────────────────────────────────────────────────────────

    @Test
    void saveAll_callsDeleteThenAppendAll() {
        final InOrder inOrder = inOrder(repository);

        chatMemoryRepository.saveAll("conv-1", List.of(new UserMessage("hi")));

        inOrder.verify(repository).deleteByConversationId("conv-1");
        inOrder.verify(repository).save(any(ChatMemoryEntry.class));
    }

    @Test
    void saveAll_emptyMessages_stillCallsDelete() {
        chatMemoryRepository.saveAll("conv-1", List.of());

        verify(repository).deleteByConversationId("conv-1");
        verify(repository, never()).save(any());
    }

    // ── findByConversationId ─────────────────────────────────────────────────

    @Test
    void findByConversationId_emptyRepo_returnsEmptyList() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of());

        final List<Message> result = chatMemoryRepository.findByConversationId("conv-1");

        assertThat(result).isEmpty();
    }

    @Test
    void findByConversationId_mapsUserMessage() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of(entry("USER", "hello")));

        final List<Message> result = chatMemoryRepository.findByConversationId("conv-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(0).getText()).isEqualTo("hello");
    }

    @Test
    void findByConversationId_mapsAssistantMessage() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of(entry("ASSISTANT", "answer")));

        final List<Message> result = chatMemoryRepository.findByConversationId("conv-1");

        assertThat(result.get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(result.get(0).getText()).isEqualTo("answer");
    }

    @Test
    void findByConversationId_mapsSystemMessage() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of(entry("SYSTEM", "be helpful")));

        final List<Message> result = chatMemoryRepository.findByConversationId("conv-1");

        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(0).getText()).isEqualTo("be helpful");
    }

    @Test
    void findByConversationId_mapsTool_withNullContent() {
        when(repository.findByConversationId("conv-1")).thenReturn(List.of(entry("TOOL", null)));

        final List<Message> result = chatMemoryRepository.findByConversationId("conv-1");

        assertThat(result.get(0)).isInstanceOf(ToolResponseMessage.class);
    }

    @Test
    void findByConversationId_preservesOrder() {
        final List<ChatMemoryEntry> entries =
                List.of(entry("USER", "first"), entry("ASSISTANT", "second"), entry("USER", "third"));
        when(repository.findByConversationId("conv-1")).thenReturn(entries);

        final List<Message> result = chatMemoryRepository.findByConversationId("conv-1");

        assertThat(result).extracting(Message::getText).containsExactly("first", "second", "third");
    }

    // ── deleteByConversationId ───────────────────────────────────────────────

    @Test
    void deleteByConversationId_delegatesToRepo() {
        chatMemoryRepository.deleteByConversationId("conv-1");

        verify(repository).deleteByConversationId("conv-1");
    }

    // ── findConversationIds ──────────────────────────────────────────────────

    @Test
    void findConversationIds_delegatesToNamedJdbc() {
        final List<String> expected = List.of("conv-1", "conv-2");
        when(namedJdbc.queryForList(
                        "SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY", Map.of(), String.class))
                .thenReturn(expected);

        final List<String> result = chatMemoryRepository.findConversationIds();

        assertThat(result).containsExactlyElementsOf(expected);
        verify(namedJdbc)
                .queryForList(
                        eq("SELECT DISTINCT conversation_id FROM SPRING_AI_CHAT_MEMORY"),
                        eq(Map.of()),
                        eq(String.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ChatMemoryEntry entry(final String type, final String content) {
        return new ChatMemoryEntry(1L, "conv-1", content, type, Instant.now());
    }
}
