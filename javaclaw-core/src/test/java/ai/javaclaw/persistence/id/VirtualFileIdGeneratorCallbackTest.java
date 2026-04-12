package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.files.VirtualFile;
import org.junit.jupiter.api.Test;

class VirtualFileIdGeneratorCallbackTest {

    private final VirtualFileIdGeneratorCallback callback = new VirtualFileIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final VirtualFile input = VirtualFile.newGlobalFile("/readme.md", "hello", "text/markdown");

        final VirtualFile result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.path()).isEqualTo("/readme.md");
        assertThat(result.content()).isEqualTo("hello");
        assertThat(result.contentType()).isEqualTo("text/markdown");
        assertThat(result.sizeBytes()).isEqualTo(5L);
    }

    @Test
    void keeps_existing_id() {
        final VirtualFile input = VirtualFile.newGlobalFile("/a.txt", "x", "text/plain");
        final VirtualFile withId = new VirtualFile(
                "fixed-id",
                input.ownerId(),
                input.path(),
                input.content(),
                input.contentType(),
                input.sizeBytes(),
                input.createdAt(),
                input.updatedAt());

        final VirtualFile result = callback.onBeforeConvert(withId);

        assertThat(result).isSameAs(withId);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
