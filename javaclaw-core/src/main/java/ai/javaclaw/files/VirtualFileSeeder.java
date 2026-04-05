package ai.javaclaw.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * One-shot seed: loads files from the workspace directory into {@code virtual_files} table
 * (owner_id = NULL = global) on application startup. Skipped if any global files already exist.
 */
@Component
public class VirtualFileSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VirtualFileSeeder.class);

    private final VirtualFileRepository repository;
    private final Resource workspaceDir;

    public VirtualFileSeeder(
            final VirtualFileRepository repository,
            @Value("${agent.workspace:file:./workspace/}") final Resource workspaceDir) {
        this.repository = repository;
        this.workspaceDir = workspaceDir;
    }

    @Override
    public void run(final ApplicationArguments args) throws Exception {
        if (repository.countByOwnerIdIsNull() > 0) {
            log.info("VirtualFileSeeder: virtual_files already seeded, skipping");
            return;
        }

        final Path workspacePath = resolveWorkspacePath();
        if (workspacePath == null) {
            return;
        }

        final int seededCount = seedFromDirectory(workspacePath);
        log.info("VirtualFileSeeder: seeded {} files from workspace", seededCount);
    }

    private Path resolveWorkspacePath() {
        try {
            final Path path = workspaceDir.getFile().toPath().toAbsolutePath().normalize();
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                log.warn("VirtualFileSeeder: workspace directory does not exist or is not a directory: {}", path);
                return null;
            }
            return path;
        } catch (IOException e) {
            log.warn("VirtualFileSeeder: could not resolve workspace directory — {}", e.getMessage());
            return null;
        }
    }

    private int seedFromDirectory(final Path workspacePath) throws IOException {
        final List<Path> fileEntries = collectFiles(workspacePath);
        int count = 0;
        for (final Path filePath : fileEntries) {
            seedFile(workspacePath, filePath);
            count++;
        }
        return count;
    }

    private List<Path> collectFiles(final Path workspacePath) throws IOException {
        try (final Stream<Path> stream = Files.walk(workspacePath)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    private void seedFile(final Path workspacePath, final Path filePath) {
        try {
            final String relativePath =
                    workspacePath.relativize(filePath).toString().replace('\\', '/');
            final String content = Files.readString(filePath, StandardCharsets.UTF_8);
            final String contentType = inferContentType(relativePath);
            repository.save(VirtualFile.newGlobalFile(relativePath, content, contentType));
        } catch (IOException e) {
            log.warn("VirtualFileSeeder: skipping file {} — {}", filePath, e.getMessage());
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
}
