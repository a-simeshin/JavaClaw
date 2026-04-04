package ai.javaclaw.api.chat.rest;

import jakarta.validation.constraints.Size;

/** Body for {@code POST /api/conversations}. Both fields are optional. */
public record CreateConversationRequest(@Size(max = 120) String title, @Size(max = 120) String id) {}
