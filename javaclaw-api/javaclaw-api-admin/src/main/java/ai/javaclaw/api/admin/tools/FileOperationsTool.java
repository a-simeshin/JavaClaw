package ai.javaclaw.api.admin.tools;

import ai.javaclaw.api.admin.files.FileContentDto;
import ai.javaclaw.api.admin.files.FileNodeDto;
import ai.javaclaw.api.admin.files.VirtualFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Agent tool for reading, writing, listing, creating and deleting virtual files stored in the
 * database. All file paths are relative (no leading slash, no {@code ..} segments).
 */
public class FileOperationsTool {

    private static final Logger logger = LoggerFactory.getLogger(FileOperationsTool.class);

    private final VirtualFileService fileService;

    public FileOperationsTool(final VirtualFileService fileService) {
        this.fileService = fileService;
    }

    @Tool(
            description =
                    """
            Reads the content of a virtual file stored in the database.
            Use this to inspect existing workspace files.

            - path: Relative file path (e.g. 'notes/todo.md'). Must not start with '/' or contain '..'.

            Returns the file content as a string, or an error message if the file does not exist.
            """)
    public String readFile(final String path) {
        try {
            final FileContentDto dto = fileService.read(path);
            return dto.content();
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            logger.error("readFile failed for path={}", path, e);
            return "Error: Could not read file. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Writes (creates or overwrites) a virtual file in the database with the given content.
            Use this to update an existing file or create a new one.

            - path: Relative file path (e.g. 'notes/todo.md'). Must not start with '/' or contain '..'.
            - content: Full text content to store in the file.

            Returns a confirmation message with the saved path.
            """)
    public String writeFile(final String path, final String content) {
        try {
            final FileContentDto dto = fileService.write(path, content);
            return String.format(
                    "File '%s' written successfully (%d chars).",
                    dto.path(), dto.content().length());
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            logger.error("writeFile failed for path={}", path, e);
            return "Error: Could not write file. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Creates a new virtual file in the database. Fails if a file at the given path already exists.
            Use this when you explicitly want to create a new file and avoid accidental overwrites.

            - path: Relative file path (e.g. 'reports/summary.md'). Must not start with '/' or contain '..'.
            - content: Initial content for the new file.

            Returns a confirmation message, or an error if the file already exists.
            """)
    public String createFile(final String path, final String content) {
        try {
            final FileContentDto dto = fileService.create(path, content);
            return String.format(
                    "File '%s' created successfully (%d chars).",
                    dto.path(), dto.content().length());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            logger.error("createFile failed for path={}", path, e);
            return "Error: Could not create file. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Deletes a virtual file from the database by path.
            Use this when the user asks you to remove or clean up a workspace file.

            - path: Relative file path (e.g. 'tmp/scratch.txt'). Must not start with '/' or contain '..'.

            Returns a confirmation message. If the file did not exist, no error is raised.
            """)
    public String deleteFile(final String path) {
        try {
            fileService.delete(path);
            return String.format("File '%s' deleted successfully.", path);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            logger.error("deleteFile failed for path={}", path, e);
            return "Error: Could not delete file. " + e.getMessage();
        }
    }

    @Tool(
            description =
                    """
            Lists all virtual files in the workspace as a tree structure.
            Use this to explore the workspace layout before reading or writing files.

            Returns a text representation of the file tree (paths and sizes).
            """)
    public String listFiles() {
        try {
            final FileNodeDto root = fileService.tree();
            final StringBuilder sb = new StringBuilder();
            appendNode(sb, root, "");
            return sb.toString();
        } catch (Exception e) {
            logger.error("listFiles failed", e);
            return "Error: Could not list files. " + e.getMessage();
        }
    }

    private static void appendNode(final StringBuilder sb, final FileNodeDto node, final String indent) {
        if ("dir".equals(node.type())) {
            sb.append(indent).append("[").append(node.name()).append("/]").append(System.lineSeparator());
            if (node.children() != null) {
                for (final FileNodeDto child : node.children()) {
                    appendNode(sb, child, indent + "  ");
                }
            }
        } else {
            sb.append(indent)
                    .append(node.name())
                    .append(" (")
                    .append(node.size())
                    .append(" bytes)")
                    .append(System.lineSeparator());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private VirtualFileService fileService;

        public Builder fileService(final VirtualFileService fileService) {
            this.fileService = fileService;
            return this;
        }

        public FileOperationsTool build() {
            return new FileOperationsTool(this.fileService);
        }
    }
}
