package ai.javaclaw.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void authority_prefixesWithPERM() {
        assertThat(Permission.CHAT_SEND.authority()).isEqualTo("PERM_CHAT_SEND");
        assertThat(Permission.SYSTEM_ADMIN.authority()).isEqualTo("PERM_SYSTEM_ADMIN");
    }

    @Test
    void allPermissions_haveUniqueAuthorities() {
        var authorities = java.util.Arrays.stream(Permission.values())
                .map(Permission::authority)
                .toList();
        assertThat(authorities).doesNotHaveDuplicates();
    }

    @Test
    void allPermissions_authorityStartsWithPERM() {
        for (Permission p : Permission.values()) {
            assertThat(p.authority()).startsWith("PERM_");
        }
    }

    @Test
    void valueOf_roundTrips() {
        for (Permission p : Permission.values()) {
            assertThat(Permission.valueOf(p.name())).isEqualTo(p);
        }
    }
}
