package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.users.AppUser;
import org.junit.jupiter.api.Test;

class AppUserIdGeneratorCallbackTest {

    private final AppUserIdGeneratorCallback callback = new AppUserIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final AppUser input = AppUser.create("alice", "hash", "USER");

        final AppUser result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.passwordHash()).isEqualTo("hash");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.active()).isTrue();
    }

    @Test
    void keeps_existing_id() {
        final AppUser input = new AppUser("fixed-id", "bob", "hash2", "ADMIN", true);

        final AppUser result = callback.onBeforeConvert(input);

        assertThat(result).isSameAs(input);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
