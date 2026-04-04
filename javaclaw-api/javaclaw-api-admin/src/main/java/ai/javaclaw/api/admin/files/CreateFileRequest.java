package ai.javaclaw.api.admin.files;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code POST /api/files}. */
public record CreateFileRequest(@NotBlank String path, String content) {}
