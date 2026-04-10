package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.SystemPromptProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Unit tests for {@link MessageAssembler}.
 *
 * <p>Verifies: sectioned system prompt, filtering of {@link SystemMessage} from history,
 * token-budget windowing via {@link TurnBoundaryWindower}, correct message order in
 * {@link AssembledPrompt#toMessageList()}, and behaviour with empty providers.
 */
@ExtendWith(MockitoExtension.class)
class MessageAssemblerTest {

    /** Mock provider of the system prompt identity and context sections. */
    @Mock
    private SystemPromptProvider systemPromptProvider;

    /** Mock provider of the active skills section. */
    @Mock
    private ActiveSkillsProvider activeSkillsProvider;

    /**
     * Mock {@link ChatMemory} — injected but only used for write path; reads go via
     * {@link #chatMemoryRepository}.
     */
    @Mock
    private ChatMemory chatMemory;

    /** Mock raw repository used for reading the full, unwindowed history. */
    @Mock
    private ChatMemoryRepository chatMemoryRepository;

    /** The assembler under test. */
    private MessageAssembler messageAssembler;

    /** Conversation identifier reused across tests. */
    private static final String CONVERSATION_ID = "conv-test-42";

    /** User input reused across tests. */
    private static final String USER_CONTENT = "What is the answer?";

    @BeforeEach
    void setUp() {
        final TokenBudgetProperties budgetProperties = new TokenBudgetProperties();
        final TokenEstimator tokenEstimator = new TokenEstimator();
        messageAssembler = new MessageAssembler(
                systemPromptProvider,
                activeSkillsProvider,
                chatMemory,
                chatMemoryRepository,
                new MessageSanitizer(),
                new TurnBoundaryWindower(),
                budgetProperties,
                tokenEstimator);
    }

    @Test
    @DisplayName("assemble(): system message is first in toMessageList()")
    void assemble_systemMessageIsFirst() {
        when(systemPromptProvider.loadIdentity(null)).thenReturn("I am an agent.");
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("");
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of());

        final AssembledPrompt assembled = messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final List<Message> messages = assembled.toMessageList();

        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
    }

    @Test
    @DisplayName("assemble(): SystemMessage from history is filtered out")
    void assemble_systemMessageFilteredFromHistory() {
        when(systemPromptProvider.loadIdentity(null)).thenReturn("Identity");
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("");
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID))
                .thenReturn(
                        List.of(new SystemMessage("Old system"), new UserMessage("Hello"), new AssistantMessage("Hi")));

        final AssembledPrompt assembled = messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final List<Message> history = assembled.history();

        final long systemCount =
                history.stream().filter(m -> m instanceof SystemMessage).count();
        assertThat(systemCount).isZero();
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("assemble(): empty skills section is not included in system prompt")
    void assemble_emptySkillsSectionSkipped() {
        final String identity = "I am an agent.";
        when(systemPromptProvider.loadIdentity(null)).thenReturn(identity);
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("");
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of());

        final AssembledPrompt assembled = messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final String systemText = assembled.systemMessage().getText();

        assertThat(systemText).doesNotContain("\n\n\n");
    }

    @Test
    @DisplayName("assemble(): all 4 sections appear when all providers return content")
    void assemble_allFourSectionsPresent() {
        when(systemPromptProvider.loadIdentity(null)).thenReturn("IDENTITY");
        when(systemPromptProvider.loadContext()).thenReturn("CONTEXT");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("SKILLS");
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of());

        final AssembledPrompt assembled = messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final String systemText = assembled.systemMessage().getText();

        assertThat(systemText).contains("IDENTITY");
        assertThat(systemText).contains("SKILLS");
        assertThat(systemText).contains("CONTEXT");
        assertThat(systemText).contains("# Environment");
    }

    @Test
    @DisplayName("assemble(): empty history → only system + user in toMessageList()")
    void assemble_emptyHistory_onlySystemAndUser() {
        when(systemPromptProvider.loadIdentity(null)).thenReturn("Identity");
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("");
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of());

        final AssembledPrompt assembled = messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final List<Message> messages = assembled.toMessageList();

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo(USER_CONTENT);
    }

    @Test
    @DisplayName("toMessageList(): order is [system, history..., user]")
    void toMessageList_correctOrder() {
        when(systemPromptProvider.loadIdentity(null)).thenReturn("Identity");
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("");

        final UserMessage historyUser = new UserMessage("Earlier question");
        final AssistantMessage historyAssistant = new AssistantMessage("Earlier answer");
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID))
                .thenReturn(List.of(historyUser, historyAssistant));

        final AssembledPrompt assembled = messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final List<Message> messages = assembled.toMessageList();

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("Earlier question");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo("Earlier answer");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3).getText()).isEqualTo(USER_CONTENT);
    }

    @Test
    @DisplayName("toMessageList(): returns unmodifiable list")
    void toMessageList_returnsUnmodifiableList() {
        when(systemPromptProvider.loadIdentity(null)).thenReturn("Identity");
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("");
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of());

        final AssembledPrompt assembled = messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final List<Message> messages = assembled.toMessageList();

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> messages.add(new UserMessage("extra")));
    }

    @Test
    @DisplayName("assemble(): windowing is applied — token-budget trimming by turn preserves at least one turn")
    void assemble_windowing_appliedToHistory() {
        // Strategy: use a TokenEstimator spy so we can verify how many tokens each message costs,
        // then set maxContextTokens just above the system-prompt cost so only a few history
        // messages fit.  Because AgentEnvironment.info() always contributes to the system message
        // we cannot predict its exact size up front, so we measure it at test time.
        //
        // 1. Build the assembler with default (large) budget first just to measure system tokens.
        // 2. Build a tight assembler whose available budget = 10 tokens (small enough that 41
        //    short "question N / answer N" messages — each ~3 tokens — are trimmed).
        final TokenEstimator tokenEstimator = new TokenEstimator();

        when(systemPromptProvider.loadIdentity(null)).thenReturn("Identity");
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("");

        // Measure the real system-prompt token cost using default assembler (no history needed)
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of());
        final AssembledPrompt probe = messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final int systemTokens = tokenEstimator.estimate(probe.systemMessage());

        // Build tight budget: max = systemTokens + 10 → exactly 10 tokens available for history
        final int availableForHistory = 10;
        final TokenBudgetProperties tightBudget = new TokenBudgetProperties();
        tightBudget.setMaxContextTokens(systemTokens + availableForHistory);
        tightBudget.setReservedForResponse(0);
        tightBudget.setReservedForTools(0);

        final MessageAssembler tightAssembler = new MessageAssembler(
                systemPromptProvider,
                activeSkillsProvider,
                chatMemory,
                chatMemoryRepository,
                new MessageSanitizer(),
                new TurnBoundaryWindower(),
                tightBudget,
                tokenEstimator);

        final List<Message> bigHistory = buildHistory(41);
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID)).thenReturn(bigHistory);

        final AssembledPrompt assembled = tightAssembler.assemble(CONVERSATION_ID, USER_CONTENT);
        final List<Message> history = assembled.history();

        // 41 messages × ~3 tokens each ≈ 123 tokens — far exceeds budget of 10 → must be trimmed
        assertThat(history.size()).isLessThan(41);
        // At least one turn must always be preserved
        assertThat(history).isNotEmpty();
    }

    @Test
    @DisplayName("assemble(): reads history from chatMemoryRepository, not chatMemory")
    void assemble_readsFromRepository_notChatMemory() {
        when(systemPromptProvider.loadIdentity(null)).thenReturn("Identity");
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(activeSkillsProvider.loadActiveSkills()).thenReturn("");
        when(chatMemoryRepository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of());

        messageAssembler.assemble(CONVERSATION_ID, USER_CONTENT);

        verify(chatMemoryRepository).findByConversationId(CONVERSATION_ID);
        // chatMemory.get() must NOT be called for reading
        org.mockito.Mockito.verify(chatMemory, org.mockito.Mockito.never()).get(CONVERSATION_ID);
    }

    /**
     * Builds a flat message history with alternating {@link UserMessage} and
     * {@link AssistantMessage} pairs up to {@code count} total messages.
     *
     * @param count total number of messages to create
     * @return list of messages
     */
    private static List<Message> buildHistory(final int count) {
        final List<Message> messages = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                messages.add(new UserMessage("question " + i));
            } else {
                messages.add(new AssistantMessage("answer " + i));
            }
        }
        return messages;
    }
}
