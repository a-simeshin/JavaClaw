package ai.javaclaw.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.javaclaw.persistence.id.SkillIdGeneratorCallback;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJdbcTest
@Testcontainers
@ActiveProfiles("test")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(SkillIdGeneratorCallback.class)
class SkillRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    SkillRepository repository;

    // ── save / findById roundtrip ─────────────────────────────────────────────

    @Test
    void save_findById_roundtrip() {
        final Skill skill = Skill.newGlobal("code-reviewer", "Reviews code quality", true);

        final Skill saved = repository.save(skill);

        assertThat(saved.id()).isNotNull();
        final Optional<Skill> found = repository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("code-reviewer");
        assertThat(found.get().description()).isEqualTo("Reviews code quality");
        assertThat(found.get().enabled()).isTrue();
        assertThat(found.get().ownerId()).isNull();
        assertThat(found.get().content()).isNull();
        assertThat(found.get().createdAt()).isNotNull();
        assertThat(found.get().updatedAt()).isNotNull();
    }

    // ── findAllByOwnerIdIsNull ────────────────────────────────────────────────

    @Test
    void findAllByOwnerIdIsNull_returnsOnlyGlobal() {
        repository.save(Skill.newGlobal("skill-a", "First", true));
        repository.save(Skill.newGlobal("skill-b", "Second", false));

        final List<Skill> globals = repository.findAllByOwnerIdIsNull();

        assertThat(globals).hasSizeGreaterThanOrEqualTo(2);
        assertThat(globals).allMatch(s -> s.ownerId() == null);
        assertThat(globals).extracting(Skill::name).contains("skill-a", "skill-b");
    }

    // ── existsByOwnerIdIsNullAndName ──────────────────────────────────────────

    @Test
    void existsByOwnerIdIsNullAndName_trueAndFalse() {
        repository.save(Skill.newGlobal("exists-check", "Desc", false));

        assertThat(repository.existsByOwnerIdIsNullAndName("exists-check")).isTrue();
        assertThat(repository.existsByOwnerIdIsNullAndName("phantom-skill")).isFalse();
    }

    // ── partial unique index: global name enforced ────────────────────────────

    @Test
    void partialUniqueIndex_globalName_enforced() {
        repository.save(Skill.newGlobal("unique-name", "First", true));

        assertThatThrownBy(() -> repository.save(Skill.newGlobal("unique-name", "Second", false)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ── deleteById ───────────────────────────────────────────────────────────

    @Test
    void deleteById_removes() {
        final Skill saved = repository.save(Skill.newGlobal("to-remove", "Will be deleted", false));
        assertThat(repository.findById(saved.id())).isPresent();

        repository.deleteById(saved.id());

        assertThat(repository.findById(saved.id())).isEmpty();
    }
}
