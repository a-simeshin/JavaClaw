package ai.javaclaw.api.admin.files;

import ai.javaclaw.files.VirtualFile;
import ai.javaclaw.files.VirtualFileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * JDBC-backed file service. Replaces {@link WorkspaceFileService} as the backend for
 * {@link FileController}. All operations work on global files (owner_id = NULL) until
 * Spring Security is wired in Phase 4.3.
 */
@Service
public class VirtualFileService {

    private final VirtualFileRepository repository;

    public VirtualFileService(final VirtualFileRepository repository) {
        this.repository = repository;
    }

    public FileNodeDto tree() {
        final List<VirtualFile> files = repository.findAllByOwnerIdIsNull();
        return buildTree(files);
    }

    public FileContentDto read(final String path) {
        validatePath(path);
        final Optional<VirtualFile> found = repository.findByOwnerIdIsNullAndPath(path);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        final VirtualFile file = found.get();
        return new FileContentDto(file.path(), file.content());
    }

    public FileContentDto write(final String path, final String content) {
        validatePath(path);
        final Optional<VirtualFile> existing = repository.findByOwnerIdIsNullAndPath(path);
        final String safeContent = content == null ? "" : content;
        final VirtualFile toSave;
        if (existing.isPresent()) {
            toSave = existing.get().withUpdatedContent(safeContent);
        } else {
            toSave = VirtualFile.newGlobalFile(path, safeContent, inferContentType(path));
        }
        final VirtualFile saved = repository.save(toSave);
        return new FileContentDto(saved.path(), saved.content());
    }

    public void delete(final String path) {
        validatePath(path);
        repository.deleteByOwnerIdIsNullAndPath(path);
    }

    public FileContentDto create(final String path, final String content) {
        validatePath(path);
        if (repository.existsByOwnerIdIsNullAndPath(path)) {
            throw new IllegalStateException("File already exists: " + path);
        }
        final String safeContent = content == null ? "" : content;
        final VirtualFile saved = repository.save(VirtualFile.newGlobalFile(path, safeContent, inferContentType(path)));
        return new FileContentDto(saved.path(), saved.content());
    }

    private static void validatePath(final String path) {
        Assert.hasText(path, "path must not be blank");
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("path must not be absolute: " + path);
        }
        if (path.contains("..")) {
            throw new IllegalArgumentException("path must not contain '..': " + path);
        }
    }

    private static String inferContentType(final String path) {
        if (path.endsWith(".md")) {
            return "text/markdown";
        }
        if (path.endsWith(".json")) {
            return "application/json";
        }
        if (path.endsWith(".yaml") || path.endsWith(".yml")) {
            return "text/yaml";
        }
        return "text/plain";
    }

    private FileNodeDto buildTree(final List<VirtualFile> files) {
        final List<FileNodeDto> rootChildren = new ArrayList<>();
        for (final VirtualFile file : files) {
            insertIntoTree(rootChildren, file.path(), file);
        }
        rootChildren.sort((a, b) -> a.name().compareTo(b.name()));
        return new FileNodeDto(".", "workspace", "dir", 0L, rootChildren);
    }

    private static void insertIntoTree(
            final List<FileNodeDto> siblings, final String remainingPath, final VirtualFile file) {
        final int slashIndex = remainingPath.indexOf('/');
        if (slashIndex < 0) {
            // leaf file node
            siblings.add(new FileNodeDto(file.path(), remainingPath, "file", file.sizeBytes(), null));
            return;
        }
        final String dirName = remainingPath.substring(0, slashIndex);
        final String rest = remainingPath.substring(slashIndex + 1);
        final FileNodeDto existingDir = findDir(siblings, dirName);
        if (existingDir != null) {
            insertIntoTree(existingDir.children(), rest, file);
        } else {
            final List<FileNodeDto> children = new ArrayList<>();
            final String dirPath = computeDirPath(file.path(), rest);
            siblings.add(new FileNodeDto(dirPath, dirName, "dir", 0L, children));
            insertIntoTree(children, rest, file);
        }
    }

    private static FileNodeDto findDir(final List<FileNodeDto> siblings, final String name) {
        for (final FileNodeDto node : siblings) {
            if ("dir".equals(node.type()) && name.equals(node.name())) {
                return node;
            }
        }
        return null;
    }

    private static String computeDirPath(final String fullPath, final String rest) {
        final int endIndex = fullPath.length() - rest.length() - 1;
        return fullPath.substring(0, endIndex);
    }
}
