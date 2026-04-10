package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.audit.ChatAuditService;
import ai.javaclaw.tasks.ApprovalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
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
 * Unit-тесты для {@link ChatService}.
 *
 * <p>Проверяют: порядок сообщений (system → history → user), фильтрацию SystemMessage из истории,
 * persist user message до стриминга, persist assistant message после завершения стрима, корректную
 * работу синхронного call(). После рефакторинга ChatService делегирует сборку сообщений
 * {@link MessageAssembler}.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    /** Мок модели LLM. */
    @Mock
    private ChatModel chatModel;

    /** Мок хранилища истории. */
    @Mock
    private ChatMemory chatMemory;

    /** Мок ассемблера промптов. */
    @Mock
    private MessageAssembler messageAssembler;

    /** Мок резолвера tool callbacks. */
    @Mock
    private ToolCallbackResolver toolCallbackResolver;

    /** Мок сервиса аудита. */
    @Mock
    private ChatAuditService chatAuditService;

    /** Тестируемый сервис. */
    private ChatService chatService;

    /** Константа conversationId для тестов. */
    private static final String CONVERSATION_ID = "test-conversation-123";

    /** Константа userContent для тестов. */
    private static final String USER_CONTENT = "Hello, assistant!";

    /** Константа системного промпта. */
    private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

    /** Константа ответа ассистента. */
    private static final String ASSISTANT_REPLY = "Hello, user!";

    @BeforeEach
    void setUp() {
        when(toolCallbackResolver.resolve()).thenReturn(List.of());
        chatService = new ChatService(chatModel, chatMemory, messageAssembler, toolCallbackResolver, chatAuditService);
    }

    /**
     * Builds a default AssembledPrompt with empty history for the given user content.
     *
     * @param userContent the user message content
     * @return assembled prompt with system + empty history + user message
     */
    private AssembledPrompt buildAssembledPrompt(final String userContent) {
        return new AssembledPrompt(new SystemMessage(SYSTEM_PROMPT), List.of(), new UserMessage(userContent));
    }

    // -------------------------------------------------------------------------
    // stream() tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("stream(): SystemMessage идёт первым в Prompt")
    void stream_systemMessageIsFirst() {
        final ChatResponse response = buildChatResponse(ASSISTANT_REPLY);
        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                .thenReturn(buildAssembledPrompt(USER_CONTENT));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        StepVerifier.create(chatService.stream(CONVERSATION_ID, USER_CONTENT))
                .expectNextCount(1)
                .verifyComplete();

        verify(chatModel).stream(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();
        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo(SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("stream(): SystemMessage из истории не дублируется в Prompt")
    void stream_systemMessagesFromHistoryAreFiltered() {
        // MessageAssembler already filters SystemMessages from history — verify the result
        final UserMessage historyUser = new UserMessage("Previous user message");
        final AssistantMessage historyAssistant = new AssistantMessage("Previous assistant reply");
        final AssembledPrompt assembled = new AssembledPrompt(
                new SystemMessage(SYSTEM_PROMPT),
                List.of(historyUser, historyAssistant),
                new UserMessage(USER_CONTENT));

        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT)).thenReturn(assembled);
        final ChatResponse response = buildChatResponse(ASSISTANT_REPLY);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        StepVerifier.create(chatService.stream(CONVERSATION_ID, USER_CONTENT))
                .expectNextCount(1)
                .verifyComplete();

        verify(chatModel).stream(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();

        // Должен быть только один SystemMessage — из AssembledPrompt
        final long systemCount =
                messages.stream().filter(m -> m instanceof SystemMessage).count();
        assertThat(systemCount).isEqualTo(1);
        assertThat(messages.get(0).getText()).isEqualTo(SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("stream(): пользовательское сообщение персистируется до начала стриминга")
    void stream_userMessagePersistedBeforeStreaming() {
        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                .thenReturn(buildAssembledPrompt(USER_CONTENT));
        final ChatResponse response = buildChatResponse(ASSISTANT_REPLY);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        final ArgumentCaptor<List<Message>> addCaptor = ArgumentCaptor.forClass(List.class);

        StepVerifier.create(chatService.stream(CONVERSATION_ID, USER_CONTENT))
                .expectNextCount(1)
                .verifyComplete();

        // Первый вызов add — это UserMessage перед stream
        verify(chatMemory, times(2)).add(eq(CONVERSATION_ID), addCaptor.capture());
        final List<Message> firstAddedMessages = addCaptor.getAllValues().get(0);
        assertThat(firstAddedMessages).hasSize(1);
        assertThat(firstAddedMessages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(firstAddedMessages.get(0).getText()).isEqualTo(USER_CONTENT);
    }

    @Test
    @DisplayName("stream(): ассистентское сообщение персистируется после завершения стрима")
    void stream_assistantMessagePersistedAfterStreamComplete() {
        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                .thenReturn(buildAssembledPrompt(USER_CONTENT));
        final ChatResponse response = buildChatResponse(ASSISTANT_REPLY);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        final ArgumentCaptor<List<Message>> addCaptor = ArgumentCaptor.forClass(List.class);

        StepVerifier.create(chatService.stream(CONVERSATION_ID, USER_CONTENT))
                .expectNextCount(1)
                .verifyComplete();

        verify(chatMemory, times(2)).add(eq(CONVERSATION_ID), addCaptor.capture());
        final List<Message> secondAddedMessages = addCaptor.getAllValues().get(1);
        assertThat(secondAddedMessages).hasSize(1);
        assertThat(secondAddedMessages.get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(secondAddedMessages.get(0).getText()).isEqualTo(ASSISTANT_REPLY);
    }

    @Test
    @DisplayName("stream(): fallback на call() если ChatModel выбрасывает UnsupportedOperationException")
    void stream_fallbackToCallWhenStreamingUnsupported() {
        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                .thenReturn(buildAssembledPrompt(USER_CONTENT));
        when(chatModel.stream(any(Prompt.class)))
                .thenThrow(new UnsupportedOperationException("streaming not supported"));
        final ChatResponse fallbackResponse = buildChatResponse(ASSISTANT_REPLY);
        when(chatModel.call(any(Prompt.class))).thenReturn(fallbackResponse);

        StepVerifier.create(chatService.stream(CONVERSATION_ID, USER_CONTENT))
                .expectNextMatches(
                        r -> ASSISTANT_REPLY.equals(r.getResult().getOutput().getText()))
                .verifyComplete();

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("stream(): история содержит user и assistant сообщения в правильном порядке")
    void stream_historyMessagesIncludedInOrder() {
        final UserMessage historyUser = new UserMessage("First question");
        final AssistantMessage historyAssistant = new AssistantMessage("First answer");
        final AssembledPrompt assembled = new AssembledPrompt(
                new SystemMessage(SYSTEM_PROMPT),
                List.of(historyUser, historyAssistant),
                new UserMessage(USER_CONTENT));

        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT)).thenReturn(assembled);
        final ChatResponse response = buildChatResponse(ASSISTANT_REPLY);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        StepVerifier.create(chatService.stream(CONVERSATION_ID, USER_CONTENT))
                .expectNextCount(1)
                .verifyComplete();

        verify(chatModel).stream(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();

        // Порядок: [SystemMessage, UserMessage(history), AssistantMessage(history), UserMessage(current)]
        assertThat(messages).hasSizeGreaterThanOrEqualTo(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("First question");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo("First answer");
    }

    // -------------------------------------------------------------------------
    // call(String, String) tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("call(): возвращает текст ответа ассистента")
    void call_returnsAssistantText() {
        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                .thenReturn(buildAssembledPrompt(USER_CONTENT));
        when(chatModel.call(any(Prompt.class))).thenReturn(buildChatResponse(ASSISTANT_REPLY));

        final String result = chatService.call(CONVERSATION_ID, USER_CONTENT);

        assertThat(result).isEqualTo(ASSISTANT_REPLY);
    }

    @Test
    @DisplayName("call(): персистирует и user, и assistant сообщения")
    void call_persistsBothMessages() {
        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                .thenReturn(buildAssembledPrompt(USER_CONTENT));
        when(chatModel.call(any(Prompt.class))).thenReturn(buildChatResponse(ASSISTANT_REPLY));

        chatService.call(CONVERSATION_ID, USER_CONTENT);

        final ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatMemory, times(2)).add(eq(CONVERSATION_ID), captor.capture());

        final List<Message> firstAdded = captor.getAllValues().get(0);
        assertThat(firstAdded.get(0)).isInstanceOf(UserMessage.class);

        final List<Message> secondAdded = captor.getAllValues().get(1);
        assertThat(secondAdded.get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(secondAdded.get(0).getText()).isEqualTo(ASSISTANT_REPLY);
    }

    @Test
    @DisplayName("call(): SystemMessage первым в Prompt")
    void call_systemMessageIsFirst() {
        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                .thenReturn(buildAssembledPrompt(USER_CONTENT));
        when(chatModel.call(any(Prompt.class))).thenReturn(buildChatResponse(ASSISTANT_REPLY));

        chatService.call(CONVERSATION_ID, USER_CONTENT);

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo(SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("call(): пустой ответ LLM не вызывает ошибку")
    void call_emptyResponseHandledGracefully() {
        when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                .thenReturn(buildAssembledPrompt(USER_CONTENT));
        final ChatResponse emptyResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
        when(chatModel.call(any(Prompt.class))).thenReturn(emptyResponse);

        final String result = chatService.call(CONVERSATION_ID, USER_CONTENT);

        assertThat(result).isEmpty();
        // Пустой ответ не должен персистироваться
        verify(chatMemory, times(1)).add(anyString(), any(List.class));
    }

    // -------------------------------------------------------------------------
    // call(String, String, Class<T>) tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("call(resultType): добавляет format instructions к userContent")
    void callWithResultType_addsFormatInstructions() {
        // BeanOutputConverter appends format instructions to userContent; assembler is called with enriched content
        when(messageAssembler.assemble(eq(CONVERSATION_ID), any(), anyString())).thenAnswer(inv -> {
            final String content = inv.getArgument(2);
            return new AssembledPrompt(new SystemMessage(SYSTEM_PROMPT), List.of(), new UserMessage(content));
        });
        // Возвращаем валидный JSON для SimpleDto
        final String jsonResponse = "{\"value\":\"test\"}";
        when(chatModel.call(any(Prompt.class))).thenReturn(buildChatResponse(jsonResponse));

        chatService.call(CONVERSATION_ID, USER_CONTENT, SimpleDto.class);

        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        final List<Message> messages = promptCaptor.getValue().getInstructions();
        // Последнее сообщение — UserMessage с format instructions
        final Message lastMessage = messages.get(messages.size() - 1);
        assertThat(lastMessage).isInstanceOf(UserMessage.class);
        assertThat(lastMessage.getText()).contains(USER_CONTENT);
        assertThat(lastMessage.getText()).contains("JSON"); // BeanOutputConverter добавляет JSON schema
    }

    // -------------------------------------------------------------------------
    // Approval integration tests (T17 — pending approval routing)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Approval routing")
    class ApprovalRouting {

        @Mock
        private ApprovalService approvalService;

        private ChatService chatServiceWithApproval;

        @BeforeEach
        void setUpApproval() {
            Mockito.lenient().when(toolCallbackResolver.resolve()).thenReturn(List.of());
            chatServiceWithApproval = new ChatService(
                    chatModel, chatMemory, messageAssembler, toolCallbackResolver, chatAuditService, approvalService);
        }

        @Test
        @DisplayName("stream(): pending approval → ответ перехватывается, submitApproval вызван, LLM не вызывается")
        void stream_pendingApprovalInterceptsAndSubmits() {
            when(approvalService.hasPendingApproval(CONVERSATION_ID)).thenReturn(true);
            when(approvalService.submitApproval(CONVERSATION_ID, USER_CONTENT)).thenReturn(true);

            StepVerifier.create(chatServiceWithApproval.stream(CONVERSATION_ID, USER_CONTENT))
                    .expectNextMatches(r -> ChatService.APPROVAL_CONFIRMATION.equals(
                            r.getResult().getOutput().getText()))
                    .verifyComplete();

            // submitApproval called
            verify(approvalService).submitApproval(CONVERSATION_ID, USER_CONTENT);
            // LLM not called
            verify(chatModel, times(0)).stream(any(Prompt.class));
            verify(chatModel, times(0)).call(any(Prompt.class));
            // User + assistant messages persisted
            verify(chatMemory, times(2)).add(eq(CONVERSATION_ID), any(List.class));
        }

        @Test
        @DisplayName("call(): pending approval → возвращает подтверждение, LLM не вызывается")
        void call_pendingApprovalInterceptsAndSubmits() {
            when(approvalService.hasPendingApproval(CONVERSATION_ID)).thenReturn(true);
            when(approvalService.submitApproval(CONVERSATION_ID, USER_CONTENT)).thenReturn(true);

            final String result = chatServiceWithApproval.call(CONVERSATION_ID, USER_CONTENT);

            assertThat(result).isEqualTo(ChatService.APPROVAL_CONFIRMATION);
            verify(approvalService).submitApproval(CONVERSATION_ID, USER_CONTENT);
            verify(chatModel, times(0)).call(any(Prompt.class));
            verify(chatMemory, times(2)).add(eq(CONVERSATION_ID), any(List.class));
        }

        @Test
        @DisplayName("stream(): no pending approval → normal chat flow (T17)")
        void stream_noPendingApprovalProceedsNormally() {
            when(approvalService.hasPendingApproval(CONVERSATION_ID)).thenReturn(false);
            when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                    .thenReturn(buildAssembledPrompt(USER_CONTENT));
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(buildChatResponse(ASSISTANT_REPLY)));

            StepVerifier.create(chatServiceWithApproval.stream(CONVERSATION_ID, USER_CONTENT))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(approvalService, times(0)).submitApproval(anyString(), anyString());
            verify(chatModel).stream(any(Prompt.class));
        }

        @Test
        @DisplayName("call(): no pending approval → normal chat flow (T17)")
        void call_noPendingApprovalProceedsNormally() {
            when(approvalService.hasPendingApproval(CONVERSATION_ID)).thenReturn(false);
            when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                    .thenReturn(buildAssembledPrompt(USER_CONTENT));
            when(chatModel.call(any(Prompt.class))).thenReturn(buildChatResponse(ASSISTANT_REPLY));

            final String result = chatServiceWithApproval.call(CONVERSATION_ID, USER_CONTENT);

            assertThat(result).isEqualTo(ASSISTANT_REPLY);
            verify(approvalService, times(0)).submitApproval(anyString(), anyString());
            verify(chatModel).call(any(Prompt.class));
        }

        @Test
        @DisplayName("stream(): approvalService is null → normal chat flow")
        void stream_nullApprovalServiceProceedsNormally() {
            // chatService (from parent setUp) has null approvalService
            when(messageAssembler.assemble(CONVERSATION_ID, null, USER_CONTENT))
                    .thenReturn(buildAssembledPrompt(USER_CONTENT));
            when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(buildChatResponse(ASSISTANT_REPLY)));

            StepVerifier.create(chatService.stream(CONVERSATION_ID, USER_CONTENT))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(chatModel).stream(any(Prompt.class));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Создаёт ChatResponse с одним Generation, содержащим заданный текст.
     *
     * @param text текст ответа ассистента
     * @return ChatResponse с текстом
     */
    private ChatResponse buildChatResponse(final String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /**
     * Простой DTO для теста structured output.
     *
     * @param value строковое значение
     */
    public record SimpleDto(String value) {}
}
