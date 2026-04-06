package ai.javaclaw.agent.pipeline;

import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Provides the active (enabled, global) skills as a formatted string for inclusion in the system
 * prompt.
 *
 * <p>Each skill is rendered as a markdown section: {@code ## Skill: <name>\n<content>\n\n}.
 * Skills with blank content are silently skipped. Returns an empty string when no active skills
 * exist.
 */
@Component
public class ActiveSkillsProvider {

    private static final Logger log = LoggerFactory.getLogger(ActiveSkillsProvider.class);

    /** Repository used to fetch globally enabled skills. */
    private final SkillRepository skillRepository;

    /**
     * Creates an {@code ActiveSkillsProvider} with the given skill repository.
     *
     * @param skillRepository repository for skill data, must not be null
     */
    public ActiveSkillsProvider(final SkillRepository skillRepository) {
        Assert.notNull(skillRepository, "skillRepository must not be null");
        this.skillRepository = skillRepository;
    }

    /**
     * Loads all globally enabled skills and formats them as markdown sections.
     *
     * <p>Each skill with non-blank content is rendered as:
     * <pre>
     * ## Skill: &lt;name&gt;
     * &lt;content&gt;
     *
     * </pre>
     * Skills with null or blank content are silently skipped.
     *
     * @return formatted skills string, or empty string if no enabled skills exist
     */
    public String loadActiveSkills() {
        final List<Skill> skills;
        try {
            skills = skillRepository.findAllByOwnerIdIsNullAndEnabledTrue();
        } catch (final Exception e) {
            log.warn("ActiveSkillsProvider: could not load skills — {}", e.getMessage());
            return "";
        }

        if (skills == null || skills.isEmpty()) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (final Skill skill : skills) {
            final String content = skill.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            sb.append("## Skill: ").append(skill.name()).append("\n");
            sb.append(content.strip()).append("\n\n");
        }

        return sb.toString();
    }
}
