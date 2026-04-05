package ai.javaclaw.api.admin.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.api.admin.files.FileContentDto;
import ai.javaclaw.api.admin.files.FileNodeDto;
import ai.javaclaw.api.admin.files.VirtualFileService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileOperationsToolTest {

    @Mock
    private VirtualFileService fileService;

    private FileOperationsTool tool;

    @BeforeEach
    void setUp() {
        tool = FileOperationsTool.builder().fileService(fileService).build();
    }

    // ── readFile ──────────────────────────────────────────────────────────────

    @Test
    void readFile_found_returnsContent() {
        when(fileService.read("notes/todo.md")).thenReturn(new FileContentDto("notes/todo.md", "# TODO\n- item1"));

        final String result = tool.readFile("notes/todo.md");

        assertThat(result).isEqualTo("# TODO\n- item1");
    }

    @Test
    void readFile_notFound_returnsErrorMessage() {
        when(fileService.read("missing.txt")).thenThrow(new IllegalArgumentException("File not found: missing.txt"));

        final String result = tool.readFile("missing.txt");

        assertThat(result).startsWith("Error:").contains("missing.txt");
    }

    // ── writeFile ─────────────────────────────────────────────────────────────

    @Test
    void writeFile_success_returnsConfirmation() {
        when(fileService.write("docs/readme.md", "Hello world"))
                .thenReturn(new FileContentDto("docs/readme.md", "Hello world"));

        final String result = tool.writeFile("docs/readme.md", "Hello world");

        assertThat(result).contains("docs/readme.md").contains("written successfully");
        verify(fileService).write("docs/readme.md", "Hello world");
    }

    @Test
    void writeFile_invalidPath_returnsErrorMessage() {
        when(fileService.write(eq("../etc/passwd"), any()))
                .thenThrow(new IllegalArgumentException("path must not contain '..'"));

        final String result = tool.writeFile("../etc/passwd", "bad");

        assertThat(result).startsWith("Error:").contains("..");
    }

    // ── createFile ────────────────────────────────────────────────────────────

    @Test
    void createFile_success_returnsConfirmation() {
        when(fileService.create("new.txt", "content")).thenReturn(new FileContentDto("new.txt", "content"));

        final String result = tool.createFile("new.txt", "content");

        assertThat(result).contains("new.txt").contains("created successfully");
    }

    @Test
    void createFile_alreadyExists_returnsErrorMessage() {
        when(fileService.create("exists.txt", "x"))
                .thenThrow(new IllegalStateException("File already exists: exists.txt"));

        final String result = tool.createFile("exists.txt", "x");

        assertThat(result).startsWith("Error:").contains("exists.txt");
    }

    // ── deleteFile ────────────────────────────────────────────────────────────

    @Test
    void deleteFile_success_returnsConfirmation() {
        final String result = tool.deleteFile("tmp/scratch.txt");

        verify(fileService).delete("tmp/scratch.txt");
        assertThat(result).contains("tmp/scratch.txt").contains("deleted successfully");
    }

    // ── listFiles ─────────────────────────────────────────────────────────────

    @Test
    void listFiles_withFiles_returnsTreeRepresentation() {
        final FileNodeDto leaf = new FileNodeDto("notes/todo.md", "todo.md", "file", 42L, null);
        final FileNodeDto dir = new FileNodeDto("notes", "notes", "dir", 0L, List.of(leaf));
        final FileNodeDto root = new FileNodeDto(".", ".", "dir", 0L, List.of(dir));
        when(fileService.tree()).thenReturn(root);

        final String result = tool.listFiles();

        assertThat(result).contains("notes/").contains("todo.md").contains("42 bytes");
    }

    @Test
    void listFiles_emptyWorkspace_returnsRootDirOnly() {
        final FileNodeDto root = new FileNodeDto(".", ".", "dir", 0L, List.of());
        when(fileService.tree()).thenReturn(root);

        final String result = tool.listFiles();

        assertThat(result).contains("[./]");
    }
}
