package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.SystemPromptProvider;
import ai.javaclaw.skills.SkillRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Integration tests for {@link ChatService} wiring the real pipeline components end-to-end.
 *
 * <p>Real objects used: {@link TokenEstimator}, {@link TurnBoundaryWindower},
 * {@link MessageSanitizer}, {@link TokenBudgetProperties}, {@link ActiveSkillsProvider},
 * {@link MessageAssembler}.
 *
 * <p>Mocked boundaries: {@link ChatModel}, {@link ChatMemory}, {@link ChatMemoryRepository},
 * {@link SystemPromptProvider}, {@link SkillRepository}, {@link ToolCallbackResolver}.
 *
 * <p>No Spring context is started — pure constructor wiring.
 */
@DisplayName("ChatService Integration: full pipeline flow")
class ChatServiceIntegrationTest {

    // --- Real pipeline components (no Spring context) ---

    /** Real token estimator — pure function, no deps. */
    private TokenEstimator tokenEstimator;

    /** Real windower — pure function, no deps. */
    private TurnBoundaryWindower windower;

    /** Real sanitizer — pure function, no deps. */
    private MessageSanitizer sanitizer;

    /** Real budget properties — default values, no Spring binding needed. */
    private TokenBudgetProperties budgetProperties;

    // --- Mocked boundaries ---

    /** Mock chat model — actual LLM calls not made. */
    private ChatModel chatModel;

    /** Mock chat memory — in-memory persistence not needed. */
    private ChatMemory chatMemory;

    /** Mock raw repository — used by MessageAssembler to read full history. */
    private ChatMemoryRepository chatMemoryRepository;

    /** Mock system prompt provider — no file system or DB access. */
    private SystemPromptProvider systemPromptProvider;

    /** Mock skill repository — no DB access. */
    private SkillRepository skillRepository;

    /** Mock tool callback resolver. */
    private ToolCallbackResolver toolCallbackResolver;

    /** The service under test, wired with real pipeline and mocked boundaries. */
    private ChatService chatService;

    /** Constant conversation ID shared across tests. */
    private static final String CONV_ID = "integration-conv-1";

    /** Constant user input shared across tests. */
    private static final String USER_INPUT = "What is the answer?";

    /** Constant assistant reply shared across tests. */
    private static final String ASSISTANT_REPLY = "The answer is 42.";

    /** Constant identity prompt returned by mocked system prompt provider. */
    private static final String IDENTITY_PROMPT = "You are a helpful assistant.";

    /**
     * Wires the full pipeline using real components and mocked I/O boundaries.
     *
     * <p>System prompt provider returns a fixed identity, context is empty, skills are empty.
     * This allows tests to focus on pipeline behaviour rather than prompt content.
     */
    @BeforeEach
    void setUp() {
        tokenEstimator = new TokenEstimator();
        windower = new TurnBoundaryWindower();
        sanitizer = new MessageSanitizer();
        budgetProperties = new TokenBudgetProperties();

        chatModel = mock(ChatModel.class);
        chatMemory = mock(ChatMemory.class);
        chatMemoryRepository = mock(ChatMemoryRepository.class);
        systemPromptProvider = mock(SystemPromptProvider.class);
        skillRepository = mock(SkillRepository.class);
        toolCallbackResolver = mock(ToolCallbackResolver.class);

        when(systemPromptProvider.loadIdentity()).thenReturn(IDENTITY_PROMPT);
        when(systemPromptProvider.loadContext()).thenReturn("");
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue()).thenReturn(List.of());
        when(toolCallbackResolver.resolve()).thenReturn(List.of());
        // Default: empty history
        when(chatMemoryRepository.findByConversationId(any())).thenReturn(List.of());

        final ActiveSkillsProvider skillsProvider = new ActiveSkillsProvider(skillRepository);
        final MessageAssembler assembler = new MessageAssembler(
                systemPromptProvider,
                skillsProvider,
                chatMemory,
                chatMemoryRepository,
                sanitizer,
                windower,
                budgetProperties,
                tokenEstimator);

