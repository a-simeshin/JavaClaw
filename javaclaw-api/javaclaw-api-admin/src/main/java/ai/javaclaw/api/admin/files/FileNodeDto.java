package ai.javaclaw.api.admin.files;

import java.util.List;

/**
 * Tree node for {@code GET /api/files}. {@code children} is non-{@code null} when
 * {@code type=dir}, otherwise {@code null}.
 */
public record FileNodeDto(String path, String name, String type, long size, List<FileNodeDto> children) {}
