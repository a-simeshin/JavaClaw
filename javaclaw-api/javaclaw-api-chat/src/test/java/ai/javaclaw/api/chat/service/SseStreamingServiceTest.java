package ai.javaclaw.api.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.audit.ChatAuditService;
import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.agent.pipeline.ChatService;
import ai.javaclaw.api.chat.configuration.ChatRestConfiguration;
import ai.javaclaw.channels.ChannelContextService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

/**
 * Unit tests for {@link SseStreamingService} — admission control, emitter lifecycle,
 * linear persist/audit/cancel flow (Phase 2 refactor: Flux.toStream + AtomicBoolean cancel).
 */
class SseStreamingServiceTest {

    /** Мок ChatService — streaming/call не вызываются в этих тестах. */
    private final ChatService chatService = mock(ChatService.class);

    /** Мок ChannelContextService — saveContext не вызывается в этих тестах. */
    private final ChannelContextService channelContextService = mock(ChannelContextService.class);

    /** Мок ChatMemory — для проверки persist. */
    private final ChatMemory chatMemory = mock(ChatMemory.class);

    /** Мок ChatAuditService — для проверки audit. */
    private final ChatAuditService chatAuditService = mock(ChatAuditService.class);

    private SseStreamingService newService(final ChatRestConfiguration.SseProperties props) {
        return new SseStreamingService(
                chatService,
                channelContextService,
                chatMemory,
                chatAuditService,
                props,
                tools.jackson.databind.json.JsonMapper.builder().build());
    }

    private static ChatRestConfiguration.SseProperties props(final int max) {
        return new ChatRestConfiguration.SseProperties(Duration.ofSeconds(30), Duration.ofSeconds(10), max);
    }

    // -------------------------------------------------------------------------
    // Admission control
    // -------------------------------------------------------------------------

    @Test
    void createEmitterReturnsNullWhenCapacityExhausted() {
        final SseStreamingService service =
                newService(new ChatRestConfiguration.SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 1));

        final ResponseBodyEmitter first = service.createEmitter();
        final ResponseBodyEmitter second = service.createEmitter();

