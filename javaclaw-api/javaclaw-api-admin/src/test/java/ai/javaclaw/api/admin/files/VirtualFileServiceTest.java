package ai.javaclaw.api.admin.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.files.VirtualFile;
import ai.javaclaw.files.VirtualFileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualFileServiceTest {

    @Mock
    VirtualFileRepository repository;

    VirtualFileService service;

    @BeforeEach
    void setUp() {
        service = new VirtualFileService(repository);
    }

    // ── read ──────────────────────────────────────────────────────────────────

    @Test
    void read_existingFile_returnsContent() {
        VirtualFile file = makeFile("docs/readme.md", "# Hello", "text/markdown");
        when(repository.findByOwnerIdIsNullAndPath("docs/readme.md")).thenReturn(Optional.of(file));

        FileContentDto result = service.read("docs/readme.md");

        assertThat(result.path()).isEqualTo("docs/readme.md");
        assertThat(result.content()).isEqualTo("# Hello");
    }

    @Test
    void read_missingFile_throwsIllegalArgument() {
        when(repository.findByOwnerIdIsNullAndPath("missing.txt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read("missing.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void read_blankPath_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.read("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void read_dotdotPath_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.read("../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("..");
    }

    @Test
    void read_absolutePath_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.read("/absolute/path.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    // ── write ─────────────────────────────────────────────────────────────────

    @Test
    void write_newFile_insertsAndReturnsDto() {
        when(repository.findByOwnerIdIsNullAndPath("notes/new.txt")).thenReturn(Optional.empty());
        VirtualFile saved = makeFile("notes/new.txt", "hello", "text/plain");
        when(repository.save(any())).thenReturn(saved);

        FileContentDto result = service.write("notes/new.txt", "hello");

        assertThat(result.path()).isEqualTo("notes/new.txt");
        assertThat(result.content()).isEqualTo("hello");
        verify(repository).save(any());
    }

    @Test
    void write_existingFile_updatesContent() {
        VirtualFile existing = makeFile("notes/existing.txt", "old content", "text/plain");
        when(repository.findByOwnerIdIsNullAndPath("notes/existing.txt")).thenReturn(Optional.of(existing));
        VirtualFile updated = makeFile("notes/existing.txt", "new content", "text/plain");
        when(repository.save(any())).thenReturn(updated);

        FileContentDto result = service.write("notes/existing.txt", "new content");

        assertThat(result.content()).isEqualTo("new content");
        verify(repository).save(any());
    }

    @Test
    void write_mdExtension_infersMarkdownContentType() {
        when(repository.findByOwnerIdIsNullAndPath("README.md")).thenReturn(Optional.empty());
        VirtualFile saved = makeFile("README.md", "# Doc", "text/markdown");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.write("README.md", "# Doc");

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(f -> "text/markdown".equals(f.contentType())));
    }

    @Test
    void write_jsonExtension_infersJsonContentType() {
        when(repository.findByOwnerIdIsNullAndPath("config.json")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.write("config.json", "{}");

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(f -> "application/json".equals(f.contentType())));
    }

    @Test
    void write_yamlExtension_infersYamlContentType() {
        when(repository.findByOwnerIdIsNullAndPath("app.yaml")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.write("app.yaml", "key: value");

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(f -> "text/yaml".equals(f.contentType())));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_newFile_savesAndReturnsDto() {
        when(repository.existsByOwnerIdIsNullAndPath("fresh.txt")).thenReturn(false);
        VirtualFile saved = makeFile("fresh.txt", "brand new", "text/plain");
        when(repository.save(any())).thenReturn(saved);

        FileContentDto result = service.create("fresh.txt", "brand new");

        assertThat(result.path()).isEqualTo("fresh.txt");
        assertThat(result.content()).isEqualTo("brand new");
    }

    @Test
    void create_duplicatePath_throwsIllegalState() {
        when(repository.existsByOwnerIdIsNullAndPath("duplicate.txt")).thenReturn(true);

        assertThatThrownBy(() -> service.create("duplicate.txt", "data"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingPath_callsRepository() {
        service.delete("to-delete.txt");

        verify(repository).deleteByOwnerIdIsNullAndPath("to-delete.txt");
    }

    @Test
    void delete_missingPath_silentlyDelegates() {
        // deleteByOwnerIdIsNullAndPath is a void method — no exception must be thrown
        service.delete("ghost.txt");

        verify(repository).deleteByOwnerIdIsNullAndPath("ghost.txt");
    }

    // ── tree ──────────────────────────────────────────────────────────────────

    @Test
    void tree_emptyRepository_returnsRootDirWithNoChildren() {
        when(repository.findAllByOwnerIdIsNull()).thenReturn(List.of());

        FileNodeDto root = service.tree();

        assertThat(root.type()).isEqualTo("dir");
        assertThat(root.name()).isEqualTo("workspace");
        assertThat(root.children()).isEmpty();
    }

    @Test
    void tree_flatFiles_allFilesAtRoot() {
        List<VirtualFile> files = List.of(
                makeFile("a.txt", "A", "text/plain"),
                makeFile("b.md", "B", "text/markdown"),
                makeFile("c.json", "C", "application/json"));
        when(repository.findAllByOwnerIdIsNull()).thenReturn(files);

        FileNodeDto root = service.tree();

        assertThat(root.children()).hasSize(3);
        assertThat(root.children()).extracting(FileNodeDto::type).containsOnly("file");
        assertThat(root.children()).extracting(FileNodeDto::name).containsExactlyInAnyOrder("a.txt", "b.md", "c.json");
    }

    @Test
    void tree_nestedDirs_createsIntermediateDirNodes() {
        List<VirtualFile> files = List.of(
                makeFile("docs/guide.md", "guide", "text/markdown"),
                makeFile("docs/api.md", "api", "text/markdown"),
                makeFile("readme.txt", "root", "text/plain"));
        when(repository.findAllByOwnerIdIsNull()).thenReturn(files);

        FileNodeDto root = service.tree();

        // Root should have "docs" dir and "readme.txt" file
        assertThat(root.children()).extracting(FileNodeDto::name).containsExactlyInAnyOrder("docs", "readme.txt");

        FileNodeDto docsDir = root.children().stream()
                .filter(n -> "docs".equals(n.name()))
                .findFirst()
                .orElseThrow();
        assertThat(docsDir.type()).isEqualTo("dir");
        assertThat(docsDir.children()).hasSize(2);
        assertThat(docsDir.children()).extracting(FileNodeDto::name).containsExactlyInAnyOrder("guide.md", "api.md");
    }

    @Test
    void tree_deepNesting_allLevelsCreated() {
        List<VirtualFile> files = List.of(makeFile("a/b/c/deep.txt", "deep content", "text/plain"));
        when(repository.findAllByOwnerIdIsNull()).thenReturn(files);

        FileNodeDto root = service.tree();

        FileNodeDto aDir = root.children().getFirst();
        assertThat(aDir.type()).isEqualTo("dir");
        assertThat(aDir.name()).isEqualTo("a");

        FileNodeDto bDir = aDir.children().getFirst();
        assertThat(bDir.type()).isEqualTo("dir");
        assertThat(bDir.name()).isEqualTo("b");

        FileNodeDto cDir = bDir.children().getFirst();
        assertThat(cDir.type()).isEqualTo("dir");
        assertThat(cDir.name()).isEqualTo("c");

        FileNodeDto leaf = cDir.children().getFirst();
        assertThat(leaf.type()).isEqualTo("file");
        assertThat(leaf.name()).isEqualTo("deep.txt");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static VirtualFile makeFile(String path, String content, String contentType) {
        return new VirtualFile(
                "id-" + path.replace('/', '-'),
                null,
                path,
                content,
                contentType,
                content.getBytes().length,
                java.time.Instant.now(),
                java.time.Instant.now());
    }
}
