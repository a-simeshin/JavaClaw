package ai.javaclaw.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.users.PermissionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link RoleAgentConfigService}. */
@ExtendWith(MockitoExtension.class)
class RoleAgentConfigServiceTest {

    @Mock
    private RoleAgentConfigRepository repository;

    @Mock
    private PermissionService permissionService;

    private RoleAgentConfigService service;

    @BeforeEach
    void setUp() {
        service = new RoleAgentConfigService(repository, permissionService);
    }

    @Nested
    class ResolveForRole {

        @Test
        void exactMatch() {
            RoleAgentConfig config = new RoleAgentConfig("1", "ADMIN", "gpt-4o", true, 50000, null, 200000);
            when(repository.findByRole("ADMIN")).thenReturn(Optional.of(config));
            Optional<RoleAgentConfig> result = service.resolveForRole("ADMIN");
            assertThat(result).isPresent();
            assertThat(result.get().modelId()).isEqualTo("gpt-4o");
        }

        @Test
        void hierarchyFallback_userFallsToAdmin() {
            when(repository.findByRole("USER")).thenReturn(Optional.empty());
            when(repository.findByRole("POWER_USER")).thenReturn(Optional.empty());
            RoleAgentConfig adminConfig = new RoleAgentConfig("1", "ADMIN", "gpt-4o", true, 50000, null, 200000);
            when(repository.findByRole("ADMIN")).thenReturn(Optional.of(adminConfig));

            Optional<RoleAgentConfig> result = service.resolveForRole("USER");
            assertThat(result).isPresent();
            assertThat(result.get().role()).isEqualTo("ADMIN");
        }

        @Test
        void hierarchyFallback_userFallsToPowerUser() {
            when(repository.findByRole("USER")).thenReturn(Optional.empty());
            RoleAgentConfig powerConfig =
                    new RoleAgentConfig("2", "POWER_USER", "claude-3-haiku", false, 10000, null, 150000);
            when(repository.findByRole("POWER_USER")).thenReturn(Optional.of(powerConfig));

            Optional<RoleAgentConfig> result = service.resolveForRole("USER");
            assertThat(result).isPresent();
            assertThat(result.get().role()).isEqualTo("POWER_USER");
        }

        @Test
        void nullRole_returnsEmpty() {
            assertThat(service.resolveForRole(null)).isEmpty();
        }

        @Test
        void blankRole_returnsEmpty() {
            assertThat(service.resolveForRole("  ")).isEmpty();
        }

        @Test
        void noConfigAnywhere_returnsEmpty() {
            when(repository.findByRole("USER")).thenReturn(Optional.empty());
            when(repository.findByRole("POWER_USER")).thenReturn(Optional.empty());
            when(repository.findByRole("ADMIN")).thenReturn(Optional.empty());
            assertThat(service.resolveForRole("USER")).isEmpty();
        }

        @Test
        void customRole_noHierarchyFallback() {
            when(repository.findByRole("ANALYST")).thenReturn(Optional.empty());
            assertThat(service.resolveForRole("ANALYST")).isEmpty();
        }

        @Test
        void cacheWorks() {
            RoleAgentConfig config = new RoleAgentConfig("1", "ADMIN", "gpt-4o", true, 50000, null, 200000);
            when(repository.findByRole("ADMIN")).thenReturn(Optional.of(config));

            service.resolveForRole("ADMIN");
            service.resolveForRole("ADMIN");

            // Repository should only be called once due to caching
            verify(repository).findByRole("ADMIN");
        }
    }

    @Nested
    class ResolveModelForRole {

        @Test
        void returnsModelId() {
            RoleAgentConfig config = new RoleAgentConfig("1", "ADMIN", "gpt-4o", true, 50000, null, 200000);
            when(repository.findByRole("ADMIN")).thenReturn(Optional.of(config));
            assertThat(service.resolveModelForRole("ADMIN")).isEqualTo("gpt-4o");
        }

        @Test
        void returnsNull_whenNoConfig() {
            when(repository.findByRole("CUSTOM")).thenReturn(Optional.empty());
            assertThat(service.resolveModelForRole("CUSTOM")).isNull();
        }

        @Test
        void returnsNull_whenModelIdNull() {
            RoleAgentConfig config = new RoleAgentConfig("1", "USER", null, false, 10000, null, 100000);
            when(repository.findByRole("USER")).thenReturn(Optional.of(config));
            assertThat(service.resolveModelForRole("USER")).isNull();
        }
    }

    @Nested
    class Save {

        @Test
        void createNew() {
            when(repository.findByRole("POWER_USER")).thenReturn(Optional.empty());
            RoleAgentConfig saved = new RoleAgentConfig("gen-id", "POWER_USER", "claude-3", false, 10000, null, 150000);
            when(repository.save(any())).thenReturn(saved);

            RoleAgentConfig result = service.save("POWER_USER", "claude-3", false, 10000, null, 150000);
            assertThat(result.role()).isEqualTo("POWER_USER");
            assertThat(result.modelId()).isEqualTo("claude-3");
        }

        @Test
        void updateExisting() {
            RoleAgentConfig existing =
                    new RoleAgentConfig("existing-id", "ADMIN", "old-model", false, 10000, null, 100000);
            when(repository.findByRole("ADMIN")).thenReturn(Optional.of(existing));
            RoleAgentConfig saved = new RoleAgentConfig("existing-id", "ADMIN", "new-model", true, 50000, null, 200000);
            when(repository.save(any())).thenReturn(saved);

            RoleAgentConfig result = service.save("ADMIN", "new-model", true, 50000, null, 200000);
            assertThat(result.modelId()).isEqualTo("new-model");
        }

        @Test
        void blankRole_throws() {
            assertThatThrownBy(() -> service.save("", null, false, 10000, null, 100000))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesAndEvictsCache() {
            service.delete("USER");
            verify(repository).deleteByRole("USER");
        }

        @Test
        void blankRole_throws() {
            assertThatThrownBy(() -> service.delete("")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ListAll {

        @Test
        void delegatesToRepository() {
            RoleAgentConfig c1 = new RoleAgentConfig("1", "ADMIN", "gpt-4o", true, 50000, null, 200000);
            RoleAgentConfig c2 = new RoleAgentConfig("2", "USER", null, false, 10000, null, 100000);
            when(repository.findAllOrdered()).thenReturn(List.of(c1, c2));

            List<RoleAgentConfig> result = service.listAll();
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class CacheEviction {

        @Test
        void evictCache_removesEntry() {
            RoleAgentConfig config = new RoleAgentConfig("1", "ADMIN", "gpt-4o", true, 50000, null, 200000);
            when(repository.findByRole("ADMIN")).thenReturn(Optional.of(config));

            service.resolveForRole("ADMIN"); // cache it
            service.evictCache("ADMIN");
            service.resolveForRole("ADMIN"); // should query again

            // After eviction, repository is called twice
            org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).findByRole("ADMIN");
        }

        @Test
        void evictAllCache_clearsAll() {
            RoleAgentConfig config = new RoleAgentConfig("1", "ADMIN", "gpt-4o", true, 50000, null, 200000);
            when(repository.findByRole("ADMIN")).thenReturn(Optional.of(config));

            service.resolveForRole("ADMIN"); // cache it
            service.evictAllCache();
            service.resolveForRole("ADMIN"); // should query again

            org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).findByRole("ADMIN");
        }
    }
}
