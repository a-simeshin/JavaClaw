package ai.javaclaw.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link RoleModelAllowlistService}. */
@ExtendWith(MockitoExtension.class)
class RoleModelAllowlistServiceTest {

    @Mock
    private RoleModelAllowlistRepository repository;

    private RoleModelAllowlistService service;

    @BeforeEach
    void setUp() {
        service = new RoleModelAllowlistService(repository);
    }

    @Nested
    class GetAllowedModels {

        @Test
        void returnsModelsForRole() {
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(
                            new RoleModelAllowlist("1", "USER", "gpt-4o", Instant.now()),
                            new RoleModelAllowlist("2", "USER", "claude-3-haiku", Instant.now())));
            assertThat(service.getAllowedModels("USER")).containsExactly("gpt-4o", "claude-3-haiku");
        }

        @Test
        void returnsEmptyForRoleWithNoEntries() {
            when(repository.findByRole("ADMIN")).thenReturn(List.of());
            assertThat(service.getAllowedModels("ADMIN")).isEmpty();
        }

        @Test
        void returnsEmptyForNullRole() {
            assertThat(service.getAllowedModels(null)).isEmpty();
        }

        @Test
        void returnsEmptyForBlankRole() {
            assertThat(service.getAllowedModels("  ")).isEmpty();
        }

        @Test
        void cachesResult() {
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(new RoleModelAllowlist("1", "USER", "gpt-4o", Instant.now())));
            service.getAllowedModels("USER");
            service.getAllowedModels("USER");
            verify(repository).findByRole("USER"); // called only once
        }
    }

    @Nested
    class IsModelAllowed {

        @Test
        void allowedWhenNoRestrictions() {
            when(repository.findByRole("ADMIN")).thenReturn(List.of());
            assertThat(service.isModelAllowed("ADMIN", "any-model")).isTrue();
        }

        @Test
        void allowedWhenModelInList() {
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(new RoleModelAllowlist("1", "USER", "gpt-4o", Instant.now())));
            assertThat(service.isModelAllowed("USER", "gpt-4o")).isTrue();
        }

        @Test
        void deniedWhenModelNotInList() {
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(new RoleModelAllowlist("1", "USER", "gpt-4o", Instant.now())));
            assertThat(service.isModelAllowed("USER", "claude-3-opus")).isFalse();
        }

        @Test
        void allowedWhenModelIdIsNull() {
            assertThat(service.isModelAllowed("USER", null)).isTrue();
        }

        @Test
        void allowedWhenModelIdIsBlank() {
            assertThat(service.isModelAllowed("USER", "  ")).isTrue();
        }
    }

    @Nested
    class ValidateModelAccess {

        @Test
        void throwsWhenDenied() {
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(new RoleModelAllowlist("1", "USER", "gpt-4o", Instant.now())));
            assertThatThrownBy(() -> service.validateModelAccess("USER", "claude-3-opus"))
                    .isInstanceOf(RoleModelAllowlistService.ModelAccessDeniedException.class)
                    .hasMessageContaining("claude-3-opus")
                    .hasMessageContaining("USER");
        }

        @Test
        void doesNotThrowWhenAllowed() {
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(new RoleModelAllowlist("1", "USER", "gpt-4o", Instant.now())));
            service.validateModelAccess("USER", "gpt-4o"); // no exception
        }
    }

    @Nested
    class SetAllowedModels {

        @Test
        void replacesExistingEntries() {
            service.setAllowedModels("USER", List.of("gpt-4o", "claude-3-haiku"));
            verify(repository).deleteAllByRole("USER");
            verify(repository, org.mockito.Mockito.times(2)).save(any(RoleModelAllowlist.class));
        }

        @Test
        void clearsWhenEmptyList() {
            service.setAllowedModels("USER", List.of());
            verify(repository).deleteAllByRole("USER");
            verify(repository, never()).save(any());
        }

        @Test
        void clearsWhenNull() {
            service.setAllowedModels("USER", null);
            verify(repository).deleteAllByRole("USER");
            verify(repository, never()).save(any());
        }

        @Test
        void throwsForBlankRole() {
            assertThatThrownBy(() -> service.setAllowedModels("", List.of("gpt-4o")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void evictsCacheAfterSet() {
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(new RoleModelAllowlist("1", "USER", "gpt-4o", Instant.now())));
            service.getAllowedModels("USER"); // populate cache
            service.setAllowedModels("USER", List.of("claude-3-haiku"));
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(new RoleModelAllowlist("2", "USER", "claude-3-haiku", Instant.now())));
            assertThat(service.getAllowedModels("USER")).containsExactly("claude-3-haiku");
        }
    }

    @Nested
    class AddAllowedModel {

        @Test
        void addsNewModel() {
            when(repository.existsByRoleAndModelId("USER", "gpt-4o")).thenReturn(false);
            service.addAllowedModel("USER", "gpt-4o");
            verify(repository).save(any(RoleModelAllowlist.class));
        }

        @Test
        void skipsDuplicate() {
            when(repository.existsByRoleAndModelId("USER", "gpt-4o")).thenReturn(true);
            service.addAllowedModel("USER", "gpt-4o");
            verify(repository, never()).save(any());
        }

        @Test
        void throwsForBlankRole() {
            assertThatThrownBy(() -> service.addAllowedModel("", "gpt-4o"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsForBlankModelId() {
            assertThatThrownBy(() -> service.addAllowedModel("USER", "")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RemoveAllowedModel {

        @Test
        void removesModel() {
            service.removeAllowedModel("USER", "gpt-4o");
            verify(repository).deleteByRoleAndModelId("USER", "gpt-4o");
        }

        @Test
        void throwsForBlankRole() {
            assertThatThrownBy(() -> service.removeAllowedModel("", "gpt-4o"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsForBlankModelId() {
            assertThatThrownBy(() -> service.removeAllowedModel("USER", ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class CacheEviction {

        @Test
        void evictCacheRemovesEntry() {
            when(repository.findByRole("USER"))
                    .thenReturn(List.of(new RoleModelAllowlist("1", "USER", "gpt-4o", Instant.now())));
            service.getAllowedModels("USER");
            service.evictCache("USER");
            service.getAllowedModels("USER");
            verify(repository, org.mockito.Mockito.times(2)).findByRole("USER");
        }

        @Test
        void evictAllCacheClearsAll() {
            when(repository.findByRole("USER")).thenReturn(List.of());
            when(repository.findByRole("ADMIN")).thenReturn(List.of());
            service.getAllowedModels("USER");
            service.getAllowedModels("ADMIN");
            service.evictAllCache();
            service.getAllowedModels("USER");
            service.getAllowedModels("ADMIN");
            verify(repository, org.mockito.Mockito.times(2)).findByRole("USER");
            verify(repository, org.mockito.Mockito.times(2)).findByRole("ADMIN");
        }
    }

    @Nested
    class ModelAccessDeniedExceptionTest {

        @Test
        void hasRoleAndModelId() {
            var ex = new RoleModelAllowlistService.ModelAccessDeniedException("USER", "gpt-4o");
            assertThat(ex.getRole()).isEqualTo("USER");
            assertThat(ex.getModelId()).isEqualTo("gpt-4o");
            assertThat(ex.getMessage()).contains("USER").contains("gpt-4o");
        }
    }
}
