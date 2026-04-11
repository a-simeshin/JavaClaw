package ai.javaclaw.api.admin.tools;

import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Agent tool for managing skills stored in the database. Skills are reusable prompts / personas
 * that the agent can load and apply during conversations.
 */
public class SkillsTool {

    private static final Logger logger = LoggerFactory.getLogger(SkillsTool.class);

    private final SkillRepository skillRepository;

    public SkillsTool(final SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Tool(
            description =
                    """
            Lists all active (enabled) global skills available to the agent.
            Use this to discover which skills are currently loaded and can be applied.

            Returns a formatted list with each skill's name, description and enabled status.
            Returns an empty list message if no skills are defined.
            """)
    public String listSkills() {
        try {
            final List<Skill> skills = skillRepository.findAllByOwnerIdIsNull();
            if (skills.isEmpty()) {
                return "No skills defined.";
            }
            final StringBuilder sb = new StringBuilder();
            sb.append("Skills (").append(skills.size()).append("):").append(System.lineSeparator());
            for (final Skill skill : skills) {
                sb.append("- name: ").append(skill.name()).append(System.lineSeparator());
                sb.append("  description: ")
                        .append(skill.description() != null ? skill.description() : "(none)")
                        .append(System.lineSeparator());
                sb.append("  enabled: ").append(skill.enabled()).append(System.lineSeparator());
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("listSkills failed", e);
            return "Error: Could not list skills. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Creates a new global skill and saves it to the database.
            Use this when the user asks you to add, create or register a new skill.

            - name: Short, unique identifier for the skill (e.g. 'code-reviewer', 'summarizer').
            - description: Human-readable explanation of what the skill does (max 500 chars).
            - content: The skill prompt / instruction content that the agent will use.

            Returns a confirmation message with the created skill's name, or an error if creation failed.
            """)
    public String addSkill(final String name, final String description, final String content) {
        try {
            final Skill skill = new Skill(
                    null,
                    null,
                    name,
                    description,
                    content,
                    true,
                    Skill.VISIBILITY_PUBLIC,
                    Instant.now(),
                    Instant.now());
            final Skill saved = skillRepository.save(skill);
            return String.format("Skill '%s' created successfully (id=%s).", saved.name(), saved.id());
        } catch (Exception e) {
            logger.error("addSkill failed for name={}", name, e);
            return "Error: Could not create skill. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Removes a global skill from the database by name.
            Use this when the user asks to delete or unregister a skill.

            - name: The exact name of the skill to remove (e.g. 'code-reviewer').

            Returns a confirmation message, or an error if no skill with that name was found.
            """)
    public String removeSkill(final String name) {
        try {
            final List<Skill> skills = skillRepository.findAllByOwnerIdIsNull();
            final Optional<Skill> found =
                    skills.stream().filter(s -> name.equals(s.name())).findFirst();
            if (found.isEmpty()) {
                return String.format("Error: Skill '%s' not found.", name);
            }
            skillRepository.deleteById(found.get().id());
            return String.format("Skill '%s' removed successfully.", name);
        } catch (Exception e) {
            logger.error("removeSkill failed for name={}", name, e);
            return "Error: Could not remove skill. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Enables a skill by name so it is active and can be applied by the agent.
            Use this when the user asks to activate or turn on a specific skill.

            - name: The exact name of the skill to enable.

            Returns a confirmation message, or an error if the skill was not found.
            """)
    public String enableSkill(final String name) {
        return setEnabled(name, true);
    }

    @Tool(
            description =
                    """
            Disables a skill by name so it is inactive and will not be applied by the agent.
            Use this when the user asks to deactivate or turn off a specific skill.

            - name: The exact name of the skill to disable.

            Returns a confirmation message, or an error if the skill was not found.
            """)
    public String disableSkill(final String name) {
        return setEnabled(name, false);
    }

    private String setEnabled(final String name, final boolean enabled) {
        try {
            final List<Skill> skills = skillRepository.findAllByOwnerIdIsNull();
            final Optional<Skill> found =
                    skills.stream().filter(s -> name.equals(s.name())).findFirst();
            if (found.isEmpty()) {
                return String.format("Error: Skill '%s' not found.", name);
            }
            final Skill updated = found.get().withPatch(null, null, enabled);
            skillRepository.save(updated);
            final String state = enabled ? "enabled" : "disabled";
            return String.format("Skill '%s' %s successfully.", name, state);
        } catch (Exception e) {
            logger.error("setEnabled(name={}, enabled={}) failed", name, enabled, e);
            return "Error: Could not update skill. " + e.getMessage();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private SkillRepository skillRepository;

        public Builder skillRepository(final SkillRepository skillRepository) {
            this.skillRepository = skillRepository;
            return this;
        }

        public SkillsTool build() {
            return new SkillsTool(this.skillRepository);
        }
    }
}
