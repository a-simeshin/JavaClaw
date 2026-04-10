package ai.javaclaw.api.chat.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovalRespondRequest(@NotBlank String response) {}
