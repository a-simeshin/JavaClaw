package ai.javaclaw.api.chat.service;

import ai.javaclaw.agent.pipeline.ChatService;
import ai.javaclaw.api.chat.configuration.ChatRestConfiguration;
import ai.javaclaw.channels.ChannelContextService;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

/**
 * Emits Vercel AI SDK stream-protocol events for a single chat turn.
 *
 * <p>Hardened against the documented Spring MVC {@code ResponseBodyEmitter} pitfalls:
 * heartbeats for disconnect detection, a shared emitter registry for cleanup,
 * a semaphore to bound concurrency, and synchronized writes from the streaming
 * thread + the heartbeat scheduler.
 *
 * <p>Streaming is delegated to {@link ChatService#stream(String, String)} which returns
 * a {@code Flux<ChatResponse>}; this service maps each response to a text delta and
 * writes it to the Vercel v4 data-stream wire format.
 */
@Service
public class SseStreamingService {

    private static final Logger log = LoggerFactory.getLogger(SseStreamingService.class);

    /** Оркестратор pipeline запросов к LLM. */
    private final ChatService chatService;

    /** Сервис сохранения routing context — позволяет async задачам найти канал для уведомления. */
    private final ChannelContextService channelContextService;

    /** Настройки SSE (таймаут, интервал heartbeat, лимит concurrency). */
    private final ChatRestConfiguration.SseProperties sseProperties;

    /** Набор активных emitter-ов — используется для контроля жизненного цикла. */
    private final Set<ResponseBodyEmitter> emitters = ConcurrentHashMap.newKeySet();

    /** Cancel-сигналы по conversationId — позволяют прервать LLM стрим по запросу пользователя. */
    private final ConcurrentMap<String, Sinks.Empty<Void>> cancelSignals = new ConcurrentHashMap<>();

    /** Семафор, ограничивающий число одновременных SSE-стримов. */
    private final Semaphore concurrencyLimit;

    /** Планировщик heartbeat-сообщений для обнаружения разрыва соединения. */
    private final ScheduledExecutorService heartbeatScheduler;

    /** Исполнитель виртуальных потоков для запуска стримов. */
    private final Executor streamExecutor;

    /** JSON-маппер для сериализации событий в Vercel v4 data-stream формат. */
    private final ObjectMapper jsonMapper;

    /**
     * Создаёт SseStreamingService с полным набором зависимостей.
     *
     * @param chatService          оркестратор pipeline, не может быть null
     * @param channelContextService сервис routing context, не может быть null
     * @param sseProperties        настройки SSE, не может быть null
     * @param jsonMapper           JSON-маппер, не может быть null
     */
    public SseStreamingService(
            final ChatService chatService,
            final ChannelContextService channelContextService,
            final ChatRestConfiguration.SseProperties sseProperties,
            final ObjectMapper jsonMapper) {
        this.chatService = chatService;
        this.channelContextService = channelContextService;
        this.sseProperties = sseProperties;
        this.jsonMapper = jsonMapper;
        this.concurrencyLimit = new Semaphore(sseProperties.maxConcurrent());
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.streamExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates a new emitter, registers cleanup callbacks and returns it.
     * Returns {@code null} if the concurrency limit is exhausted.
     *
     * @return новый {@link ResponseBodyEmitter} или {@code null} если лимит исчерпан
     */
    public ResponseBodyEmitter createEmitter() {
        if (!concurrencyLimit.tryAcquire()) {
            return null;
        }
        final ResponseBodyEmitter emitter =
                new ResponseBodyEmitter(sseProperties.timeout().toMillis());
        emitters.add(emitter);

        final Runnable release = () -> {
            if (emitters.remove(emitter)) {
                concurrencyLimit.release();
            }
        };
        emitter.onCompletion(release);
        emitter.onTimeout(() -> {
            emitter.complete();
            release.run();
        });
        emitter.onError(ex -> release.run());
        return emitter;
    }

    /**
     * Streams the agent's response to {@code userContent} for the given conversation id.
     *
     * <p>Runs the ChatService stream on a virtual thread; writes to the emitter under a
     * synchronization lock shared with the heartbeat scheduler.
     *
     * @param emitter цель для записи SSE-событий
     * @param conversationId идентификатор разговора
     * @param userContent сообщение пользователя
     */
    public void stream(final ResponseBodyEmitter emitter, final String conversationId, final String userContent) {
        stream(emitter, conversationId, null, userContent);
    }

    /**
     * Streams the agent's response with per-user prompt support.
     *
     * @param emitter цель для записи SSE-событий
     * @param conversationId идентификатор разговора
     * @param userId идентификатор пользователя для per-user промптов, может быть null
     * @param userContent сообщение пользователя
     */
    public void stream(
            final ResponseBodyEmitter emitter,
            final String conversationId,
            final String userId,
            final String userContent) {
        stream(emitter, conversationId, userId, userContent, null);
    }

    /**
     * Streams the agent's response with per-user prompt support and role-based model override.
     *
     * @param emitter цель для записи SSE-событий
     * @param conversationId идентификатор разговора
     * @param userId идентификатор пользователя для per-user промптов, может быть null
     * @param userContent сообщение пользователя
     * @param modelOverride модель для использования вместо дефолтной, может быть null
     */
    public void stream(
            final ResponseBodyEmitter emitter,
            final String conversationId,
            final String userId,
            final String userContent,
            final String modelOverride) {
        final Object lock = new Object();
        final ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(emitter, lock),
                sseProperties.heartbeatInterval().toMillis(),
                sseProperties.heartbeatInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> heartbeat.cancel(false));
        emitter.onTimeout(() -> heartbeat.cancel(false));
        emitter.onError(ex -> heartbeat.cancel(false));

        streamExecutor.execute(() -> runStream(emitter, lock, conversationId, userId, userContent, modelOverride));
    }

