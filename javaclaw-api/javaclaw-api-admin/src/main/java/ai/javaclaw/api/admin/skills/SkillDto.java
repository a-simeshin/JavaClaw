package ai.javaclaw.api.admin.skills;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mutable projection of a skill. {@code id} is generated server-side on create. */
public record SkillDto(
        String id, @NotBlank @Size(max = 120) String name, @Size(max = 500) String description, boolean enabled) {}
