package ai.javaclaw.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.javaclaw.persistence.id.VirtualFileIdGeneratorCallback;
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
@Import(VirtualFileIdGeneratorCallback.class)
class VirtualFileRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    VirtualFileRepository repository;

    // ── save / findById roundtrip ─────────────────────────────────────────────

    @Test
    void save_findById_roundtrip() {
        VirtualFile file = VirtualFile.newGlobalFile("docs/readme.md", "# Hello", "text/markdown");

        VirtualFile saved = repository.save(file);

        assertThat(saved.id()).isNotNull();
        Optional<VirtualFile> found = repository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().path()).isEqualTo("docs/readme.md");
        assertThat(found.get().content()).isEqualTo("# Hello");
        assertThat(found.get().contentType()).isEqualTo("text/markdown");
        assertThat(found.get().ownerId()).isNull();
        assertThat(found.get().sizeBytes()).isEqualTo("# Hello".getBytes().length);
    }

    // ── findByOwnerIdIsNullAndPath ────────────────────────────────────────────

    @Test
    void findByOwnerIdIsNullAndPath_findsGlobalFile() {
        repository.save(VirtualFile.newGlobalFile("config/settings.yaml", "key: val", "text/yaml"));

        Optional<VirtualFile> result = repository.findByOwnerIdIsNullAndPath("config/settings.yaml");

        assertThat(result).isPresent();
        assertThat(result.get().path()).isEqualTo("config/settings.yaml");
        assertThat(result.get().content()).isEqualTo("key: val");
    }

    @Test
    void findByOwnerIdIsNullAndPath_missingPath_returnsEmpty() {
        Optional<VirtualFile> result = repository.findByOwnerIdIsNullAndPath("nonexistent/file.txt");

        assertThat(result).isEmpty();
    }

    // ── findAllByOwnerIdIsNull ────────────────────────────────────────────────

    @Test
    void findAllByOwnerIdIsNull_returnsOnlyGlobal() {
        repository.save(VirtualFile.newGlobalFile("global-a.txt", "A", "text/plain"));
        repository.save(VirtualFile.newGlobalFile("global-b.txt", "B", "text/plain"));

        List<VirtualFile> globals = repository.findAllByOwnerIdIsNull();

        assertThat(globals).hasSizeGreaterThanOrEqualTo(2);
        assertThat(globals).allMatch(f -> f.ownerId() == null);
        assertThat(globals).extracting(VirtualFile::path).contains("global-a.txt", "global-b.txt");
    }

    // ── partial unique index on global path ───────────────────────────────────

    @Test
    void partialUniqueIndex_enforcedOnGlobalPath() {
        repository.save(VirtualFile.newGlobalFile("unique-path.txt", "first", "text/plain"));

        assertThatThrownBy(() -> repository.save(VirtualFile.newGlobalFile("unique-path.txt", "second", "text/plain")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ── deleteByOwnerIdIsNullAndPath ──────────────────────────────────────────

    @Test
    void deleteByOwnerIdIsNullAndPath_removes() {
        repository.save(VirtualFile.newGlobalFile("to-delete.txt", "bye", "text/plain"));
        assertThat(repository.findByOwnerIdIsNullAndPath("to-delete.txt")).isPresent();

        repository.deleteByOwnerIdIsNullAndPath("to-delete.txt");

        assertThat(repository.findByOwnerIdIsNullAndPath("to-delete.txt")).isEmpty();
    }

    // ── existsByOwnerIdIsNullAndPath ──────────────────────────────────────────

    @Test
    void existsByOwnerIdIsNullAndPath_trueWhenPresent() {
        repository.save(VirtualFile.newGlobalFile("exists-check.txt", "yes", "text/plain"));

        assertThat(repository.existsByOwnerIdIsNullAndPath("exists-check.txt")).isTrue();
    }

    @Test
    void existsByOwnerIdIsNullAndPath_falseWhenAbsent() {
        assertThat(repository.existsByOwnerIdIsNullAndPath("phantom.txt")).isFalse();
    }

    // ── withUpdatedContent ────────────────────────────────────────────────────

    @Test
    void withUpdatedContent_savesNewContentAndPreservesId() {
        VirtualFile original = repository.save(VirtualFile.newGlobalFile("update-me.txt", "v1", "text/plain"));

        VirtualFile updated = repository.save(original.withUpdatedContent("v2"));

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.content()).isEqualTo("v2");
        assertThat(updated.sizeBytes()).isEqualTo(2L);

        Optional<VirtualFile> fromDb = repository.findByOwnerIdIsNullAndPath("update-me.txt");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().content()).isEqualTo("v2");
    }
}