    /**
     * Выполняет стриминг: публикует события о начале, дельты текста/reasoning и финальные события.
     *
     * <p>Поддерживает extended thinking (Claude) и reasoning (o1/o3): если ChatResponse содержит
     * метаданные {@code "thinking"}, дельта эмитится как reasoning event (wire code {@code g});
     * при появлении {@code "signature"} reasoning блок закрывается; обычный текст идёт как text delta.
     *
     * @param emitter цель для записи SSE-событий
     * @param lock объект синхронизации, общий с heartbeat-потоком
     * @param conversationId идентификатор разговора
     * @param userId идентификатор пользователя (nullable)
     * @param userContent сообщение пользователя
     */
    private void runStream(
            final ResponseBodyEmitter emitter,
            final Object lock,
            final String conversationId,
            final String userId,
            final String userContent,
            final String modelOverride) {
        final String messageId = "msg_" + UUID.randomUUID();
        final String textBlockId = "text_" + UUID.randomUUID();
        final String reasoningBlockId = "reasoning_" + UUID.randomUUID();
        final Sinks.Empty<Void> cancelSink = Sinks.empty();
        cancelSignals.put(conversationId, cancelSink);
        // Mutable state for reasoning block lifecycle — accessed only from the stream thread.
        final boolean[] reasoningStarted = {false};
        final boolean[] textStartSent = {false};
        try {
            channelContextService.saveContext(
                    conversationId, "Web Chat Channel", java.util.Map.of("conversationId", conversationId));

            sendEvent(
                    emitter,
                    lock,
                    VercelSseEvent.MessageStart.builder().messageId(messageId).build());

            chatService.stream(conversationId, userId, userContent, modelOverride)
                    .takeUntilOther(cancelSink.asMono())
                    .doOnNext(response -> {
                        if (response == null
                                || response.getResult() == null
                                || response.getResult().getOutput() == null) {
                            return;
                        }
                        final var output = response.getResult().getOutput();
                        final var metadata = output.getMetadata();
                        final String text = output.getText();

                        if (metadata != null && metadata.containsKey("thinking")) {
                            // Thinking delta — emit as reasoning event
                            if (text != null && !text.isEmpty()) {
                                if (!reasoningStarted[0]) {
                                    trySendEvent(
                                            emitter,
                                            lock,
                                            VercelSseEvent.ReasoningStart.builder()
                                                    .id(reasoningBlockId)
                                                    .build());
                                    reasoningStarted[0] = true;
                                }
                                trySendEvent(
                                        emitter,
                                        lock,
                                        VercelSseEvent.ReasoningDelta.builder()
                                                .id(reasoningBlockId)
                                                .delta(text)
                                                .build());
                            }
                        } else if (metadata != null && metadata.containsKey("signature")) {
                            // Signature marks end of thinking block
                            if (reasoningStarted[0]) {
                                final Object signature = metadata.get("signature");
                                if (signature instanceof String sig) {
                                    trySendReasoningSignature(emitter, lock, sig);
                                }
                                trySendEvent(
                                        emitter,
                                        lock,
                                        VercelSseEvent.ReasoningEnd.builder()
                                                .id(reasoningBlockId)
                                                .build());
                                reasoningStarted[0] = false;
                            }
                        } else {
                            // Regular text delta
                            if (text != null && !text.isEmpty()) {
                                if (!textStartSent[0]) {
                                    trySendEvent(
                                            emitter,
                                            lock,
                                            VercelSseEvent.TextStart.builder()
                                                    .id(textBlockId)
                                                    .build());
                                    textStartSent[0] = true;
                                }
                                trySendEvent(
                                        emitter,
                                        lock,
                                        VercelSseEvent.TextDelta.builder()
                                                .id(textBlockId)
                                                .delta(text)
                                                .build());
                            }
                        }
                    })
                    .blockLast();

            // Close any unclosed reasoning block (e.g. if signature was missing)
            if (reasoningStarted[0]) {
                sendEvent(
                        emitter,
                        lock,
                        VercelSseEvent.ReasoningEnd.builder()
                                .id(reasoningBlockId)
                                .build());
            }
            // Close text block if it was opened
            if (textStartSent[0]) {
                sendEvent(
                        emitter,
                        lock,
                        VercelSseEvent.TextEnd.builder().id(textBlockId).build());
            } else {
                // If no text was sent (pure reasoning response), still emit empty text block
                sendEvent(
                        emitter,
                        lock,
                        VercelSseEvent.TextStart.builder().id(textBlockId).build());
                sendEvent(
                        emitter,
                        lock,
                        VercelSseEvent.TextEnd.builder().id(textBlockId).build());
            }
            sendEvent(emitter, lock, VercelSseEvent.FinishStep.builder().build());
            sendEvent(emitter, lock, VercelSseEvent.Finish.builder().build());
            synchronized (lock) {
                emitter.complete();
            }
        } catch (IOException io) {
            // Client disconnected — framework will complete the emitter, do not touch it.
            log.debug("SSE client disconnected for conversation {}", conversationId, io);
        } catch (ClientDisconnectedException disconnect) {
            log.debug("SSE client disconnected mid-stream for conversation {}", conversationId, disconnect);
        } catch (RuntimeException ex) {
            log.warn("SSE streaming failed for conversation {}", conversationId, ex);
            trySendError(emitter, lock, ex.getMessage());
            synchronized (lock) {
                emitter.completeWithError(ex);
            }
        } finally {
            cancelSignals.remove(conversationId);
        }
    }

