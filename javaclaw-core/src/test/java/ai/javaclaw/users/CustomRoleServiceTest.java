package ai.javaclaw.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomRoleServiceTest {

    private CustomRoleRepository roleRepo;
    private RolePermissionRepository permRepo;
    private PermissionService permService;
    private CustomRoleService service;

    @BeforeEach
    void setUp() {
        roleRepo = mock(CustomRoleRepository.class);
        permRepo = mock(RolePermissionRepository.class);
        permService = mock(PermissionService.class);
        service = new CustomRoleService(roleRepo, permRepo, permService);
    }

    // --- listAll / listCustom ---

    @Test
    void listAll_delegatesToRepository() {
        var roles = List.of(
                new CustomRole(1L, "ADMIN", "Admin", true, Instant.now()),
                new CustomRole(2L, "REVIEWER", "Code reviewer", false, Instant.now()));
        when(roleRepo.findAllOrdered()).thenReturn(roles);

        assertThat(service.listAll()).hasSize(2);
        verify(roleRepo).findAllOrdered();
    }

    @Test
    void listCustom_returnsOnlyNonBuiltIn() {
        var custom = List.of(new CustomRole(3L, "REVIEWER", "Code reviewer", false, Instant.now()));
        when(roleRepo.findAllCustom()).thenReturn(custom);

        assertThat(service.listCustom()).hasSize(1);
        assertThat(service.listCustom().getFirst().name()).isEqualTo("REVIEWER");
    }

    // --- findByName ---

    @Test
    void findByName_found() {
        var role = new CustomRole(1L, "REVIEWER", "desc", false, Instant.now());
        when(roleRepo.findByName("REVIEWER")).thenReturn(Optional.of(role));

        assertThat(service.findByName("REVIEWER")).isPresent();
    }

    @Test
    void findByName_notFound() {
        when(roleRepo.findByName("NOPE")).thenReturn(Optional.empty());
        assertThat(service.findByName("NOPE")).isEmpty();
    }

    // --- roleExists / isBuiltIn ---

    @Test
    void roleExists_delegatesToRepo() {
        when(roleRepo.existsByName("ADMIN")).thenReturn(true);
        assertThat(service.roleExists("ADMIN")).isTrue();
    }

    @Test
    void isBuiltIn_trueForBuiltInRoles() {
        assertThat(service.isBuiltIn("ADMIN")).isTrue();
        assertThat(service.isBuiltIn("POWER_USER")).isTrue();
        assertThat(service.isBuiltIn("USER")).isTrue();
    }

    @Test
    void isBuiltIn_falseForCustomRoles() {
        assertThat(service.isBuiltIn("REVIEWER")).isFalse();
    }

    // --- create ---

    @Test
    void create_success() {
        when(roleRepo.existsByName("REVIEWER")).thenReturn(false);
        when(roleRepo.save(any())).thenAnswer(inv -> {
            CustomRole r = inv.getArgument(0);
            return new CustomRole(10L, r.name(), r.description(), r.builtIn(), r.createdAt());
        });

        var result = service.create("reviewer", "Code reviewer", Set.of(Permission.CHAT_SEND, Permission.FILE_READ));

        assertThat(result.name()).isEqualTo("REVIEWER");
        assertThat(result.id()).isEqualTo(10L);
        verify(permService).grantPermission("REVIEWER", Permission.CHAT_SEND);
        verify(permService).grantPermission("REVIEWER", Permission.FILE_READ);
    }

    @Test
    void create_normalizesName() {
        when(roleRepo.existsByName("MY_ROLE")).thenReturn(false);
        when(roleRepo.save(any())).thenAnswer(inv -> {
            CustomRole r = inv.getArgument(0);
            return new CustomRole(11L, r.name(), r.description(), r.builtIn(), r.createdAt());
        });

        var result = service.create("my-role", "desc", null);
        assertThat(result.name()).isEqualTo("MY_ROLE");
    }

    @Test
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> service.create("", "desc", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsReservedName() {
        assertThatThrownBy(() -> service.create("ADMIN", "desc", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void create_rejectsDuplicateName() {
        when(roleRepo.existsByName("REVIEWER")).thenReturn(true);
        assertThatThrownBy(() -> service.create("reviewer", "desc", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_withNullPermissions() {
        when(roleRepo.existsByName("EMPTY_ROLE")).thenReturn(false);
        when(roleRepo.save(any())).thenAnswer(inv -> {
            CustomRole r = inv.getArgument(0);
            return new CustomRole(12L, r.name(), r.description(), r.builtIn(), r.createdAt());
        });

        service.create("empty_role", "No perms", null);
        verify(permService, never()).grantPermission(any(), any());
    }

    // --- updateDescription ---

    @Test
    void updateDescription_success() {
        var existing = new CustomRole(5L, "REVIEWER", "old", false, Instant.now());
        when(roleRepo.findByName("REVIEWER")).thenReturn(Optional.of(existing));
        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.updateDescription("REVIEWER", "new desc");
        assertThat(result.description()).isEqualTo("new desc");
    }

    @Test
    void updateDescription_rejectsBuiltIn() {
        var existing = new CustomRole(1L, "ADMIN", "Admin", true, Instant.now());
        when(roleRepo.findByName("ADMIN")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateDescription("ADMIN", "new"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("built-in");
    }

    @Test
    void updateDescription_notFound() {
        when(roleRepo.findByName("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateDescription("NOPE", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // --- setPermissions ---

    @Test
    void setPermissions_success() {
        when(roleRepo.existsByName("REVIEWER")).thenReturn(true);

        service.setPermissions("REVIEWER", Set.of(Permission.CHAT_SEND));

        verify(permRepo).deleteAllByRole("REVIEWER");
        verify(permRepo).save(RolePermission.create("REVIEWER", "CHAT_SEND"));
        verify(permService).clearCache();
    }

    @Test
    void setPermissions_rejectsBuiltIn() {
        when(roleRepo.existsByName("ADMIN")).thenReturn(true);
        assertThatThrownBy(() -> service.setPermissions("ADMIN", Set.of(Permission.CHAT_SEND)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("built-in");
    }

    @Test
    void setPermissions_notFound() {
        when(roleRepo.existsByName("NOPE")).thenReturn(false);
        assertThatThrownBy(() -> service.setPermissions("NOPE", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // --- delete ---

    @Test
    void delete_success() {
        var existing = new CustomRole(5L, "REVIEWER", "desc", false, Instant.now());
        when(roleRepo.findByName("REVIEWER")).thenReturn(Optional.of(existing));

        service.delete("REVIEWER");

        verify(permRepo).deleteAllByRole("REVIEWER");
        verify(roleRepo).delete(existing);
        verify(permService).clearCache();
    }

    @Test
    void delete_rejectsBuiltIn() {
        var existing = new CustomRole(1L, "ADMIN", "Admin", true, Instant.now());
        when(roleRepo.findByName("ADMIN")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete("ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("built-in");
    }

    @Test
    void delete_notFound() {
        when(roleRepo.findByName("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete("NOPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // --- getPermissions ---

    @Test
    void getPermissions_delegatesToPermissionService() {
        when(permService.getPermissionsForRole("REVIEWER")).thenReturn(Set.of(Permission.CHAT_SEND));
        assertThat(service.getPermissions("REVIEWER")).containsExactly(Permission.CHAT_SEND);
    }
}
