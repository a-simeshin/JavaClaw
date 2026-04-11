package ai.javaclaw.api.admin.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillsToolTest {

    @Mock
    private SkillRepository skillRepository;

    private SkillsTool tool;

    @BeforeEach
    void setUp() {
        tool = SkillsTool.builder().skillRepository(skillRepository).build();
    }

    // ── listSkills ────────────────────────────────────────────────────────────

    @Test
    void listSkills_withSkills_returnsFormattedList() {
        final Skill s1 = skill("id-1", "code-reviewer", "Reviews code", true);
        final Skill s2 = skill("id-2", "summarizer", "Summarizes docs", false);
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(s1, s2));

        final String result = tool.listSkills();

        assertThat(result).contains("code-reviewer").contains("summarizer");
        assertThat(result).contains("Reviews code").contains("Summarizes docs");
        assertThat(result).contains("true").contains("false");
    }

    @Test
    void listSkills_empty_returnsNoSkillsMessage() {
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of());

        final String result = tool.listSkills();

        assertThat(result).contains("No skills defined");
    }

    // ── addSkill ──────────────────────────────────────────────────────────────

    @Test
    void addSkill_success_returnsConfirmation() {
        final Skill saved = skill("gen-id", "my-skill", "Does stuff", true);
        when(skillRepository.save(any(Skill.class))).thenReturn(saved);

        final String result = tool.addSkill("my-skill", "Does stuff", "You are a helpful assistant.");

        assertThat(result).contains("my-skill").contains("created successfully");
        verify(skillRepository).save(any(Skill.class));
    }

    @Test
    void addSkill_repositoryThrows_returnsErrorMessage() {
        when(skillRepository.save(any(Skill.class))).thenThrow(new RuntimeException("DB error"));

        final String result = tool.addSkill("bad-skill", "desc", "content");

        assertThat(result).startsWith("Error:").contains("DB error");
    }

    // ── removeSkill ───────────────────────────────────────────────────────────

    @Test
    void removeSkill_found_deletesAndReturnsConfirmation() {
        final Skill existing = skill("id-del", "to-delete", "desc", true);
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(existing));

        final String result = tool.removeSkill("to-delete");

        verify(skillRepository).deleteById("id-del");
        assertThat(result).contains("to-delete").contains("removed successfully");
    }

    @Test
    void removeSkill_notFound_returnsErrorMessage() {
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of());

        final String result = tool.removeSkill("ghost");

        assertThat(result).startsWith("Error:").contains("ghost");
    }

    // ── enableSkill / disableSkill ────────────────────────────────────────────

    @Test
    void enableSkill_found_savesEnabledTrueAndReturnsConfirmation() {
        final Skill existing = skill("id-e", "my-skill", "desc", false);
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(existing));
        when(skillRepository.save(any(Skill.class))).thenReturn(existing);

        final String result = tool.enableSkill("my-skill");

        assertThat(result).contains("my-skill").contains("enabled successfully");
        verify(skillRepository).save(any(Skill.class));
    }

    @Test
    void disableSkill_found_savesEnabledFalseAndReturnsConfirmation() {
        final Skill existing = skill("id-d", "active-skill", "desc", true);
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(existing));
        when(skillRepository.save(any(Skill.class))).thenReturn(existing);

        final String result = tool.disableSkill("active-skill");

        assertThat(result).contains("active-skill").contains("disabled successfully");
        verify(skillRepository).save(any(Skill.class));
    }

    @Test
    void enableSkill_notFound_returnsErrorMessage() {
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of());

        final String result = tool.enableSkill("missing");

        assertThat(result).startsWith("Error:").contains("missing");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Skill skill(final String id, final String name, final String description, final boolean enabled) {
        return new Skill(
                id, null, name, description, null, enabled, Skill.VISIBILITY_PUBLIC, Instant.now(), Instant.now());
    }
}
