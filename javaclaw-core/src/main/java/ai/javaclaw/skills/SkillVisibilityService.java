package ai.javaclaw.skills;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Manages skill visibility and role-based allowlists (15.3.1-15.3.2).
 * <p>PUBLIC skills are visible to all roles.
 * RESTRICTED skills are visible only to allowlisted roles (ADMIN always sees everything).
 */
@Service
public class SkillVisibilityService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final SkillRepository skillRepository;
    private final SkillRoleAllowlistRepository allowlistRepository;

    public SkillVisibilityService(
            final SkillRepository skillRepository, final SkillRoleAllowlistRepository allowlistRepository) {
        this.skillRepository = skillRepository;
        this.allowlistRepository = allowlistRepository;
    }

    /** Returns all global skills visible to the given role. ADMIN sees everything. */
    public List<Skill> listVisibleSkills(final String role) {
        final List<Skill> allGlobal = skillRepository.findAllByOwnerIdIsNull();
        if (ADMIN_ROLE.equalsIgnoreCase(role)) {
            return allGlobal;
        }
        return allGlobal.stream().filter(skill -> isVisibleToRole(skill, role)).toList();
    }

    /** Returns all enabled global skills visible to the given role. */
    public List<Skill> listVisibleEnabledSkills(final String role) {
        if (ADMIN_ROLE.equalsIgnoreCase(role)) {
            return skillRepository.findAllByOwnerIdIsNullAndEnabledTrue();
        }
        return skillRepository.findAllByOwnerIdIsNullAndEnabledTrue().stream()
                .filter(skill -> isVisibleToRole(skill, role))
                .toList();
    }

    /** Sets skill visibility to PUBLIC or RESTRICTED. */
    public Skill setVisibility(final String skillId, final String visibility) {
        final Skill skill = skillRepository
                .findByIdAndOwnerIdIsNull(skillId)
                .orElseThrow(() -> new NoSuchElementException("skill not found: " + skillId));
        if (!Skill.VISIBILITY_PUBLIC.equals(visibility) && !Skill.VISIBILITY_RESTRICTED.equals(visibility)) {
            throw new IllegalArgumentException("visibility must be PUBLIC or RESTRICTED");
        }
        final Skill updated = skill.withVisibility(visibility);
        return skillRepository.save(updated);
    }

    /** Gets the set of roles allowed for a RESTRICTED skill. */
    public Set<String> getAllowedRoles(final String skillId) {
        return allowlistRepository.findBySkillId(skillId).stream()
                .map(SkillRoleAllowlist::role)
                .collect(Collectors.toSet());
    }

    /** Sets the complete allowlist for a skill (replaces existing). */
    public void setAllowedRoles(final String skillId, final Set<String> roles) {
        if (!skillRepository.existsById(skillId)) {
            throw new NoSuchElementException("skill not found: " + skillId);
        }
        allowlistRepository.deleteAllBySkillId(skillId);
        roles.forEach(role -> allowlistRepository.save(SkillRoleAllowlist.create(skillId, role)));
    }

    /** Adds a single role to the skill's allowlist. */
    public void addAllowedRole(final String skillId, final String role) {
        if (!skillRepository.existsById(skillId)) {
            throw new NoSuchElementException("skill not found: " + skillId);
        }
        if (!allowlistRepository.existsBySkillIdAndRole(skillId, role)) {
            allowlistRepository.save(SkillRoleAllowlist.create(skillId, role));
        }
    }

    /** Removes a single role from the skill's allowlist. */
    public void removeAllowedRole(final String skillId, final String role) {
        allowlistRepository.deleteBySkillIdAndRole(skillId, role);
    }

    private boolean isVisibleToRole(final Skill skill, final String role) {
        if (skill.isPublic()) {
            return true;
        }
        return allowlistRepository.existsBySkillIdAndRole(skill.id(), role);
    }
}
