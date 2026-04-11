package ai.javaclaw.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillUsageAuditServiceTest {

    @Mock
    private SkillUsageAuditRepository repository;

    @InjectMocks
    private SkillUsageAuditService service;

    // ── Entity factory methods ──────────────────��────────────────────────────

    @Nested
    class EntityFactoryMethods {

        @Test
        void created_setsCorrectEventType() {
            var audit = SkillUsageAudit.created("s1", "code-review", "admin");
            assertThat(audit.eventType()).isEqualTo("created");
            assertThat(audit.skillId()).isEqualTo("s1");
            assertThat(audit.skillName()).isEqualTo("code-review");
            assertThat(audit.username()).isEqualTo("admin");
            assertThat(audit.detail()).isNull();
            assertThat(audit.id()).isNull();
            assertThat(audit.createdAt()).isNotNull();
        }

        @Test
        void updated_setsCorrectEventTypeAndDetail() {
            var audit = SkillUsageAudit.updated("s2", "summarizer", "user", "name changed");
            assertThat(audit.eventType()).isEqualTo("updated");
            assertThat(audit.detail()).isEqualTo("name changed");
        }

        @Test
        void deleted_setsCorrectEventType() {
            var audit = SkillUsageAudit.deleted("s3", "old-skill", "admin");
            assertThat(audit.eventType()).isEqualTo("deleted");
            assertThat(audit.detail()).isNull();
        }

        @Test
        void enabled_setsCorrectEventType() {
            var audit = SkillUsageAudit.enabled("s4", "my-skill", "admin");
            assertThat(audit.eventType()).isEqualTo("enabled");
        }

        @Test
        void disabled_setsCorrectEventType() {
            var audit = SkillUsageAudit.disabled("s5", "my-skill", "admin");
            assertThat(audit.eventType()).isEqualTo("disabled");
        }

        @Test
        void visibilityChanged_formatsDetail() {
            var audit = SkillUsageAudit.visibilityChanged("s6", "restricted-skill", "admin", "PUBLIC", "RESTRICTED");
            assertThat(audit.eventType()).isEqualTo("visibility_changed");
            assertThat(audit.detail()).isEqualTo("PUBLIC -> RESTRICTED");
        }

        @Test
        void allowlistChanged_setsDetail() {
            var audit = SkillUsageAudit.allowlistChanged("s7", "restricted-skill", "admin", "roles=[USER, POWER_USER]");
            assertThat(audit.eventType()).isEqualTo("allowlist_changed");
            assertThat(audit.detail()).isEqualTo("roles=[USER, POWER_USER]");
        }
    }

    // ── Log methods (async write) ──────────────────────────��─────────────────

    @Nested
    class LogMethods {

        @Test
        void logCreated_savesAuditRecord() {
            service.logCreated("s1", "code-review", "admin");
            var captor = ArgumentCaptor.forClass(SkillUsageAudit.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("created");
            assertThat(captor.getValue().skillId()).isEqualTo("s1");
        }

        @Test
        void logUpdated_savesWithDetail() {
            service.logUpdated("s2", "summarizer", "admin", "name changed");
            var captor = ArgumentCaptor.forClass(SkillUsageAudit.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("updated");
            assertThat(captor.getValue().detail()).isEqualTo("name changed");
        }

        @Test
        void logDeleted_savesAuditRecord() {
            service.logDeleted("s3", "old-skill", "admin");
            var captor = ArgumentCaptor.forClass(SkillUsageAudit.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("deleted");
        }

        @Test
        void logEnabled_savesAuditRecord() {
            service.logEnabled("s4", "my-skill", "admin");
            var captor = ArgumentCaptor.forClass(SkillUsageAudit.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("enabled");
        }

        @Test
        void logDisabled_savesAuditRecord() {
            service.logDisabled("s5", "my-skill", "admin");
            var captor = ArgumentCaptor.forClass(SkillUsageAudit.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().eventType()).isEqualTo("disabled");
        }

        @Test
        void logVisibilityChanged_savesWithFormattedDetail() {
            service.logVisibilityChanged("s6", "skill", "admin", "PUBLIC", "RESTRICTED");
            var captor = ArgumentCaptor.forClass(SkillUsageAudit.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().detail()).isEqualTo("PUBLIC -> RESTRICTED");
        }

        @Test
        void logAllowlistChanged_savesWithDetail() {
            service.logAllowlistChanged("s7", "skill", "admin", "roles=[USER]");
            var captor = ArgumentCaptor.forClass(SkillUsageAudit.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().detail()).isEqualTo("roles=[USER]");
        }

        @Test
        void logCreated_swallowsException() {
            doThrow(new RuntimeException("DB down")).when(repository).save(any());
            // should not throw
            service.logCreated("s1", "code-review", "admin");
        }
    }

    // ── Query methods ────────────────────────────────────────────────────────

    @Nested
    class QueryMethods {

        @Test
        void findBySkillId_delegatesToRepository() {
            var audit = new SkillUsageAudit(1L, "s1", "skill", "created", "admin", null, Instant.now());
            when(repository.findBySkillIdOrderByCreatedAtDesc("s1")).thenReturn(List.of(audit));

            var result = service.findBySkillId("s1");
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().skillId()).isEqualTo("s1");
        }

        @Test
        void findByUsername_delegatesToRepository() {
            var audit = new SkillUsageAudit(2L, "s2", "skill", "deleted", "user1", null, Instant.now());
            when(repository.findByUsernameOrderByCreatedAtDesc("user1")).thenReturn(List.of(audit));

            var result = service.findByUsername("user1");
            assertThat(result).hasSize(1);
        }

        @Test
        void findByEventType_delegatesToRepository() {
            when(repository.findByEventTypeOrderByCreatedAtDesc("created")).thenReturn(List.of());
            assertThat(service.findByEventType("created")).isEmpty();
        }

        @Test
        void findByDateRange_delegatesToRepository() {
            var from = Instant.now().minusSeconds(3600);
            var to = Instant.now();
            when(repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to))
                    .thenReturn(List.of());
            assertThat(service.findByDateRange(from, to)).isEmpty();
        }

        @Test
        void findAll_delegatesToRepository() {
            when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
            assertThat(service.findAll()).isEmpty();
        }
    }
}
