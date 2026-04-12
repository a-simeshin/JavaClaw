package ai.javaclaw.api.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.agent.audit.ChatAuditService;
import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.agent.pipeline.ChatService;
import ai.javaclaw.api.chat.configuration.ChatRestConfiguration;
import ai.javaclaw.channels.ChannelContextService;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

/**
 * Compatibility tests for {@link SseStreamingService} reasoning lifecycle — proves that the
 * concrete {@code ReasoningAwareChatModel} contract (AssistantMessage с {@code properties.thinking=true},
 * затем {@code signature}, затем обычный content) корректно транслируется в v4 data-stream wire lines
 * ({@code g:}, {@code j:}, {@code 0:}, {@code e:}, {@code d:}).
 *
 * <p>Tests do not mutate {@link SseStreamingService} — they consume a capturing
 * {@link ResponseBodyEmitter} and assert on the ordered sequence of emitted wire lines.
 */
class SseStreamingServiceReasoningCompatTest {

    private final ChatService chatService = mock(ChatService.class);
    private final ChannelContextService channelContextService = mock(ChannelContextService.class);
    private final ChatMemory chatMemory = mock(ChatMemory.class);
    private final ChatAuditService chatAuditService = mock(ChatAuditService.class);

    private SseStreamingService newService() {
        return new SseStreamingService(
                chatService,
                channelContextService,
                chatMemory,
                chatAuditService,
                new ChatRestConfiguration.SseProperties(Duration.ofSeconds(30), Duration.ofSeconds(10), 5),
                tools.jackson.databind.json.JsonMapper.builder().build());
    }

    private static CapturingEmitter capturing() {
        return new CapturingEmitter();
    }

    /** Emitter that appends each {@code send(...)} payload to an in-memory list. */
    private static final class CapturingEmitter extends ResponseBodyEmitter {
        private final CopyOnWriteArrayList<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public void send(final Object data, final MediaType mediaType) throws IOException {
            lines.add(data.toString());
        }

        @Override
        public void send(final Object data) throws IOException {
            lines.add(data.toString());
        }

        List<String> lines() {
            return lines;
        }
    }

    // -------------------------------------------------------------------------
    // Test 1 — OpenAI-compat reasoning → g: wire frames, then signature, then text
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("reasoning (thinking → signature → content) → g:, j:, 0:, e:, d: in order")
    void reasoningFromOpenAiCompat_emitsGKadres() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final var thinking1 = AssistantMessage.builder()
                .content("думаю")
                .properties(Map.of("thinking", true))
                .build();
        final var thinking2 = AssistantMessage.builder()
                .content("шаг 2")
                .properties(Map.of("thinking", true))
                .build();
        final var signature = AssistantMessage.builder()
                .content("")
                .properties(Map.of("signature", "reasoning-1"))
                .build();
        final var finalText = new AssistantMessage("Ответ: 42");

        final Flux<ChatResponse> flux = Flux.just(
                new ChatResponse(List.of(new Generation(thinking1))),
                new ChatResponse(List.of(new Generation(thinking2))),
                new ChatResponse(List.of(new Generation(signature))),
                new ChatResponse(List.of(new Generation(finalText))));
        when(chatService.stream(eq("compat-1"), isNull(), eq("q?"), isNull())).thenReturn(flux);

        final SseStreamingService service = newService();
        final CapturingEmitter emitter = capturing();

        service.stream(emitter, "compat-1", "q?");

        verify(chatAuditService, timeout(5000))
                .log(eq("compat-1"), eq("stream-completed"), isNull(), anyString(), anyLong(), isNull(), any(), any());

        final List<String> lines = emitter.lines();

        // Reasoning deltas — обе thinking-порции в отдельных g:-кадрах.
        final List<String> gFrames =
                lines.stream().filter(l -> l.startsWith("g:")).toList();
        assertThat(gFrames).hasSize(2);
        assertThat(gFrames.get(0)).contains("думаю");
        assertThat(gFrames.get(1)).contains("шаг 2");

        // Signature — отдельный j:-кадр, эмитится ReasoningEnd-ом signature-ветки.
        final List<String> jFrames =
                lines.stream().filter(l -> l.startsWith("j:")).toList();
        assertThat(jFrames).hasSize(1);
        assertThat(jFrames.get(0)).contains("signature").contains("reasoning-1");

        // Финальный текст — 0:-кадр.
        final List<String> textFrames =
                lines.stream().filter(l -> l.startsWith("0:")).toList();
        assertThat(textFrames).hasSize(1);
        assertThat(textFrames.get(0)).contains("Ответ: 42");