        assertThat(first).isNotNull();
        assertThat(second).isNull();
        assertThat(service.activeEmitters()).isEqualTo(1);
        assertThat(service.availablePermits()).isZero();
    }

    @Test
    void createEmitterTracksConfiguredTimeout() {
        final SseStreamingService service =
                newService(new ChatRestConfiguration.SseProperties(Duration.ofMillis(4242), Duration.ofSeconds(1), 5));

        final ResponseBodyEmitter emitter = service.createEmitter();

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(4242L);
    }

    @Test
    void availablePermitsDecrementsOnCreate() {
        final SseStreamingService service =
                newService(new ChatRestConfiguration.SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 3));

        service.createEmitter();
        service.createEmitter();

        assertThat(service.availablePermits()).isEqualTo(1);
        assertThat(service.activeEmitters()).isEqualTo(2);
    }

    @Test
    void exhaustingAllPermitsReturnsNullAfterMaxReached() {
        final SseStreamingService service =
                newService(new ChatRestConfiguration.SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 2));

        assertThat(service.createEmitter()).isNotNull();
        assertThat(service.createEmitter()).isNotNull();
        assertThat(service.createEmitter()).isNull();
        assertThat(service.availablePermits()).isZero();
    }

    @Test
    void cancelReturnsFalseWhenNoActiveStream() {
        final SseStreamingService service = newService(props(5));
        assertThat(service.cancel("nonexistent-conv")).isFalse();
    }

    // -------------------------------------------------------------------------
    // Phase 2: linear persist + audit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("whenStreamCompletes_thenAccPersisted_andAuditMethodStreamCompleted")
    void stream_completes_persistsAndAuditsWithCompletedMethod() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final Flux<ChatResponse> flux = Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello ")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("world")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("!")))));
        when(chatService.stream(eq("cid-1"), eq("user-1"), eq("hi"), isNull())).thenReturn(flux);

        final SseStreamingService service = newService(props(5));
        final ResponseBodyEmitter emitter = service.createEmitter();

        service.stream(emitter, "cid-1", "user-1", "hi", null);

        // Mockito timeout() blocks until the mock call occurs — this is the completion signal
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Message>> msgCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMemory, timeout(5000)).appendAll(eq("cid-1"), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).hasSize(1);
        assertThat(msgCaptor.getValue().get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(msgCaptor.getValue().get(0).getText()).isEqualTo("Hello world!");

        verify(chatAuditService, timeout(5000))
                .log(
                        eq("cid-1"),
                        eq("stream-completed"),
                        isNull(),
                        eq("Hello world!"),
                        anyLong(),
                        eq("user-1"),
                        any(),
                        any());
        verify(chatAuditService, never()).logError(anyString(), anyString(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("whenCancelFlagSet_thenPartialAccPersisted_andAuditMethodStreamCancelled")
    void stream_cancelled_persistsPartialAndAuditsWithCancelledMethod() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        // Slow flux: emits ~20 chunks at 50ms intervals. We'll cancel after ~200ms.
        final Flux<ChatResponse> slow = Flux.interval(Duration.ofMillis(50))
                .take(40)
                .map(i -> new ChatResponse(List.of(new Generation(new AssistantMessage("chunk-" + i + " ")))));
        when(chatService.stream(eq("cid-2"), isNull(), eq("hi"), isNull())).thenReturn(slow);

        final SseStreamingService service = newService(props(5));
        final ResponseBodyEmitter emitter = service.createEmitter();

        service.stream(emitter, "cid-2", "hi");
        // Let it accumulate a couple of chunks
        Thread.sleep(200);
        assertThat(service.cancel("cid-2")).isTrue();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Message>> msgCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMemory, timeout(5000)).appendAll(eq("cid-2"), msgCaptor.capture());
        // Partial content must be non-empty
        assertThat(msgCaptor.getValue()).hasSize(1);
        final String persistedText = msgCaptor.getValue().get(0).getText();
        assertThat(persistedText).isNotEmpty().contains("chunk-");

        verify(chatAuditService, timeout(5000))
                .log(eq("cid-2"), eq("stream-cancelled"), isNull(), anyString(), anyLong(), isNull(), any(), any());
        // Fix #3: no SRE logError on cancel
        verify(chatAuditService, never()).logError(anyString(), anyString(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("whenStreamErrors_thenAuditLogError_withStreamErrorMethod")
    void stream_errors_callsLogErrorWithErrorMethod() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final RuntimeException boom = new RuntimeException("boom");
        // Pure error Flux — Reactor's toStream() delivers terminal error via Iterator.
        // (Mixing data + error in one Flux has subtle ordering issues with Reactor's BlockingIterator
        // buffer; the cancel test below covers the "partial content is persisted" path instead.)
        final Flux<ChatResponse> errFlux = Flux.error(boom);
        when(chatService.stream(eq("cid-3"), isNull(), eq("hi"), isNull())).thenReturn(errFlux);

        final SseStreamingService service = newService(props(5));
        final ResponseBodyEmitter emitter = service.createEmitter();

        service.stream(emitter, "cid-3", "hi");

        // Audit goes through logError with method=stream-error — this is the critical bug #11 check
        verify(chatAuditService, timeout(5000))
                .logError(eq("cid-3"), eq("stream-error"), isNull(), any(Throwable.class), anyLong(), isNull());
        // No log() success call expected
        verify(chatAuditService, never())
                .log(eq("cid-3"), eq("stream-completed"), any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("whenStreamHasUsageMetadata_thenUsagePassedToAudit")
    void stream_usageMetadata_passedToAuditLog() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(usage.getTotalTokens()).thenReturn(15);
        final ChatResponseMetadata metadata =
                ChatResponseMetadata.builder().usage(usage).build();
        final ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))), metadata);

        when(chatService.stream(eq("cid-4"), isNull(), eq("hi"), isNull())).thenReturn(Flux.just(response));

        final SseStreamingService service = newService(props(5));
        final ResponseBodyEmitter emitter = service.createEmitter();

        service.stream(emitter, "cid-4", "hi");

        final ArgumentCaptor<Usage> usageCaptor = ArgumentCaptor.forClass(Usage.class);
        verify(chatAuditService, timeout(5000))
                .log(
                        eq("cid-4"),
                        eq("stream-completed"),
                        isNull(),
                        eq("ok"),
                        anyLong(),
                        isNull(),
                        usageCaptor.capture(),
                        any());
        assertThat(usageCaptor.getValue()).isSameAs(usage);
    }

    @Test
    @DisplayName("cancel_returnsTrue_whileStreamActive_andFalseAfter")
    void cancel_setsFlagAndReturnsTrue() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final Flux<ChatResponse> slow = Flux.interval(Duration.ofMillis(50))
                .take(40)
                .map(i -> new ChatResponse(List.of(new Generation(new AssistantMessage("t" + i)))));
        when(chatService.stream(eq("cid-5"), isNull(), eq("hi"), isNull())).thenReturn(slow);

        final SseStreamingService service = newService(props(5));
        final ResponseBodyEmitter emitter = service.createEmitter();

        service.stream(emitter, "cid-5", "hi");
        Thread.sleep(150);
        assertThat(service.cancel("cid-5")).isTrue();
        // Wait for runStream finally to run (persist + audit + cancelFlags.remove)
        verify(chatAuditService, timeout(5000))
                .log(eq("cid-5"), eq("stream-cancelled"), isNull(), anyString(), anyLong(), isNull(), any(), any());
        // After stream loop exits, flag removed → cancel returns false
        assertThat(service.cancel("cid-5")).isFalse();
        assertThat(service.cancel("nonexistent")).isFalse();
    }

    // -------------------------------------------------------------------------
    // Thinking / reasoning lifecycle (pre-existing scenarios, updated for new flow)
    // -------------------------------------------------------------------------

    @Test
    void streamEmitsReasoningDeltasForThinkingMetadata() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final var thinkingMsg1 = AssistantMessage.builder()
                .content("Let me think...")
                .properties(Map.of("thinking", true))
                .build();
        final var thinkingMsg2 = AssistantMessage.builder()
                .content("step 2")
                .properties(Map.of("thinking", true))
                .build();
        final var signatureMsg = AssistantMessage.builder()
                .content("")
                .properties(Map.of("signature", "sig_abc123"))
                .build();
        final var textMsg = new AssistantMessage("The answer is 42.");

        final Flux<ChatResponse> flux = Flux.just(
                new ChatResponse(List.of(new Generation(thinkingMsg1))),
                new ChatResponse(List.of(new Generation(thinkingMsg2))),
                new ChatResponse(List.of(new Generation(signatureMsg))),
                new ChatResponse(List.of(new Generation(textMsg))));
        when(chatService.stream(eq("think-test"), isNull(), eq("why?"), isNull()))
                .thenReturn(flux);

        final SseStreamingService service = newService(props(5));
        final ResponseBodyEmitter emitter = service.createEmitter();

        service.stream(emitter, "think-test", "why?");
        // Wait for stream completion via Mockito timeout
        verify(chatAuditService, timeout(5000))
                .log(eq("think-test"), eq("stream-completed"), isNull(), any(), anyLong(), any(), any(), any());
        assertThat(service.cancel("think-test")).isFalse();
    }

    @Test
    void streamHandlesTextOnlyWithoutReasoning() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final Flux<ChatResponse> flux = Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hello ")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("world!")))));
        when(chatService.stream(eq("text-test"), isNull(), eq("hi"), isNull())).thenReturn(flux);

        final SseStreamingService service = newService(props(5));
        final ResponseBodyEmitter emitter = service.createEmitter();

        service.stream(emitter, "text-test", "hi");
        verify(chatMemory, timeout(5000).atLeastOnce()).appendAll(eq("text-test"), any(List.class));
        assertThat(service.cancel("text-test")).isFalse();
    }

    @Test
    void streamClosesUnfinishedReasoningBlock() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final var thinkingMsg = AssistantMessage.builder()
                .content("pondering...")
                .properties(Map.of("thinking", true))
                .build();
        final var textMsg = new AssistantMessage("Done.");

        final Flux<ChatResponse> flux = Flux.just(
                new ChatResponse(List.of(new Generation(thinkingMsg))),
                new ChatResponse(List.of(new Generation(textMsg))));
        when(chatService.stream(eq("unfinished-think"), isNull(), eq("q"), isNull()))
                .thenReturn(flux);

        final SseStreamingService service = newService(props(5));
        final ResponseBodyEmitter emitter = service.createEmitter();

        service.stream(emitter, "unfinished-think", "q");
        verify(chatAuditService, timeout(5000).atLeastOnce())
                .log(eq("unfinished-think"), eq("stream-completed"), isNull(), any(), anyLong(), any(), any(), any());
        assertThat(service.cancel("unfinished-think")).isFalse();
    }

    @Test
    void thinkingPropertiesDefaults() {
        final var pr = new ChatRestConfiguration.ThinkingProperties(true, 0);
        assertThat(pr.enabled()).isTrue();
        assertThat(pr.budgetTokens()).isEqualTo(10_000L);

        final var custom = new ChatRestConfiguration.ThinkingProperties(false, 5000);
        assertThat(custom.enabled()).isFalse();
        assertThat(custom.budgetTokens()).isEqualTo(5000L);
    }

    @Test
    void writeLineEmitsReasoningWireCodeG() throws Exception {
        final SseStreamingService service =
                newService(new ChatRestConfiguration.SseProperties(Duration.ofSeconds(5), Duration.ofSeconds(1), 5));

        final java.util.concurrent.CopyOnWriteArrayList<String> written =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        final ResponseBodyEmitter capturingEmitter = new ResponseBodyEmitter(5000L) {
            @Override
            public void send(Object data, org.springframework.http.MediaType mediaType) throws java.io.IOException {
                written.add(data.toString());
            }
        };

        final Object lock = new Object();
        service.writeLine(capturingEmitter, lock, 'g', "thinking step 1");

        assertThat(written).hasSize(1);
        assertThat(written.get(0)).startsWith("g:");
        assertThat(written.get(0)).contains("thinking step 1");
    }
}
