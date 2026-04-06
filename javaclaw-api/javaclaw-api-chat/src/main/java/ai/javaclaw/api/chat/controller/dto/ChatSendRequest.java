package ai.javaclaw.api.chat.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/chat/send}.
 *
 * @param content user message text (required, 1..32000 chars)
 * @param conversationId optional conversation id; server generates one if {@code null} or blank
 */
public record ChatSendRequest(@NotBlank @Size(max = 32000) String content, String conversationId) {}