    /**
     * Writes a v4 Vercel AI data-stream line: {@code code:json\n}.
     *
     * @param emitter цель для записи
     * @param lock объект синхронизации
     * @param code однобуквенный код протокола
     * @param payload объект для сериализации в JSON
     * @throws IOException если запись в emitter завершилась ошибкой
     */
    void writeLine(final ResponseBodyEmitter emitter, final Object lock, final char code, final Object payload)
            throws IOException {
        final String line = code + ":" + jsonMapper.writeValueAsString(payload) + "\n";
        synchronized (lock) {
            emitter.send(line, org.springframework.http.MediaType.TEXT_PLAIN);
        }
    }

    /**
     * Converts a typed UI Message Stream event to the v4 data stream wire line.
     *
     * @param emitter цель для записи
     * @param lock объект синхронизации
     * @param payload событие для отправки
     * @throws IOException если запись завершилась ошибкой
     */
    private void sendEvent(final ResponseBodyEmitter emitter, final Object lock, final Object payload)
            throws IOException {
        if (!(payload instanceof VercelSseEvent event)) {
            writeLine(emitter, lock, '2', payload);
            return;
        }
        switch (event) {
            case VercelSseEvent.TextDelta d -> writeLine(emitter, lock, '0', d.delta());
            case VercelSseEvent.ReasoningDelta d -> writeLine(emitter, lock, 'g', d.delta());
            case VercelSseEvent.Error e -> writeLine(emitter, lock, '3', e.errorText());
            case VercelSseEvent.Finish ignored -> writeLine(emitter, lock, 'd', FinishLine.stop());
            case VercelSseEvent.FinishStep ignored -> writeLine(emitter, lock, 'e', StepLine.stop());
            case VercelSseEvent.ToolInputAvailable t ->
                writeLine(emitter, lock, '9', new ToolCallLine(t.toolCallId(), t.toolName(), t.input()));
            case VercelSseEvent.ToolOutputAvailable t ->
                writeLine(emitter, lock, 'a', new ToolResultLine(t.toolCallId(), t.output()));
            // Structural events (message-start, text-start/end, reasoning-start/end, tool-input-start/delta)
            // have no v4 data stream equivalent — they are folded into higher-level lines.
            default -> {
                /* no-op */
            }
        }
    }

