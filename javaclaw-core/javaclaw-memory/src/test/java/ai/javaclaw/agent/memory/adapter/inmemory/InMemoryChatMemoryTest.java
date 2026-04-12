package ai.javaclaw.agent.memory.adapter.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Unit tests for {@link InMemoryChatMemory}.
 *
 * <p>Covers the full {@link ai.javaclaw.agent.memory.ChatMemory} contract as satisfied by
 * the in-memory adapter: no-op handling of {@code null}/empty writes, append-order
 * preservation, isolation between conversations, immutable defensive copies on reads,
 * delete-then-append semantics of {@code saveAll}, and idempotent deletes. No Spring
 * context; pure POJO.
 *
 * <p>{@code @SuppressWarnings("deprecation")} is applied class-wide because
 * {@link ai.javaclaw.agent.memory.ChatMemory#saveAll(String, java.util.List)} is
 * intentionally deprecated but must still be exercised to pin the replace-behaviour
 * of the adapter.
 */
@SuppressWarnings("deprecation")
class InMemoryChatMemoryTest {

    private static final String CONV_A = "conv-a";
    private static final String CONV_B = "conv-b";
    private static final String CONV_C = "conv-c";

    private InMemoryChatMemory memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryChatMemory();
    }

    // ── findConversationIds ──────────────────────────────────────────────────

    @Test
    void findConversationIds_emptyStore_returnsEmptyList() {
        assertThat(memory.findConversationIds()).isEmpty();
    }

    @Test
    void findConversationIds_afterAppend_containsId() {
        memory.appendAll(CONV_A, List.of(new UserMessage("hello")));

        assertThat(memory.findConversationIds()).containsExactly(CONV_A);
    }

    @Test
    void findConversationIds_multipleConversations_preservesFirstInsertOrder() {
        memory.appendAll(CONV_B, List.of(new UserMessage("b")));
        memory.appendAll(CONV_A, List.of(new UserMessage("a")));
        memory.appendAll(CONV_C, List.of(new UserMessage("c")));

        assertThat(memory.findConversationIds()).containsExactly(CONV_B, CONV_A, CONV_C);
    }

    @Test
    void findConversationIds_afterDelete_doesNotContainId() {
        memory.appendAll(CONV_A, List.of(new UserMessage("msg")));

        memory.deleteByConversationId(CONV_A);

        assertThat(memory.findConversationIds()).isEmpty();
    }

    @Test
    void findConversationIds_returnsImmutableDefensiveCopy() {
        memory.appendAll(CONV_A, List.of(new UserMessage("msg")));

        final List<String> result = memory.findConversationIds();

        assertThatThrownBy(() -> result.add("injected")).isInstanceOf(UnsupportedOperationException.class);
    }

    // ── findByConversationId ─────────────────────────────────────────────────

    @Test
    void findByConversationId_unknownId_returnsEmptyList() {
        assertThat(memory.findByConversationId("never-existed")).isEmpty();
    }

    @Test
    void findByConversationId_singleAppend_returnsMessagesInOrder() {
        memory.appendAll(
                CONV_A, List.of(new UserMessage("first"), new AssistantMessage("second"), new UserMessage("third")));

        final List<Message> result = memory.findByConversationId(CONV_A);

        assertThat(result).extracting(Message::getText).containsExactly("first", "second", "third");
    }

    @Test
    void findByConversationId_multipleAppends_accumulatesInOrder() {
        memory.appendAll(CONV_A, List.of(new UserMessage("1")));
        memory.appendAll(CONV_A, List.of(new AssistantMessage("2")));
        memory.appendAll(CONV_A, List.of(new UserMessage("3"), new AssistantMessage("4")));

        assertThat(memory.findByConversationId(CONV_A))
                .extracting(Message::getText)
                .containsExactly("1", "2", "3", "4");
    }

    @Test
    void findByConversationId_isolationBetweenConversations() {
        memory.appendAll(CONV_A, List.of(new UserMessage("for-a")));
        memory.appendAll(CONV_B, List.of(new UserMessage("for-b")));

        assertThat(memory.findByConversationId(CONV_A))
                .extracting(Message::getText)
                .containsExactly("for-a");
        assertThat(memory.findByConversationId(CONV_B))
                .extracting(Message::getText)
                .containsExactly("for-b");
    }

    @Test
    void findByConversationId_returnsImmutableDefensiveCopy() {
        memory.appendAll(CONV_A, List.of(new UserMessage("msg")));

        final List<Message> result = memory.findByConversationId(CONV_A);

        assertThatThrownBy(() -> result.add(new UserMessage("injected")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findByConversationId_snapshotDoesNotSeeSubsequentWrites() {
        memory.appendAll(CONV_A, List.of(new UserMessage("first")));
        final List<Message> snapshot = memory.findByConversationId(CONV_A);

        memory.appendAll(CONV_A, List.of(new UserMessage("second")));

        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.getFirst().getText()).isEqualTo("first");
    }

    @Test
    void findByConversationId_allFourMessageTypes_preserved() {
        memory.appendAll(
                CONV_A,
                List.of(
                        new UserMessage("u"),
                        new AssistantMessage("a"),
                        new SystemMessage("s"),
                        ToolResponseMessage.builder().responses(List.of()).build()));

        final List<Message> result = memory.findByConversationId(CONV_A);

        assertThat(result).hasSize(4);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(result.get(2)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(3)).isInstanceOf(ToolResponseMessage.class);
    }

    // ── appendAll ────────────────────────────────────────────────────────────

    @Test
    void appendAll_null_isNoOp_doesNotCreateConversationKey() {
        memory.appendAll(CONV_A, null);

        assertThat(memory.findConversationIds()).isEmpty();
        assertThat(memory.findByConversationId(CONV_A)).isEmpty();
    }

    @Test
    void appendAll_emptyList_isNoOp_doesNotCreateConversationKey() {
        memory.appendAll(CONV_A, List.of());

        assertThat(memory.findConversationIds()).isEmpty();
        assertThat(memory.findByConversationId(CONV_A)).isEmpty();
    }

    @Test
    void appendAll_firstCall_createsConversation() {
        memory.appendAll(CONV_A, List.of(new UserMessage("hello")));

        assertThat(memory.findConversationIds()).containsExactly(CONV_A);
        assertThat(memory.findByConversationId(CONV_A)).hasSize(1);
    }

    @Test
    void appendAll_doesNotRetainReferenceToInputList() {
        final List<Message> mutable = new ArrayList<>();
        mutable.add(new UserMessage("original"));
        memory.appendAll(CONV_A, mutable);

        mutable.add(new UserMessage("injected-after-save"));

        assertThat(memory.findByConversationId(CONV_A))
                .hasSize(1)
                .extracting(Message::getText)
                .containsExactly("original");
    }

    // ── saveAll ──────────────────────────────────────────────────────────────

    @Test
    void saveAll_newConversation_storesMessages() {
        memory.saveAll(CONV_A, List.of(new UserMessage("fresh1"), new UserMessage("fresh2")));

        assertThat(memory.findByConversationId(CONV_A))
                .extracting(Message::getText)
                .containsExactly("fresh1", "fresh2");
    }

    @Test
    void saveAll_existingConversation_replacesEntirely() {
        memory.appendAll(CONV_A, List.of(new UserMessage("old1"), new UserMessage("old2")));

        memory.saveAll(CONV_A, List.of(new AssistantMessage("new1"), new AssistantMessage("new2")));

        assertThat(memory.findByConversationId(CONV_A))
                .extracting(Message::getText)
                .containsExactly("new1", "new2");
    }

    @Test
    void saveAll_emptyList_clearsConversationAndRemovesFromIds() {
        memory.appendAll(CONV_A, List.of(new UserMessage("will be cleared")));

        memory.saveAll(CONV_A, List.of());

        assertThat(memory.findByConversationId(CONV_A)).isEmpty();
        assertThat(memory.findConversationIds()).doesNotContain(CONV_A);
    }

    @Test
    void saveAll_nullList_clearsConversationAndRemovesFromIds() {
        memory.appendAll(CONV_A, List.of(new UserMessage("will be cleared")));

        memory.saveAll(CONV_A, null);

        assertThat(memory.findByConversationId(CONV_A)).isEmpty();
        assertThat(memory.findConversationIds()).doesNotContain(CONV_A);
    }

    @Test
    void saveAll_otherConversationsUntouched() {
        memory.appendAll(CONV_A, List.of(new UserMessage("a")));
        memory.appendAll(CONV_B, List.of(new UserMessage("b")));

        memory.saveAll(CONV_A, List.of(new AssistantMessage("new-a")));

        assertThat(memory.findByConversationId(CONV_B))
                .extracting(Message::getText)
                .containsExactly("b");
    }

    // ── deleteByConversationId ───────────────────────────────────────────────

    @Test
    void deleteByConversationId_existingConversation_removes() {
        memory.appendAll(CONV_A, List.of(new UserMessage("msg")));

        memory.deleteByConversationId(CONV_A);

        assertThat(memory.findByConversationId(CONV_A)).isEmpty();
        assertThat(memory.findConversationIds()).doesNotContain(CONV_A);
    }

    @Test
    void deleteByConversationId_unknownConversation_isNoOp() {
        memory.deleteByConversationId("never-existed");

        assertThat(memory.findConversationIds()).isEmpty();
    }

    @Test
    void deleteByConversationId_onlyTargetConversationAffected() {
        memory.appendAll(CONV_A, List.of(new UserMessage("a")));
        memory.appendAll(CONV_B, List.of(new UserMessage("b")));

        memory.deleteByConversationId(CONV_A);

        assertThat(memory.findConversationIds()).containsExactly(CONV_B);
        assertThat(memory.findByConversationId(CONV_B))
                .extracting(Message::getText)
                .containsExactly("b");
    }

    @Test
    void deleteByConversationId_calledTwice_isIdempotent() {
        memory.appendAll(CONV_A, List.of(new UserMessage("msg")));

        memory.deleteByConversationId(CONV_A);
        memory.deleteByConversationId(CONV_A);

        assertThat(memory.findConversationIds()).isEmpty();
    }
}
