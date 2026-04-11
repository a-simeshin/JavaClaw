package ai.javaclaw.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {

    private RolePermissionRepository rolePermissionRepo;
    private AppUserRepository userRepo;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        rolePermissionRepo = mock(RolePermissionRepository.class);
        userRepo = mock(AppUserRepository.class);
        service = new PermissionService(rolePermissionRepo, userRepo);
    }

    @Test
    void getPermissionsForRole_loadsFromDb() {
        when(rolePermissionRepo.findPermissionsByRole("USER"))
                .thenReturn(List.of("CHAT_SEND", "CHAT_READ", "FILE_READ"));

        var perms = service.getPermissionsForRole("USER");

        assertThat(perms).containsExactlyInAnyOrder(Permission.CHAT_SEND, Permission.CHAT_READ, Permission.FILE_READ);
    }

    @Test
    void getPermissionsForRole_cachesResult() {
        when(rolePermissionRepo.findPermissionsByRole("ADMIN")).thenReturn(List.of("SYSTEM_ADMIN"));

        service.getPermissionsForRole("ADMIN");
        service.getPermissionsForRole("ADMIN");

        verify(rolePermissionRepo, times(1)).findPermissionsByRole("ADMIN");
    }

    @Test
    void getPermissionsForRole_ignoresUnknownPermissions() {
        when(rolePermissionRepo.findPermissionsByRole("USER")).thenReturn(List.of("CHAT_SEND", "UNKNOWN_FUTURE_PERM"));

        var perms = service.getPermissionsForRole("USER");

        assertThat(perms).containsExactly(Permission.CHAT_SEND);
    }

    @Test
    void getPermissionsForRole_blankRole_throws() {
        assertThatThrownBy(() -> service.getPermissionsForRole("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roleHasPermission_true() {
        when(rolePermissionRepo.findPermissionsByRole("ADMIN")).thenReturn(List.of("SYSTEM_ADMIN", "CHAT_SEND"));

        assertThat(service.roleHasPermission("ADMIN", Permission.SYSTEM_ADMIN)).isTrue();
    }

    @Test
    void roleHasPermission_false() {
        when(rolePermissionRepo.findPermissionsByRole("USER")).thenReturn(List.of("CHAT_SEND"));

        assertThat(service.roleHasPermission("USER", Permission.SYSTEM_ADMIN)).isFalse();
    }

    @Test
    void roleHasPermission_nullPermission_throws() {
        assertThatThrownBy(() -> service.roleHasPermission("ADMIN", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userHasPermission_existingUser() {
        when(userRepo.findByUsername("admin"))
                .thenReturn(Optional.of(new AppUser("1", "admin", "{noop}admin", "ADMIN", true)));
        when(rolePermissionRepo.findPermissionsByRole("ADMIN")).thenReturn(List.of("SYSTEM_ADMIN"));

        assertThat(service.userHasPermission("admin", Permission.SYSTEM_ADMIN)).isTrue();
    }

    @Test
    void userHasPermission_nonExistentUser() {
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(service.userHasPermission("ghost", Permission.CHAT_SEND)).isFalse();
    }

    @Test
    void getUserPermissions_existingUser() {
        when(userRepo.findByUsername("user"))
                .thenReturn(Optional.of(new AppUser("2", "user", "{noop}user", "USER", true)));
        when(rolePermissionRepo.findPermissionsByRole("USER")).thenReturn(List.of("CHAT_SEND", "CHAT_READ"));

        var perms = service.getUserPermissions("user");

        assertThat(perms).containsExactlyInAnyOrder(Permission.CHAT_SEND, Permission.CHAT_READ);
    }

    @Test
    void getUserPermissions_nonExistentUser_returnsEmpty() {
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(service.getUserPermissions("ghost")).isEmpty();
    }

    @Test
    void compareRoles_hierarchy() {
        assertThat(service.compareRoles("ADMIN", "USER")).isGreaterThan(0);
        assertThat(service.compareRoles("POWER_USER", "USER")).isGreaterThan(0);
        assertThat(service.compareRoles("ADMIN", "POWER_USER")).isGreaterThan(0);
        assertThat(service.compareRoles("USER", "USER")).isEqualTo(0);
        assertThat(service.compareRoles("USER", "ADMIN")).isLessThan(0);
    }

    @Test
    void isRoleAtLeast_checks() {
        assertThat(service.isRoleAtLeast("ADMIN", "USER")).isTrue();
        assertThat(service.isRoleAtLeast("ADMIN", "ADMIN")).isTrue();
        assertThat(service.isRoleAtLeast("USER", "ADMIN")).isFalse();
        assertThat(service.isRoleAtLeast("POWER_USER", "USER")).isTrue();
        assertThat(service.isRoleAtLeast("POWER_USER", "ADMIN")).isFalse();
    }

    @Test
    void compareRoles_unknownRole_ranksBelow() {
        assertThat(service.compareRoles("CUSTOM", "USER")).isLessThan(0);
    }

    @Test
    void getRoleHierarchy_orderedAscending() {
        assertThat(service.getRoleHierarchy()).containsExactly("USER", "POWER_USER", "ADMIN");
    }

    @Test
    void grantPermission_addsToDb() {
        when(rolePermissionRepo.existsByRoleAndPermission("USER", "SYSTEM_ADMIN"))
                .thenReturn(false);

        service.grantPermission("USER", Permission.SYSTEM_ADMIN);

        verify(rolePermissionRepo).save(any(RolePermission.class));
    }

    @Test
    void grantPermission_alreadyExists_skips() {
        when(rolePermissionRepo.existsByRoleAndPermission("ADMIN", "SYSTEM_ADMIN"))
                .thenReturn(true);

        service.grantPermission("ADMIN", Permission.SYSTEM_ADMIN);

        verify(rolePermissionRepo, never()).save(any());
    }

    @Test
    void revokePermission_deletesFromDb() {
        service.revokePermission("ADMIN", Permission.SYSTEM_ADMIN);

        verify(rolePermissionRepo).deleteByRoleAndPermission("ADMIN", "SYSTEM_ADMIN");
    }

    @Test
    void clearCache_evictsAll() {
        when(rolePermissionRepo.findPermissionsByRole("USER")).thenReturn(List.of("CHAT_SEND"));

        service.getPermissionsForRole("USER"); // populate cache
        service.clearCache();
        service.getPermissionsForRole("USER"); // should reload

        verify(rolePermissionRepo, times(2)).findPermissionsByRole("USER");
    }

    @Test
    void grantPermission_evictsCache() {
        when(rolePermissionRepo.findPermissionsByRole("USER")).thenReturn(List.of("CHAT_SEND"));
        when(rolePermissionRepo.existsByRoleAndPermission("USER", "FILE_READ")).thenReturn(false);

        service.getPermissionsForRole("USER"); // populate cache
        service.grantPermission("USER", Permission.FILE_READ); // should evict
        service.getPermissionsForRole("USER"); // should reload

        verify(rolePermissionRepo, times(2)).findPermissionsByRole("USER");
    }
}
