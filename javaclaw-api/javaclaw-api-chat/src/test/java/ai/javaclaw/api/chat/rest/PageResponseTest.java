package ai.javaclaw.api.chat.rest;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.api.chat.controller.dto.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void slicesByPageAndSize() {
        List<String> source = List.of("a", "b", "c", "d", "e");
        PageResponse<String> page = PageResponse.of(source, 1, 2);
        assertThat(page.content()).containsExactly("c", "d");
        assertThat(page.total()).isEqualTo(5);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void emptyContentWhenOutOfRange() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), 5, 10);
        assertThat(page.content()).isEmpty();
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void negativePageCoercedToZero() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), -1, 10);
        assertThat(page.content()).containsExactly("a", "b");
    }
}
