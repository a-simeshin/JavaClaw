package ai.javaclaw.api.admin.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

class WorkspaceFileServiceTest {

    @TempDir
    Path tempDir;

    WorkspaceFileService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new WorkspaceFileService(new FileSystemResource(tempDir.toFile()));
    }

    @Test
    void writeReadDeleteRoundTrip() throws IOException {
        service.write("notes/todo.md", "- write tests");
        FileContentDto read = service.read("notes/todo.md");
        assertThat(read.content()).isEqualTo("- write tests");

        service.delete("notes/todo.md");
        assertThatThrownBy(() -> service.read("notes/todo.md")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void treeListsCreatedFiles() throws IOException {
        service.write("a.md", "A");
        service.write("sub/b.md", "B");
        FileNodeDto root = service.tree();
        assertThat(root.type()).isEqualTo("dir");
        assertThat(root.children()).extracting(FileNodeDto::name).contains("a.md", "sub");
    }

    @Test
    void pathTraversalIsRejected() {
        assertThatThrownBy(() -> service.read("../escape.txt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRefusesExisting() throws IOException {
        service.write("x.txt", "1");
        assertThatThrownBy(() -> service.create("x.txt", "2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWritesContent() throws IOException {
        service.create("new.md", "fresh");
        assertThat(Files.readString(tempDir.resolve("new.md"))).isEqualTo("fresh");
    }
}
