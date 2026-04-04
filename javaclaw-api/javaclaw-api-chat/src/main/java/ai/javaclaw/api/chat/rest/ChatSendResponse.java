package ai.javaclaw.api.chat.rest;

/**
 * Non-streaming response stub — returned only when SSE negotiation fails.
 */
public record ChatSendResponse(String conversationId, String messageId, String content) {}
