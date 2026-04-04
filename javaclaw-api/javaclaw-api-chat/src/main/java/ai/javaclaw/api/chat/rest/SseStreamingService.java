package ai.javaclaw.api.chat.rest;

import ai.javaclaw.channels.ChannelMessageReceivedEvent;
import ai.javaclaw.channels.ChannelRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Emits Vercel AI SDK stream-protocol events for a single chat turn.
 *
 * <p>Hardened against the documented Spring MVC {@code ResponseBodyEmitter} pitfalls:
 * heartbeats for disconnect detection, a shared emitter registry for cleanup,
 * a semaphore to bound concurrency, and synchronized writes from the streaming
 * thread + the heartbeat scheduler.
 */
@Service
public class SseStreamingService {

    private static final Logger log = LoggerFactory.getLogger(SseStreamingService.class);

    private final ChatClient chatClient;
    private final ChannelRegistry channelRegistry;
    private final SseProperties sseProperties;

    private final Set<ResponseBodyEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final Semaphore concurrencyLimit;
    private final ScheduledExecutorService heartbeatScheduler;
    private final Executor streamExecutor;
    private final ObjectMapper jsonMapper;

    public SseStreamingService(
            ChatClient chatClient,
            ChannelRegistry channelRegistry,
            SseProperties sseProperties,
            ObjectMapper jsonMapper) {
        this.chatClient = chatClient;
        this.channelRegistry = channelRegistry;
        this.sseProperties = sseProperties;
        this.jsonMapper = jsonMapper;
        this.concurrencyLimit = new Semaphore(sseProperties.maxConcurrent());
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.streamExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates a new emitter, registers cleanup callbacks and returns it.
     * Returns {@code null} if the concurrency limit is exhausted.
     */
    public ResponseBodyEmitter createEmitter() {
        if (!concurrencyLimit.tryAcquire()) {
            return null;
        }
        ResponseBodyEmitter emitter =
                new ResponseBodyEmitter(sseProperties.timeout().toMillis());
        emitters.add(emitter);

        Runnable release = () -> {
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
     * Streams the agent's response to {@code request.content()} for the given conversation id.
     *
     * <p>Runs the ChatClient stream on a virtual thread; writes to the emitter under a
     * synchronization lock shared with the heartbeat scheduler.
     */
    public void stream(ResponseBodyEmitter emitter, String conversationId, String userContent) {
        Object lock = new Object();
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(emitter, lock),
                sseProperties.heartbeatInterval().toMillis(),
                sseProperties.heartbeatInterval().toMillis(),
                TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> heartbeat.cancel(false));
        emitter.onTimeout(() -> heartbeat.cancel(false));
        emitter.onError(ex -> heartbeat.cancel(false));

        streamExecutor.execute(() -> runStream(emitter, lock, conversationId, userContent));
    }

    private void runStream(ResponseBodyEmitter emitter, Object lock, String conversationId, String userContent) {
        String messageId = "msg_" + UUID.randomUUID();
        String textBlockId = "text_" + UUID.randomUUID();
        try {
            channelRegistry.publishMessageReceivedEvent(new ChannelMessageReceivedEvent("Web Chat REST", userContent));

            sendEvent(
                    emitter,
                    lock,
                    VercelSseEvent.MessageStart.builder().messageId(messageId).build());
            sendEvent(
                    emitter,
                    lock,
                    VercelSseEvent.TextStart.builder().id(textBlockId).build());

            chatClient.prompt(userContent).advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)).stream()
                    .content()
                    .doOnNext(delta -> {
                        if (delta != null && !delta.isEmpty()) {
                            trySendEvent(
                                    emitter,
                                    lock,
                                    VercelSseEvent.TextDelta.builder()
                                            .id(textBlockId)
                                            .delta(delta)
                                            .build());
                        }
                    })
                    .blockLast();

            sendEvent(
                    emitter,
                    lock,
                    VercelSseEvent.TextEnd.builder().id(textBlockId).build());
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
        }
    }

    /** Writes a v4 Vercel AI data-stream line: {@code code:json\n}. */
    void writeLine(ResponseBodyEmitter emitter, Object lock, char code, Object payload) throws IOException {
        String line = code + ":" + jsonMapper.writeValueAsString(payload) + "\n";
        synchronized (lock) {
            emitter.send(line, org.springframework.http.MediaType.TEXT_PLAIN);
        }
    }

    /** Converts a typed UI Message Stream event to the v4 data stream wire line. */
    private void sendEvent(ResponseBodyEmitter emitter, Object lock, Object payload) throws IOException {
        if (!(payload instanceof VercelSseEvent event)) {
            writeLine(emitter, lock, '2', payload);
            return;
        }
        switch (event) {
            case VercelSseEvent.TextDelta d -> writeLine(emitter, lock, '0', d.delta());
            case VercelSseEvent.Error e -> writeLine(emitter, lock, '3', e.errorText());
            case VercelSseEvent.Finish ignored -> writeLine(emitter, lock, 'd', FinishLine.stop());
            case VercelSseEvent.FinishStep ignored -> writeLine(emitter, lock, 'e', StepLine.stop());
            case VercelSseEvent.ToolInputAvailable t ->
                writeLine(emitter, lock, '9', new ToolCallLine(t.toolCallId(), t.toolName(), t.input()));
            case VercelSseEvent.ToolOutputAvailable t ->
                writeLine(emitter, lock, 'a', new ToolResultLine(t.toolCallId(), t.output()));
            // Structural events (message-start, text-start/end, reasoning-*, tool-input-start/delta)
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

    private record UsageLine(int promptTokens, int completionTokens) {}

    /** v4 {@code 9:} tool-call line payload. */
    private record ToolCallLine(String toolCallId, String toolName, Object args) {}

    /** v4 {@code a:} tool-result line payload. */
    private record ToolResultLine(String toolCallId, Object result) {}

    private void trySendEvent(ResponseBodyEmitter emitter, Object lock, Object payload) {
        try {
            sendEvent(emitter, lock, payload);
        } catch (IOException io) {
            // Signal upstream reactor pipeline to bail out — handled by runStream's catch.
            throw new ClientDisconnectedException(io);
        }
    }

    private static final class ClientDisconnectedException extends RuntimeException {
        ClientDisconnectedException(IOException cause) {
            super(cause);
        }
    }

    private void trySendError(ResponseBodyEmitter emitter, Object lock, String message) {
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

    private void sendHeartbeat(ResponseBodyEmitter emitter, Object lock) {
        try {
            // v4 data stream protocol has no comments — send empty text delta as keepalive.
            writeLine(emitter, lock, '0', "");
        } catch (IOException disconnect) {
            log.debug("Heartbeat detected client disconnect", disconnect);
        } catch (IllegalStateException completed) {
            // Emitter already completed — benign race with runStream finishing.
        }
    }

    /** Test / diagnostic accessor for the live emitter registry. */
    public int activeEmitters() {
        return emitters.size();
    }

    /** Admission-control query — exposed for tests. */
    public int availablePermits() {
        return concurrencyLimit.availablePermits();
    }
}
