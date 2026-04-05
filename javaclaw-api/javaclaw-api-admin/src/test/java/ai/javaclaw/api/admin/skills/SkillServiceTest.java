package ai.javaclaw.api.admin.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository repository;

    @InjectMocks
    private SkillService service;

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_returnsAllGlobalSkills_mappedToDto() {
        final Skill s1 = skill("id-1", "code-reviewer", "Reviews code", true);
        final Skill s2 = skill("id-2", "summarizer", "Summarizes docs", false);
        when(repository.findAllByOwnerIdIsNull()).thenReturn(List.of(s1, s2));

        final List<SkillDto> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SkillDto::id).containsExactly("id-1", "id-2");
        assertThat(result).extracting(SkillDto::name).containsExactly("code-reviewer", "summarizer");
        assertThat(result).extracting(SkillDto::enabled).containsExactly(true, false);
    }

    @Test
    void list_empty_returnsEmptyList() {
        when(repository.findAllByOwnerIdIsNull()).thenReturn(List.of());

        final List<SkillDto> result = service.list();

        assertThat(result).isEmpty();
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void create_savesWithNullOwnerId_returnsDto() {
        final SkillDto draft = new SkillDto(null, "my-skill", "A description", true);
        final Skill saved = skill("generated-id", "my-skill", "A description", true);
        when(repository.save(any(Skill.class))).thenReturn(saved);

        final SkillDto result = service.create(draft);

        assertThat(result.id()).isEqualTo("generated-id");
        assertThat(result.name()).isEqualTo("my-skill");
        assertThat(result.description()).isEqualTo("A description");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void create_generatesId() {
        final SkillDto draft = new SkillDto(null, "gen-skill", null, false);
        final Skill saved = skill("some-uuid", "gen-skill", null, false);
        when(repository.save(any(Skill.class))).thenReturn(saved);

        final SkillDto result = service.create(draft);

        verify(repository).save(any(Skill.class));
        assertThat(result.id()).isNotNull();
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_mergesNullFields_keepsOriginal() {
        final Skill original = skill("id-x", "original-name", "original-desc", true);
        when(repository.findByIdAndOwnerIdIsNull("id-x")).thenReturn(Optional.of(original));
        final Skill patched = skill("id-x", "original-name", "original-desc", false);
        when(repository.save(any(Skill.class))).thenReturn(patched);

        final SkillDto result = service.update("id-x", new SkillDto(null, null, null, false));

        assertThat(result.name()).isEqualTo("original-name");
        assertThat(result.description()).isEqualTo("original-desc");
    }

    @Test
    void update_replacesNonNullFields() {
        final Skill original = skill("id-y", "old-name", "old-desc", false);
        when(repository.findByIdAndOwnerIdIsNull("id-y")).thenReturn(Optional.of(original));
        final Skill patched = skill("id-y", "new-name", "new-desc", true);
        when(repository.save(any(Skill.class))).thenReturn(patched);

        final SkillDto result = service.update("id-y", new SkillDto(null, "new-name", "new-desc", true));

        assertThat(result.name()).isEqualTo("new-name");
        assertThat(result.description()).isEqualTo("new-desc");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void update_missingSkill_throwsNoSuchElementException() {
        when(repository.findByIdAndOwnerIdIsNull("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing-id", new SkillDto(null, "x", null, true)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("missing-id");
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_existingSkill_deletesById() {
        final Skill existing = skill("id-del", "to-delete", null, true);
        when(repository.findByIdAndOwnerIdIsNull("id-del")).thenReturn(Optional.of(existing));

        service.delete("id-del");

        verify(repository).deleteById("id-del");
    }

    @Test
    void delete_missingSkill_throwsNoSuchElementException() {
        when(repository.findByIdAndOwnerIdIsNull("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("gone"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("gone");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Skill skill(final String id, final String name, final String description, final boolean enabled) {
        return new Skill(id, null, name, description, null, enabled, Instant.now(), Instant.now());
    }
}
