package ai.javaclaw.api.admin.files;

/** File body returned by {@code GET /api/files/{path}} and written by {@code PUT}. */
public record FileContentDto(String path, String content) {}
