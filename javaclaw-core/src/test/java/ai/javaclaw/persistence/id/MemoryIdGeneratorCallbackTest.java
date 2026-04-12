package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.memory.Memory;
import org.junit.jupiter.api.Test;

class MemoryIdGeneratorCallbackTest {

    private final MemoryIdGeneratorCallback callback = new MemoryIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final Memory input = Memory.create("user-1", "favorite-color", "blue", "preferences");

        final Memory result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.ownerId()).isEqualTo("user-1");
        assertThat(result.key()).isEqualTo("favorite-color");
        assertThat(result.content()).isEqualTo("blue");
        assertThat(result.category()).isEqualTo("preferences");
    }

    @Test
    void keeps_existing_id() {
        final Memory base = Memory.create("u", "k", "v", "c");
        final Memory withId = new Memory(
                "fixed-id",
                base.ownerId(),
                base.key(),
                base.content(),
                base.category(),
                base.createdAt(),
                base.updatedAt());

        final Memory result = callback.onBeforeConvert(withId);

        assertThat(result).isSameAs(withId);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