        chatService = new ChatService(chatModel, chatMemory, assembler, toolCallbackResolver);
    }

    // -------------------------------------------------------------------------
    // stream() integration tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that with empty history the prompt sent to the model has the correct
     * structure: [SystemMessage, UserMessage] (no history messages in between).
     */
    @Test
    @DisplayName("stream_withEmptyHistory_sendsCorrectPromptToModel()")
    void stream_withEmptyHistory_sendsCorrectPromptToModel() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(buildChatResponse(ASSISTANT_REPLY)));

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        StepVerifier.create(chatService.stream(CONV_ID, USER_INPUT))
                .expectNextCount(1)
                .verifyComplete();

        org.mockito.Mockito.verify(chatModel).stream(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo(USER_INPUT);
    }

    /**
     * Verifies that history messages appear between the system message and the current
     * user message when history is non-empty.
     */
    @Test
    @DisplayName("stream_withHistory_includesHistoryAfterSystem()")
    void stream_withHistory_includesHistoryAfterSystem() {
        final UserMessage historyUser = new UserMessage("First question");
        final AssistantMessage historyAssistant = new AssistantMessage("First answer");
        when(chatMemoryRepository.findByConversationId(eq(CONV_ID))).thenReturn(List.of(historyUser, historyAssistant));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(buildChatResponse(ASSISTANT_REPLY)));

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        StepVerifier.create(chatService.stream(CONV_ID, USER_INPUT))
                .expectNextCount(1)
                .verifyComplete();

        org.mockito.Mockito.verify(chatModel).stream(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();

        // [System, User(history), Assistant(history), User(current)]
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("First question");
        assertThat(messages.get(2).getText()).isEqualTo("First answer");
        assertThat(messages.get(3).getText()).isEqualTo(USER_INPUT);
    }

    /**
     * Verifies that a dirty history containing an orphan {@link org.springframework.ai.chat.messages.ToolResponseMessage}
     * is sanitized by {@link MessageSanitizer} before the prompt is sent to the model.
     */
    @Test
    @DisplayName("stream_withDirtyHistory_sanitizesBeforeCall()")
    void stream_withDirtyHistory_sanitizesBeforeCall() {
        // Orphan ToolResponseMessage — has no preceding AssistantMessage with tool calls
        final UserMessage historyUser = new UserMessage("Do something");
        final org.springframework.ai.chat.messages.ToolResponseMessage orphanToolResponse =
                org.springframework.ai.chat.messages.ToolResponseMessage.builder()
                        .responses(List.of(new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                "id-orphan", "myTool", "some result")))
                        .build();

        when(chatMemoryRepository.findByConversationId(eq(CONV_ID)))
                .thenReturn(List.of(historyUser, orphanToolResponse));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(buildChatResponse(ASSISTANT_REPLY)));

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        StepVerifier.create(chatService.stream(CONV_ID, USER_INPUT))
                .expectNextCount(1)
                .verifyComplete();

        org.mockito.Mockito.verify(chatModel).stream(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();

        // Orphan ToolResponseMessage must be stripped: [System, User(history), User(current)]
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("Do something");
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo(USER_INPUT);
    }

    // -------------------------------------------------------------------------
    // call() integration tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that the synchronous {@code call()} returns the text from the model response.
     */
    @Test
    @DisplayName("call_returnsModelResponse()")
    void call_returnsModelResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(buildChatResponse(ASSISTANT_REPLY));

        final String result = chatService.call(CONV_ID, USER_INPUT);

        assertThat(result).isEqualTo(ASSISTANT_REPLY);
    }

    /**
     * Verifies that the system prompt text produced by the real {@link MessageAssembler}
     * includes the identity text provided by the mocked {@link SystemPromptProvider}.
     */
    @Test
    @DisplayName("call_systemPromptContainsIdentity()")
    void call_systemPromptContainsIdentity() {
        when(chatModel.call(any(Prompt.class))).thenReturn(buildChatResponse(ASSISTANT_REPLY));

        chatService.call(CONV_ID, USER_INPUT);

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();

        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).contains(IDENTITY_PROMPT);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link ChatResponse} containing a single {@link Generation} with the given text.
     *
     * @param text the assistant reply text
     * @return a minimal ChatResponse for use in mock stubs
     */
    private ChatResponse buildChatResponse(final String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
