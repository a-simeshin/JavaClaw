package ai.javaclaw.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillVisibilityServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillRoleAllowlistRepository allowlistRepository;

    @InjectMocks
    private SkillVisibilityService service;

    // ── listVisibleSkills ────────────────────────────────────────────────────

    @Test
    void listVisibleSkills_adminSeesAll() {
        var pub = publicSkill("s1", "public-skill");
        var restricted = restrictedSkill("s2", "restricted-skill");
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(pub, restricted));

        var result = service.listVisibleSkills("ADMIN");

        assertThat(result).hasSize(2);
    }

    @Test
    void listVisibleSkills_userSeesPublicOnly() {
        var pub = publicSkill("s1", "public-skill");
        var restricted = restrictedSkill("s2", "restricted-skill");
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(pub, restricted));
        when(allowlistRepository.existsBySkillIdAndRole("s2", "USER")).thenReturn(false);

        var result = service.listVisibleSkills("USER");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("public-skill");
    }

    @Test
    void listVisibleSkills_userSeesAllowlistedRestricted() {
        var pub = publicSkill("s1", "public-skill");
        var restricted = restrictedSkill("s2", "restricted-skill");
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(pub, restricted));
        when(allowlistRepository.existsBySkillIdAndRole("s2", "USER")).thenReturn(true);

        var result = service.listVisibleSkills("USER");

        assertThat(result).hasSize(2);
    }

    @Test
    void listVisibleSkills_adminCaseInsensitive() {
        var restricted = restrictedSkill("s1", "restricted");
        when(skillRepository.findAllByOwnerIdIsNull()).thenReturn(List.of(restricted));

        var result = service.listVisibleSkills("admin");

        assertThat(result).hasSize(1);
    }

    // ── listVisibleEnabledSkills ─────────────────────────────────────────────

    @Test
    void listVisibleEnabledSkills_adminGetsAll() {
        var enabled = publicSkill("s1", "enabled");
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue()).thenReturn(List.of(enabled));

        var result = service.listVisibleEnabledSkills("ADMIN");

        assertThat(result).hasSize(1);
    }

    @Test
    void listVisibleEnabledSkills_userFiltered() {
        var pub = publicSkill("s1", "public");
        var restricted = restrictedSkill("s2", "restricted");
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue()).thenReturn(List.of(pub, restricted));
        when(allowlistRepository.existsBySkillIdAndRole("s2", "USER")).thenReturn(false);

        var result = service.listVisibleEnabledSkills("USER");

        assertThat(result).hasSize(1);
    }

    // ── setVisibility ────────────────────────────────────────────────────────

    @Test
    void setVisibility_success() {
        var skill = publicSkill("s1", "test");
        when(skillRepository.findByIdAndOwnerIdIsNull("s1")).thenReturn(Optional.of(skill));
        when(skillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.setVisibility("s1", Skill.VISIBILITY_RESTRICTED);

        assertThat(result.visibility()).isEqualTo(Skill.VISIBILITY_RESTRICTED);
    }

    @Test
    void setVisibility_invalidVisibility() {
        var skill = publicSkill("s1", "test");
        when(skillRepository.findByIdAndOwnerIdIsNull("s1")).thenReturn(Optional.of(skill));

        assertThatThrownBy(() -> service.setVisibility("s1", "INVALID")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setVisibility_skillNotFound() {
        when(skillRepository.findByIdAndOwnerIdIsNull("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setVisibility("x", Skill.VISIBILITY_PUBLIC))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── getAllowedRoles ──────────────────────────────────────────────────────

    @Test
    void getAllowedRoles_returnsSet() {
        when(allowlistRepository.findBySkillId("s1"))
                .thenReturn(List.of(
                        SkillRoleAllowlist.create("s1", "USER"), SkillRoleAllowlist.create("s1", "POWER_USER")));

        var result = service.getAllowedRoles("s1");

        assertThat(result).containsExactlyInAnyOrder("USER", "POWER_USER");
    }

    @Test
    void getAllowedRoles_emptyWhenNone() {
        when(allowlistRepository.findBySkillId("s1")).thenReturn(List.of());

        assertThat(service.getAllowedRoles("s1")).isEmpty();
    }

    // ── setAllowedRoles ─────────────────────────────────────────────────────

    @Test
    void setAllowedRoles_replacesExisting() {
        when(skillRepository.existsById("s1")).thenReturn(true);

        service.setAllowedRoles("s1", Set.of("USER", "POWER_USER"));

        verify(allowlistRepository).deleteAllBySkillId("s1");
        verify(allowlistRepository, times(2)).save(any());
    }

    @Test
    void setAllowedRoles_skillNotFound() {
        when(skillRepository.existsById("x")).thenReturn(false);

        assertThatThrownBy(() -> service.setAllowedRoles("x", Set.of("USER")))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── addAllowedRole ──────────────────────────────────────────────────────

    @Test
    void addAllowedRole_addsNew() {
        when(skillRepository.existsById("s1")).thenReturn(true);
        when(allowlistRepository.existsBySkillIdAndRole("s1", "USER")).thenReturn(false);

        service.addAllowedRole("s1", "USER");

        verify(allowlistRepository).save(any());
    }

    @Test
    void addAllowedRole_skipsDuplicate() {
        when(skillRepository.existsById("s1")).thenReturn(true);
        when(allowlistRepository.existsBySkillIdAndRole("s1", "USER")).thenReturn(true);

        service.addAllowedRole("s1", "USER");

        verify(allowlistRepository, never()).save(any());
    }

    @Test
    void addAllowedRole_skillNotFound() {
        when(skillRepository.existsById("x")).thenReturn(false);

        assertThatThrownBy(() -> service.addAllowedRole("x", "USER")).isInstanceOf(NoSuchElementException.class);
    }

    // ── removeAllowedRole ───────────────────────────────────────────────────

    @Test
    void removeAllowedRole_delegates() {
        service.removeAllowedRole("s1", "USER");

        verify(allowlistRepository).deleteBySkillIdAndRole("s1", "USER");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Skill publicSkill(String id, String name) {
        return new Skill(id, null, name, "desc", null, true, Skill.VISIBILITY_PUBLIC, Instant.now(), Instant.now());
    }

    private static Skill restrictedSkill(String id, String name) {
        return new Skill(id, null, name, "desc", null, true, Skill.VISIBILITY_RESTRICTED, Instant.now(), Instant.now());
    }
}