        // FinishStep (e:) и Finish (d:) — последние два кадра.
        final List<String> eFrames =
                lines.stream().filter(l -> l.startsWith("e:")).toList();
        final List<String> dFrames =
                lines.stream().filter(l -> l.startsWith("d:")).toList();
        assertThat(eFrames).hasSize(1);
        assertThat(dFrames).hasSize(1);

        // Order invariant: g (both) < j < 0 < e < d.
        final int firstG = indexOfPrefix(lines, "g:");
        final int lastG = lastIndexOfPrefix(lines, "g:");
        final int jIdx = indexOfPrefix(lines, "j:");
        final int textIdx = indexOfPrefix(lines, "0:");
        final int eIdx = indexOfPrefix(lines, "e:");
        final int dIdx = indexOfPrefix(lines, "d:");

        assertThat(firstG).isLessThan(lastG);
        assertThat(lastG).isLessThan(jIdx);
        assertThat(jIdx).isLessThan(textIdx);
        assertThat(textIdx).isLessThan(eIdx);
        assertThat(eIdx).isLessThan(dIdx);
    }

    // -------------------------------------------------------------------------
    // Test 2 — reasoning closed by signature before tool-call chunk, then final text
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("signature closes reasoning before tool-call; final content emitted as text")
    void reasoningFollowedByToolCall_signatureClosesBlockBeforeToolCall() throws Exception {
        doNothing().when(channelContextService).saveContext(any(), any(), any());

        final var reasoning = AssistantMessage.builder()
                .content("reasoning step")
                .properties(Map.of("thinking", true))
                .build();
        final var signature = AssistantMessage.builder()
                .content("")
                .properties(Map.of("signature", "r1"))
                .build();
        // Tool-call chunk: no textual content, only a tool call list (SseStreamingService
        // captures it into `toolCalls` for audit but emits no wire frame inline).
        final var toolCallMsg = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "searchDocs", "{\"q\":\"x\"}")))
                .build();
        final var finalText = new AssistantMessage("final content");

        final Flux<ChatResponse> flux = Flux.just(
                new ChatResponse(List.of(new Generation(reasoning))),
                new ChatResponse(List.of(new Generation(signature))),
                new ChatResponse(List.of(new Generation(toolCallMsg))),
                new ChatResponse(List.of(new Generation(finalText))));
        when(chatService.stream(eq("compat-2"), isNull(), eq("q?"), isNull())).thenReturn(flux);

        final SseStreamingService service = newService();
        final CapturingEmitter emitter = capturing();

        service.stream(emitter, "compat-2", "q?");

        verify(chatAuditService, timeout(5000))
                .log(eq("compat-2"), eq("stream-completed"), isNull(), anyString(), anyLong(), isNull(), any(), any());

        final List<String> lines = emitter.lines();

        // Reasoning delta present.
        final List<String> gFrames =
                lines.stream().filter(l -> l.startsWith("g:")).toList();
        assertThat(gFrames).hasSize(1);
        assertThat(gFrames.get(0)).contains("reasoning step");

        // Signature explicitly closes reasoning block.
        final List<String> jFrames =
                lines.stream().filter(l -> l.startsWith("j:")).toList();
        assertThat(jFrames).hasSize(1);
        assertThat(jFrames.get(0)).contains("r1");

        // Final content emitted as 0: text delta (и только один — tool-call chunk не должен
        // произвести 0:-кадр, т.к. его content="" и он не thinking).
        final List<String> textFrames =
                lines.stream().filter(l -> l.startsWith("0:")).toList();
        assertThat(textFrames).hasSize(1);
        assertThat(textFrames.get(0)).contains("final content");

        // Order: g: → j: → 0: → e: → d:.
        final int gIdx = indexOfPrefix(lines, "g:");
        final int jIdx = indexOfPrefix(lines, "j:");
        final int textIdx = indexOfPrefix(lines, "0:");
        final int eIdx = indexOfPrefix(lines, "e:");
        final int dIdx = indexOfPrefix(lines, "d:");

        assertThat(gIdx).isLessThan(jIdx);
        assertThat(jIdx).isLessThan(textIdx);
        assertThat(textIdx).isLessThan(eIdx);
        assertThat(eIdx).isLessThan(dIdx);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static int indexOfPrefix(final List<String> lines, final String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOfPrefix(final List<String> lines, final String prefix) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
