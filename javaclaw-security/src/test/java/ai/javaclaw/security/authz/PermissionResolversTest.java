package ai.javaclaw.security.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ai.javaclaw.users.Permission;
import ai.javaclaw.users.PermissionService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionResolversTest {

    @Mock
    private PermissionService permissionService;

    private PermissionResolvers permissionResolvers;

    @BeforeEach
    void setUp() {
        permissionResolvers = new PermissionResolvers(permissionService);
    }

    @Test
    void register_andGet_returnsRegisteredResolver() {
        PermissionCheck resolver = (userId, targetId, action) -> true;

        permissionResolvers.register("conversation", resolver);
        Optional<PermissionCheck> result = permissionResolvers.get("conversation");

        assertThat(result).isPresent().contains(resolver);
    }

    @Test
    void get_caseInsensitive_returnsResolver() {
        PermissionCheck resolver = (userId, targetId, action) -> true;

        permissionResolvers.register("Conversation", resolver);

        assertThat(permissionResolvers.get("conversation")).isPresent();
        assertThat(permissionResolvers.get("CONVERSATION")).isPresent();
        assertThat(permissionResolvers.get("Conversation")).isPresent();
    }

    @Test
    void get_nonexistentType_returnsEmptyOptional() {
        Optional<PermissionCheck> result = permissionResolvers.get("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void defaultCheck_delegatesToPermissionService_returnsTrue() {
        when(permissionService.userHasPermission("alice", Permission.CHAT_SEND)).thenReturn(true);

        boolean result = permissionResolvers.defaultCheck("alice", "CHAT_SEND");

        assertThat(result).isTrue();
        verify(permissionService).userHasPermission("alice", Permission.CHAT_SEND);
    }

    @Test
    void defaultCheck_delegatesToPermissionService_returnsFalse() {
        when(permissionService.userHasPermission("bob", Permission.CHAT_SEND)).thenReturn(false);

        boolean result = permissionResolvers.defaultCheck("bob", "CHAT_SEND");

        assertThat(result).isFalse();
    }

    @Test
    void defaultCheck_unknownPermissionName_returnsFalse() {
        boolean result = permissionResolvers.defaultCheck("alice", "TOTALLY_UNKNOWN");

        assertThat(result).isFalse();
        verifyNoInteractions(permissionService);
    }

    @Test
    void register_overridesExistingResolver() {
        PermissionCheck first = (userId, targetId, action) -> false;
        PermissionCheck second = (userId, targetId, action) -> true;

        permissionResolvers.register("conversation", first);
        permissionResolvers.register("conversation", second);

        PermissionCheck retrieved = permissionResolvers.get("conversation").orElseThrow();
        assertThat(retrieved.check("u", "t", "a")).isTrue();
    }
}
