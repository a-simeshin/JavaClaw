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
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class JavaClawPermissionEvaluatorTest {

    @Mock
    private PermissionResolvers permissionResolvers;

    @Mock
    private PermissionService permissionService;

    @Mock
    private Authentication authentication;

    private JavaClawPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new JavaClawPermissionEvaluator(permissionResolvers, permissionService);
    }

    // --- hasPermission(auth, targetId, targetType, permission) ---

    @Test
    void registeredResolver_returnsTrue_whenResolverAllows() {
        PermissionCheck resolver = (userId, targetId, action) -> true;
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("alice");
        when(permissionResolvers.get("conversation")).thenReturn(Optional.of(resolver));

        boolean result = evaluator.hasPermission(authentication, "conv-1", "conversation", "read");

        assertThat(result).isTrue();
    }

    @Test
    void registeredResolver_returnsFalse_whenResolverDenies() {
        PermissionCheck resolver = (userId, targetId, action) -> false;
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("alice");
        when(permissionResolvers.get("conversation")).thenReturn(Optional.of(resolver));

        boolean result = evaluator.hasPermission(authentication, "conv-1", "conversation", "read");

        assertThat(result).isFalse();
    }

    @Test
    void noRegisteredResolver_fallsBackToPermissionService_returnsTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("alice");
        when(permissionResolvers.get("unknown_type")).thenReturn(Optional.empty());
        when(permissionService.userHasPermission("alice", Permission.CHAT_SEND)).thenReturn(true);

        boolean result = evaluator.hasPermission(authentication, "some-id", "unknown_type", "CHAT_SEND");

        assertThat(result).isTrue();
        verify(permissionService).userHasPermission("alice", Permission.CHAT_SEND);
    }

    @Test
    void noRegisteredResolver_fallsBackToPermissionService_returnsFalse() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("bob");
        when(permissionResolvers.get("unknown_type")).thenReturn(Optional.empty());
        when(permissionService.userHasPermission("bob", Permission.CHAT_SEND)).thenReturn(false);

        boolean result = evaluator.hasPermission(authentication, "some-id", "unknown_type", "CHAT_SEND");

        assertThat(result).isFalse();
    }

    @Test
    void unknownPermissionName_returnsFalse() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("alice");
        when(permissionResolvers.get("conversation")).thenReturn(Optional.empty());

        boolean result = evaluator.hasPermission(authentication, "conv-1", "conversation", "NONEXISTENT_PERM");

        assertThat(result).isFalse();
        verifyNoInteractions(permissionService);
    }

    @Test
    void nullAuthentication_returnsFalse() {
        boolean result = evaluator.hasPermission(null, "conv-1", "conversation", "read");

        assertThat(result).isFalse();
        verifyNoInteractions(permissionResolvers, permissionService);
    }

    @Test
    void unauthenticatedPrincipal_returnsFalse() {
        when(authentication.isAuthenticated()).thenReturn(false);

        boolean result = evaluator.hasPermission(authentication, "conv-1", "conversation", "read");

        assertThat(result).isFalse();
        verifyNoInteractions(permissionResolvers, permissionService);
    }

    // --- hasPermission(auth, targetDomainObject, permission) ---

    @Test
    void domainObjectVariant_delegatesToDefaultCheck_returnsTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("alice");
        when(permissionService.userHasPermission("alice", Permission.CHAT_SEND)).thenReturn(true);

        boolean result = evaluator.hasPermission(authentication, new Object(), "CHAT_SEND");

        assertThat(result).isTrue();
    }

    @Test
    void domainObjectVariant_nullAuth_returnsFalse() {
        boolean result = evaluator.hasPermission(null, new Object(), "CHAT_SEND");

        assertThat(result).isFalse();
    }
}
