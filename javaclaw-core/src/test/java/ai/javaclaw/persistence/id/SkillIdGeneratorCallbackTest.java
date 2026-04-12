package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.skills.Skill;
import org.junit.jupiter.api.Test;

class SkillIdGeneratorCallbackTest {

    private final SkillIdGeneratorCallback callback = new SkillIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final Skill input = Skill.newGlobal("code-review", "Reviews code", true);

        final Skill result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("code-review");
        assertThat(result.description()).isEqualTo("Reviews code");
        assertThat(result.enabled()).isTrue();
        assertThat(result.visibility()).isEqualTo(Skill.VISIBILITY_PUBLIC);
    }

    @Test
    void keeps_existing_id() {
        final Skill base = Skill.newGlobal("x", null, true);
        final Skill withId = new Skill(
                "fixed-id",
                base.ownerId(),
                base.name(),
                base.description(),
                base.content(),
                base.enabled(),
                base.visibility(),
                base.createdAt(),
                base.updatedAt());

        final Skill result = callback.onBeforeConvert(withId);

        assertThat(result).isSameAs(withId);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
