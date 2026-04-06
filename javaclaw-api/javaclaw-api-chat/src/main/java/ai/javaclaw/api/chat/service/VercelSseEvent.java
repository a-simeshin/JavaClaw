package ai.javaclaw.api.chat.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Typed UI Message Stream events (Vercel AI SDK protocol v1).
 *
 * <p>Each event is a static nested {@code @Data @Builder} class implementing
 * {@link VercelSseEvent}; the {@code type} field is the protocol discriminator
 * and has a compile-time default via {@code @Builder.Default}.
 *
 * <p>The {@link SseStreamingService} translates these events to the v4 data stream
 * wire format ({@code code:json\n} lines) at send time.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface VercelSseEvent
        permits VercelSseEvent.MessageStart,
                VercelSseEvent.TextStart,
                VercelSseEvent.TextDelta,
                VercelSseEvent.TextEnd,
                VercelSseEvent.ReasoningStart,
                VercelSseEvent.ReasoningDelta,
                VercelSseEvent.ReasoningEnd,
                VercelSseEvent.ToolInputStart,
                VercelSseEvent.ToolInputDelta,
                VercelSseEvent.ToolInputAvailable,
                VercelSseEvent.ToolOutputAvailable,
                VercelSseEvent.FinishStep,
                VercelSseEvent.Finish,
                VercelSseEvent.Error {

    String type();

    /** {@code message-start} — opens a new assistant message. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class MessageStart implements VercelSseEvent {
        @Builder.Default
        String type = "message-start";

        String messageId;
    }

    /** {@code text-start} — begins a text block within a message. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class TextStart implements VercelSseEvent {
        @Builder.Default
        String type = "text-start";

        String id;
    }

    /** {@code text-delta} — one streaming text fragment. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class TextDelta implements VercelSseEvent {
        @Builder.Default
        String type = "text-delta";

        String id;
        String delta;
    }

    /** {@code text-end} — closes a text block. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class TextEnd implements VercelSseEvent {
        @Builder.Default
        String type = "text-end";

        String id;
    }

    /** {@code reasoning-start} — begins a reasoning block (o1-style models). */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class ReasoningStart implements VercelSseEvent {
        @Builder.Default
        String type = "reasoning-start";

        String id;
    }

    /** {@code reasoning-delta} — one streaming reasoning fragment. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class ReasoningDelta implements VercelSseEvent {
        @Builder.Default
        String type = "reasoning-delta";

        String id;
        String delta;
    }

    /** {@code reasoning-end} — closes a reasoning block. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class ReasoningEnd implements VercelSseEvent {
        @Builder.Default
        String type = "reasoning-end";

        String id;
    }

    /** {@code tool-input-start} — announces a tool call by id+name. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class ToolInputStart implements VercelSseEvent {
        @Builder.Default
        String type = "tool-input-start";

        String toolCallId;
        String toolName;
    }

    /** {@code tool-input-delta} — streaming tool input JSON fragment. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class ToolInputDelta implements VercelSseEvent {
        @Builder.Default
        String type = "tool-input-delta";

        String toolCallId;
        String inputTextDelta;
    }

    /** {@code tool-input-available} — tool input is fully assembled. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class ToolInputAvailable implements VercelSseEvent {
        @Builder.Default
        String type = "tool-input-available";

        String toolCallId;
        String toolName;
        Object input;
    }

    /** {@code tool-output-available} — tool finished, result is attached. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class ToolOutputAvailable implements VercelSseEvent {
        @Builder.Default
        String type = "tool-output-available";

        String toolCallId;
        Object output;
    }

    /** {@code finish-step} — ends one step of a multi-step turn. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class FinishStep implements VercelSseEvent {
        @Builder.Default
        String type = "finish-step";
    }

    /** {@code finish} — ends the whole message turn. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class Finish implements VercelSseEvent {
        @Builder.Default
        String type = "finish";
    }

    /** {@code error} — stream-level error, terminates the stream. */
    @Data
    @Builder
    @Accessors(fluent = true)
    final class Error implements VercelSseEvent {
        @Builder.Default
        String type = "error";

        String errorText;
    }
}
