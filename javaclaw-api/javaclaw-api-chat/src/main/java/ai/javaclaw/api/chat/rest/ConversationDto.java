package ai.javaclaw.api.chat.rest;

import java.time.Instant;

/** Summary projection of a conversation for list endpoints. */
public record ConversationDto(String id, String title, Instant createdAt, Instant updatedAt, int messageCount) {}
