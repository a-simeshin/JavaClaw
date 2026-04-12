package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.skills.SkillRoleAllowlist;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SkillRoleAllowlistIdGeneratorCallbackTest {

    private final SkillRoleAllowlistIdGeneratorCallback callback = new SkillRoleAllowlistIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final SkillRoleAllowlist input = SkillRoleAllowlist.create("skill-1", "ADMIN");

        final SkillRoleAllowlist result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.skillId()).isEqualTo("skill-1");
        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(result.createdAt()).isEqualTo(input.createdAt());
    }

    @Test
    void keeps_existing_id() {
        final SkillRoleAllowlist input = new SkillRoleAllowlist("fixed-id", "skill-2", "USER", Instant.EPOCH);

        final SkillRoleAllowlist result = callback.onBeforeConvert(input);

        assertThat(result).isSameAs(input);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
