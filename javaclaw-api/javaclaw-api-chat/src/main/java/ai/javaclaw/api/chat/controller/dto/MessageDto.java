package ai.javaclaw.api.chat.controller.dto;

import java.time.Instant;

/**
 * Single conversation message as returned by {@code GET /api/conversations/{id}/messages}.
 *
 * @param role one of {@code user}, {@code assistant}, {@code system}, {@code tool}
 */
public record MessageDto(String id, String role, String content, Instant createdAt) {}