    /** v4 {@code d:} finish line payload. */
    private record FinishLine(String finishReason, UsageLine usage) {
        static FinishLine stop() {
            return new FinishLine("stop", new UsageLine(0, 0));
        }
    }

    /** v4 {@code e:} step-finish line payload. */
    private record StepLine(String finishReason, UsageLine usage, boolean isContinued) {
        static StepLine stop() {
            return new StepLine("stop", new UsageLine(0, 0), false);
        }
    }

    /** Payload для usage-полей в finish/step finish строках. */
    private record UsageLine(int promptTokens, int completionTokens) {}

    /** v4 {@code 9:} tool-call line payload. */
    private record ToolCallLine(String toolCallId, String toolName, Object args) {}

    /** v4 {@code a:} tool-result line payload. */
    private record ToolResultLine(String toolCallId, Object result) {}

    /**
     * Отправляет reasoning signature в формате Vercel AI SDK v4 (wire code {@code j}).
     *
     * @param emitter цель для записи
     * @param lock объект синхронизации
     * @param signature cryptographic signature строка
     */
    private void trySendReasoningSignature(
            final ResponseBodyEmitter emitter, final Object lock, final String signature) {
        try {
            writeLine(emitter, lock, 'j', java.util.Map.of("signature", signature));
        } catch (IOException io) {
            throw new ClientDisconnectedException(io);
        }
    }

    /**
     * Отправляет событие, преобразуя {@link IOException} в {@link ClientDisconnectedException}.
     *
     * @param emitter цель для записи
     * @param lock объект синхронизации
     * @param payload событие для отправки
     */
    private void trySendEvent(final ResponseBodyEmitter emitter, final Object lock, final Object payload) {
        try {
            sendEvent(emitter, lock, payload);
        } catch (IOException io) {
            // Signal upstream reactor pipeline to bail out — handled by runStream's catch.
            throw new ClientDisconnectedException(io);
        }
    }

    /** Исключение-сигнал о разрыве соединения клиента в середине стрима. */
    private static final class ClientDisconnectedException extends RuntimeException {
        /**
         * Создаёт исключение с оригинальной IOException как причиной.
         *
         * @param cause оригинальная IOException
         */
        ClientDisconnectedException(final IOException cause) {
            super(cause);
        }
    }

    /**
     * Отправляет событие об ошибке, игнорируя IOException (клиент уже отключён).
     *
     * @param emitter цель для записи
     * @param lock объект синхронизации
     * @param message текст ошибки
     */
    private void trySendError(final ResponseBodyEmitter emitter, final Object lock, final String message) {
        try {
            sendEvent(
                    emitter,
                    lock,
                    VercelSseEvent.Error.builder()
                            .errorText(message == null ? "stream failed" : message)
                            .build());
        } catch (IOException ignored) {
            // swallow — the client is gone anyway
        }
    }

    /**
     * Отправляет heartbeat-сообщение (пустая текстовая дельта) для обнаружения разрыва.
     *
     * @param emitter цель для записи
     * @param lock объект синхронизации
     */
    private void sendHeartbeat(final ResponseBodyEmitter emitter, final Object lock) {
        try {
            // v4 data stream protocol has no comments — send empty text delta as keepalive.
            writeLine(emitter, lock, '0', "");
        } catch (IOException disconnect) {
            log.debug("Heartbeat detected client disconnect", disconnect);
        } catch (IllegalStateException completed) {
            // Emitter already completed — benign race with runStream finishing.
        }
    }

    /**
     * Cancels an active stream for the given conversation. Emits a complete signal
     * to the {@code takeUntilOther} operator, causing the Flux pipeline to terminate
     * and stop consuming LLM tokens.
     *
     * @param conversationId идентификатор разговора для отмены
     * @return true если стрим был найден и отменён, false если стрим не был активен
     */
    public boolean cancel(final String conversationId) {
        final Sinks.Empty<Void> sink = cancelSignals.remove(conversationId);
        if (sink != null) {
            sink.tryEmitEmpty();
            log.info("Cancelled active stream for conversation {}", conversationId);
            return true;
        }
        return false;
    }

    /**
     * Test / diagnostic accessor for the live emitter registry.
     *
     * @return число активных emitter-ов
     */
    public int activeEmitters() {
        return emitters.size();
    }

    /**
     * Admission-control query — exposed for tests.
     *
     * @return число доступных разрешений семафора
     */
    public int availablePermits() {
        return concurrencyLimit.availablePermits();
    }
}
