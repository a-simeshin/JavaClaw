package ai.javaclaw.api.admin.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Reads / writes user-editable files under {@code agent.workspace}. Path
 * traversal ({@code ..}) and absolute paths are rejected — all requests are
 * resolved against and clamped to the workspace root.
 */
@Service
public class WorkspaceFileService {

    private final Path root;

    public WorkspaceFileService(@Value("${agent.workspace:file:./workspace/}") Resource workspace) throws IOException {
        this.root = workspace.getFile().toPath().toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public FileNodeDto tree() throws IOException {
        return buildTree(root);
    }

    public FileContentDto read(String relative) throws IOException {
        Path target = resolve(relative);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new NoSuchElementException("file not found: " + relative);
        }
        String content = Files.readString(target, StandardCharsets.UTF_8);
        return new FileContentDto(relative, content);
    }

    public FileContentDto write(String relative, String content) throws IOException {
        Path target = resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        return new FileContentDto(relative, content == null ? "" : content);
    }

    public void delete(String relative) throws IOException {
        Path target = resolve(relative);
        if (!Files.exists(target)) {
            throw new NoSuchElementException("file not found: " + relative);
        }
        if (Files.isDirectory(target)) {
            deleteRecursively(target);
        } else {
            Files.delete(target);
        }
    }

    public FileContentDto create(String relative, String content) throws IOException {
        Path target = resolve(relative);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("file already exists: " + relative);
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        return new FileContentDto(relative, content == null ? "" : content);
    }

    private Path resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        String normalizedInput = relative.replace('\\', '/').replaceFirst("^/+", "");
        Path resolved = root.resolve(normalizedInput).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path escapes workspace: " + relative);
        }
        return resolved;
    }

    private FileNodeDto buildTree(Path dir) throws IOException {
        List<FileNodeDto> children = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            List<Path> entries =
                    stream.sorted(Comparator.comparing(Path::getFileName)).toList();
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    children.add(buildTree(entry));
                } else {
                    children.add(new FileNodeDto(
                            root.relativize(entry).toString().replace('\\', '/'),
                            entry.getFileName().toString(),
                            "file",
                            Files.size(entry),
                            null));
                }
            }
        }
        String relative =
                root.equals(dir) ? "" : root.relativize(dir).toString().replace('\\', '/');
        String name = root.equals(dir) ? "workspace" : dir.getFileName().toString();
        return new FileNodeDto(relative, name, "dir", 0L, children);
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
