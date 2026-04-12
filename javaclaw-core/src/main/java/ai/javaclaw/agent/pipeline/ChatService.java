package ai.javaclaw.agent.pipeline;

import ai.javaclaw.agent.audit.ChatAuditService;
import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.tasks.ApprovalService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Оркестратор жизненного цикла запроса к LLM: сборка сообщений → streaming/call → persist.
 *
 * <p>Работает напрямую с {@link ChatModel} без ChatClient и advisor chain. Делегирует сборку
 * сообщений {@link MessageAssembler}: SystemMessage (секционный) → history (без системных
 * сообщений) → UserMessage.
 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Модель для общения с LLM. */
    private final ChatModel chatModel;

    /** Хранилище истории чатов. */
    private final ChatMemory chatMemory;

    /** Ассемблер промптов, формирует полный набор сообщений для LLM. */
    private final MessageAssembler messageAssembler;

    /** Резолвер tool callbacks. */
    private final ToolCallbackResolver toolCallbackResolver;

    /** Сервис аудита запросов/ответов LLM. */
    private final ChatAuditService chatAuditService;

    /** Сервис одобрений (human-in-the-loop). Nullable — если не задан, approval routing отключён. */
    @Nullable
    private final ApprovalService approvalService;

    /** Сообщение-подтверждение при перехвате ответа на approval. */
    static final String APPROVAL_CONFIRMATION = "Ваш ответ передан задаче.";

    /**
     * Создаёт ChatService с полным набором зависимостей.
     *
     * @param chatModel модель LLM, не может быть null
     * @param chatMemory хранилище истории, не может быть null
     * @param messageAssembler ассемблер промптов, не может быть null
     * @param toolCallbackResolver резолвер tool callbacks, не может быть null
     * @param chatAuditService сервис аудита, не может быть null
     * @param approvalService сервис одобрений, может быть null
     */
    public ChatService(
            final ChatModel chatModel,
            final ChatMemory chatMemory,
            final MessageAssembler messageAssembler,
            final ToolCallbackResolver toolCallbackResolver,
            final ChatAuditService chatAuditService,
            @Nullable final ApprovalService approvalService) {
        Assert.notNull(chatModel, "chatModel must not be null");
        Assert.notNull(chatMemory, "chatMemory must not be null");
        Assert.notNull(messageAssembler, "messageAssembler must not be null");
        Assert.notNull(toolCallbackResolver, "toolCallbackResolver must not be null");
        Assert.notNull(chatAuditService, "chatAuditService must not be null");
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.messageAssembler = messageAssembler;
        this.toolCallbackResolver = toolCallbackResolver;
        this.chatAuditService = chatAuditService;
        this.approvalService = approvalService;
    }

    /**
     * Backward-compatible constructor без ApprovalService.
     */
    public ChatService(
            final ChatModel chatModel,
            final ChatMemory chatMemory,
            final MessageAssembler messageAssembler,
            final ToolCallbackResolver toolCallbackResolver,
            final ChatAuditService chatAuditService) {
        this(chatModel, chatMemory, messageAssembler, toolCallbackResolver, chatAuditService, null);
    }

    /**
     * Стриминговый запрос к LLM (backward-compatible overload).
     *
     * @param conversationId идентификатор разговора, не может быть пустым
     * @param userContent содержимое сообщения пользователя, не может быть пустым
     * @return Flux с ответами от LLM
     */
    public Flux<ChatResponse> stream(final String conversationId, final String userContent) {
        return stream(conversationId, null, userContent, null);
    }

    /**
     * Стриминговый запрос к LLM с per-user prompt support (backward-compatible overload).
     *
     * @param conversationId идентификатор разговора, не может быть пустым
     * @param userId идентификатор пользователя для per-user промптов, может быть null
     * @param userContent содержимое сообщения пользователя, не может быть пустым
     * @return Flux с ответами от LLM
     */
    public Flux<ChatResponse> stream(
            final String conversationId, @Nullable final String userId, final String userContent) {
        return stream(conversationId, userId, userContent, null);
    }

    /**
     * Стриминговый запрос к LLM с per-user prompt support и role-based model override.
     *
     * <p>Метод отвечает только за построение prompt и возврат {@link Flux}; персист и audit
     * ассистентского ответа делает вызывающий код (SseStreamingService), который консьюмит Flux
     * через {@link Flux#toStream()} и управляет жизненным циклом линейно. Это устраняет
     * реактивное состояние из бизнес-кода и корректно обрабатывает cancel/upstream abort
     * (fixes #11, #12, #24, #26).
     *
     * <p>UserMessage персистится ОДИН РАЗ до построения prompt. Assistant persist и audit —
     * на стороне caller.
     *
     * <p>Если модель не поддерживает streaming (выбрасывает {@link UnsupportedOperationException}),
     * автоматически fallback на синхронный {@link ChatModel#call(Prompt)}.
     *
     * @param conversationId идентификатор разговора, не может быть пустым
     * @param userId идентификатор пользователя для per-user промптов, может быть null
     * @param userContent содержимое сообщения пользователя, не может быть пустым
     * @param modelOverride модель для использования вместо дефолтной, может быть null
     * @return Flux с ответами от LLM — без реактивных hook'ов для persist/audit
     */
    public Flux<ChatResponse> stream(
            final String conversationId,
            @Nullable final String userId,
            final String userContent,
            @Nullable final String modelOverride) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        Assert.hasText(userContent, "userContent must not be blank");

        // Check for pending approval before normal chat flow (S6 human-in-the-loop)
        if (approvalService != null && approvalService.hasPendingApproval(conversationId)) {
            return handleApprovalResponse(conversationId, userContent);
        }

        // Persist user message ONCE — fix #24 (no duplicate USER messages).
        chatMemory.appendAll(conversationId, List.of(new UserMessage(userContent)));

        final Prompt prompt = buildPrompt(conversationId, userId, userContent, modelOverride);
        try {
            return chatModel.stream(prompt);
        } catch (final UnsupportedOperationException e) {
            log.debug(
                    "ChatModel does not support streaming, falling back to call() for conversation {}", conversationId);
            return Mono.fromCallable(() -> chatModel.call(prompt)).flux();
        }
    }

    /**
     * Синхронный запрос к LLM. Персистит оба сообщения (до и после вызова).
     *
     * @param conversationId идентификатор разговора, не может быть пустым
     * @param userContent содержимое сообщения пользователя, не может быть пустым
     * @return текст ответа ассистента
     */
    public String call(final String conversationId, final String userContent) {
        return call(conversationId, null, userContent);
    }

    /**
     * Синхронный запрос к LLM с per-user prompt support.
     *
     * @param conversationId идентификатор разговора, не может быть пустым
     * @param userId идентификатор пользователя для per-user промптов, может быть null
     * @param userContent содержимое сообщения пользователя, не может быть пустым
     * @return текст ответа ассистента
     */
    public String call(final String conversationId, @Nullable final String userId, final String userContent) {
        return call(conversationId, userId, userContent, null);
    }

    /**
     * Синхронный запрос к LLM с per-user prompt support и role-based model override.
     *
     * @param conversationId идентификатор разговора, не может быть пустым
     * @param userId идентификатор пользователя для per-user промптов, может быть null
     * @param userContent содержимое сообщения пользователя, не может быть пустым
     * @param modelOverride модель для использования вместо дефолтной, может быть null
     * @return текст ответа ассистента
     */
    public String call(
            final String conversationId,
            @Nullable final String userId,
            final String userContent,
            @Nullable final String modelOverride) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        Assert.hasText(userContent, "userContent must not be blank");

        // Check for pending approval before normal chat flow (S6 human-in-the-loop)
        if (approvalService != null && approvalService.hasPendingApproval(conversationId)) {
            chatMemory.appendAll(conversationId, List.of(new UserMessage(userContent)));
            approvalService.submitApproval(conversationId, userContent);
            persistAssistantMessage(conversationId, APPROVAL_CONFIRMATION);
            return APPROVAL_CONFIRMATION;
        }

        chatMemory.appendAll(conversationId, List.of(new UserMessage(userContent)));

        final Prompt prompt = buildPrompt(conversationId, userId, userContent, modelOverride);
        final long startTime = System.currentTimeMillis();
        try {
            final ChatResponse response = chatModel.call(prompt);
            final String assistantText = extractText(response);
            persistAssistantMessage(conversationId, assistantText);
            chatAuditService.logSuccess(
                    conversationId, "call", prompt, assistantText, System.currentTimeMillis() - startTime);
            return assistantText;
        } catch (final Exception e) {
            chatAuditService.logError(conversationId, "call", prompt, e, System.currentTimeMillis() - startTime);
            throw e;
        }
    }

    /**
     * Синхронный запрос к LLM со структурированным выводом. Использует {@link BeanOutputConverter}
     * для парсинга ответа в тип {@code T}.
     *
     * @param <T> тип результата
     * @param conversationId идентификатор разговора, не может быть пустым
     * @param userContent содержимое сообщения пользователя, не может быть пустым
     * @param resultType класс ожидаемого результата, не может быть null
     * @return распаршенный объект типа T
     */
    public <T> T call(final String conversationId, final String userContent, final Class<T> resultType) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        Assert.hasText(userContent, "userContent must not be blank");
        Assert.notNull(resultType, "resultType must not be null");

        final BeanOutputConverter<T> converter = new BeanOutputConverter<>(resultType);
        final String enrichedContent = userContent + "\n\n" + converter.getFormat();

        chatMemory.appendAll(conversationId, List.of(new UserMessage(enrichedContent)));

        final Prompt prompt = buildPrompt(conversationId, null, enrichedContent);
        final long startTime = System.currentTimeMillis();
        try {
            final ChatResponse response = chatModel.call(prompt);
            final String assistantText = extractText(response);
            persistAssistantMessage(conversationId, assistantText);
            chatAuditService.logSuccess(
                    conversationId, "call<T>", prompt, assistantText, System.currentTimeMillis() - startTime);
            return converter.convert(assistantText);
        } catch (final Exception e) {
            chatAuditService.logError(conversationId, "call<T>", prompt, e, System.currentTimeMillis() - startTime);
            throw e;
        }
    }

    /**
     * Обрабатывает ответ пользователя на pending approval в streaming режиме. Персистит оба
     * сообщения (user + assistant) и возвращает подтверждение как streaming Flux.
     *
     * @param conversationId идентификатор разговора
     * @param userContent текст ответа пользователя
     * @return Flux с одним ChatResponse-подтверждением
     */
    private Flux<ChatResponse> handleApprovalResponse(final String conversationId, final String userContent) {
        chatMemory.appendAll(conversationId, List.of(new UserMessage(userContent)));
        approvalService.submitApproval(conversationId, userContent);
        persistAssistantMessage(conversationId, APPROVAL_CONFIRMATION);
        final ChatResponse confirmationResponse =
                new ChatResponse(List.of(new Generation(new AssistantMessage(APPROVAL_CONFIRMATION))));
        return Flux.just(confirmationResponse);
    }

    /**
     * Строит {@link Prompt} с помощью {@link MessageAssembler}: системный промпт (секционный),
     * история (без SystemMessage) и текущее пользовательское сообщение.
     *
     * <p>UserMessage уже персистирован в memory до вызова этого метода. Если history уже содержит
     * это сообщение последним (например, после windowing), дублирование не происходит — потому что
     * {@link MessageAssembler} добавляет UserMessage напрямую из параметра, а не из history.
     *
     * @param conversationId идентификатор разговора
     * @param userContent текущее сообщение пользователя
     * @return готовый Prompt для отправки в LLM
     */
    private Prompt buildPrompt(
            final String conversationId,
            @Nullable final String userId,
            final String userContent,
            @Nullable final String modelOverride) {
        final AssembledPrompt assembled = messageAssembler.assemble(conversationId, userId, userContent);

        final var optionsBuilder = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbackResolver.resolve())
                .internalToolExecutionEnabled(Boolean.TRUE);

        if (modelOverride != null && !modelOverride.isBlank()) {
            optionsBuilder.model(modelOverride);
        }

        return new Prompt(assembled.toMessageList(), optionsBuilder.build());
    }

    /** Backward-compatible buildPrompt without model override. */
    private Prompt buildPrompt(final String conversationId, @Nullable final String userId, final String userContent) {
        return buildPrompt(conversationId, userId, userContent, null);
    }

    /**
     * Извлекает текст из ChatResponse. Возвращает пустую строку если ответ или текст отсутствуют.
     *
     * @param response ответ от LLM
     * @return текст ответа или пустая строка
     */
    private String extractText(final ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return "";
        }
        final AssistantMessage output = response.getResult().getOutput();
        if (output == null || output.getText() == null) {
            return "";
        }
        return output.getText();
    }

    /**
     * Персистит сообщение ассистента в историю разговора. Пустые сообщения игнорируются.
     *
     * @param conversationId идентификатор разговора
     * @param content текст ответа ассистента
     */
    private void persistAssistantMessage(final String conversationId, final String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        chatMemory.appendAll(conversationId, List.of(new AssistantMessage(content)));
    }
}
