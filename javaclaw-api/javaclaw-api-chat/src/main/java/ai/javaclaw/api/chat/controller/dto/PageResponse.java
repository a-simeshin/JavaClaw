package ai.javaclaw.api.chat.controller.dto;

import java.util.List;

/** Minimal paginated envelope — {@code total} is an upper bound when the store does not support counts. */
public record PageResponse<T>(List<T> content, int page, int size, long total) {

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        if (size <= 0) size = 20;
        if (page < 0) page = 0;
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResponse<>(all.subList(from, to), page, size, all.size());
    }
}
