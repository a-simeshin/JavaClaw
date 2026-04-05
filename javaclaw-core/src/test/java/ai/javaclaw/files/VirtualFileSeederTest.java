package ai.javaclaw.files;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class VirtualFileSeederTest {

    @Mock
    VirtualFileRepository repository;

    @TempDir
    Path tempDir;

    private static final ApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    // ── seed_emptyDb_loadsWorkspaceFiles ──────────────────────────────────────

    @Test
    void seed_emptyDb_loadsWorkspaceFiles() throws Exception {
        // Create some files in temp workspace
        Files.writeString(tempDir.resolve("AGENT.md"), "# Agent instructions");
        Files.writeString(tempDir.resolve("notes.txt"), "some notes");
        Path subDir = Files.createDirectory(tempDir.resolve("docs"));
        Files.writeString(subDir.resolve("guide.md"), "# Guide");

        when(repository.countByOwnerIdIsNull()).thenReturn(0L);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VirtualFileSeeder seeder = seederFor(new FileSystemResource(tempDir.toFile()));
        seeder.run(NO_ARGS);

        // 3 files total: AGENT.md, notes.txt, docs/guide.md
        verify(repository, times(3)).save(any(VirtualFile.class));
    }

    @Test
    void seed_emptyDb_savesCorrectContentType() throws Exception {
        Files.writeString(tempDir.resolve("config.yaml"), "key: value");

        when(repository.countByOwnerIdIsNull()).thenReturn(0L);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VirtualFileSeeder seeder = seederFor(new FileSystemResource(tempDir.toFile()));
        seeder.run(NO_ARGS);

        verify(repository)
                .save(org.mockito.ArgumentMatchers.argThat(
                        f -> "text/yaml".equals(f.contentType()) && "config.yaml".equals(f.path())));
    }

    // ── seed_alreadySeeded_skips ──────────────────────────────────────────────

    @Test
    void seed_alreadySeeded_skips() throws Exception {
        when(repository.countByOwnerIdIsNull()).thenReturn(5L);

        VirtualFileSeeder seeder = seederFor(new FileSystemResource(tempDir.toFile()));
        seeder.run(NO_ARGS);

        verify(repository, never()).save(any());
    }

    // ── seed_missingWorkspace_noException ─────────────────────────────────────

    @Test
    void seed_missingWorkspace_noException() throws Exception {
        Path nonExistent = tempDir.resolve("does-not-exist");
        when(repository.countByOwnerIdIsNull()).thenReturn(0L);

        VirtualFileSeeder seeder = seederFor(new FileSystemResource(nonExistent.toFile()));

        // Must not throw
        seeder.run(NO_ARGS);

        verify(repository, never()).save(any());
    }

    @Test
    void seed_workspaceIsFile_notDirectory_noException() throws Exception {
        Path file = Files.writeString(tempDir.resolve("not-a-dir.txt"), "content");
        when(repository.countByOwnerIdIsNull()).thenReturn(0L);

        VirtualFileSeeder seeder = seederFor(new FileSystemResource(file.toFile()));

        // Must not throw even if workspace path points to a file
        seeder.run(NO_ARGS);

        verify(repository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private VirtualFileSeeder seederFor(Resource workspaceDir) {
        return new VirtualFileSeeder(repository, workspaceDir);
    }
}
