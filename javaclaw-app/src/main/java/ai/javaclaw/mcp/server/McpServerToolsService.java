package ai.javaclaw.mcp.server;

import ai.javaclaw.api.admin.files.FileContentDto;
import ai.javaclaw.api.admin.files.FileNodeDto;
import ai.javaclaw.api.admin.files.VirtualFileService;
import ai.javaclaw.conversations.ConversationQueryService;
import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Exposes JavaClaw's core capabilities as MCP tools via Streamable HTTP protocol.
 * External agents and clients can discover and call these tools through the MCP endpoint.
 */
@Component
public class McpServerToolsService {

    private final VirtualFileService fileService;
    private final SkillRepository skillRepository;
    private final ConversationQueryService conversationQueryService;

    public McpServerToolsService(
            VirtualFileService fileService,
            SkillRepository skillRepository,
            ConversationQueryService conversationQueryService) {
        this.fileService = fileService;
        this.skillRepository = skillRepository;
        this.conversationQueryService = conversationQueryService;
    }

    @McpTool(
            name = "readFile",
            description = "Read a virtual file from JavaClaw workspace. Returns file content as string.")
    public String readFile(@McpToolParam(description = "Relative file path, e.g. 'notes/todo.md'") String path) {
        try {
            FileContentDto dto = fileService.read(path);
            return dto.content();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(
            name = "writeFile",
            description = "Write content to a virtual file in JavaClaw workspace. Creates or overwrites the file.")
    public String writeFile(
            @McpToolParam(description = "Relative file path") String path,
            @McpToolParam(description = "File content to write") String content) {
        try {
            fileService.write(path, content);
            return "File written: " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(name = "listFiles", description = "List all virtual files in JavaClaw workspace as a tree structure.")
    public String listFiles() {
        try {
            FileNodeDto root = fileService.tree();
            return formatTree(root.children(), 0);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(name = "deleteFile", description = "Delete a virtual file from JavaClaw workspace.")
    public String deleteFile(@McpToolParam(description = "Relative file path to delete") String path) {
        try {
            fileService.delete(path);
            return "File deleted: " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(name = "listSkills", description = "List all available skills (prompt templates) in JavaClaw.")
    public String listSkills() {
        try {
            Iterable<Skill> skills = skillRepository.findAll();
            return StreamSupport.stream(skills.spliterator(), false)
                    .map(s -> "- " + s.name() + (s.enabled() ? " [enabled]" : " [disabled]") + ": "
                            + truncate(s.content(), 100))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @McpTool(
            name = "listConversations",
            description = "List recent conversations in JavaClaw. Returns conversation IDs and titles.")
    public String listConversations(
            @McpToolParam(description = "Page number (0-based)", required = false) Integer page,
            @McpToolParam(description = "Page size", required = false) Integer size) {
        try {
            int p = page != null ? page : 0;
            int s = size != null ? size : 20;
            var result = conversationQueryService.listConversations(p, s);
            if (result.content().isEmpty()) {
                return "No conversations found.";
            }
            return result.content().stream()
                    .map(c -> "- " + c.id() + " | " + c.title() + " | " + c.updatedAt())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String formatTree(List<FileNodeDto> nodes, int depth) {
        if (nodes == null || nodes.isEmpty()) return "(empty)";
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);
        for (FileNodeDto node : nodes) {
            sb.append(indent)
                    .append("directory".equals(node.type()) ? "[dir] " : "")
                    .append(node.name())
                    .append("\n");
            if (node.children() != null && !node.children().isEmpty()) {
                sb.append(formatTree(node.children(), depth + 1));
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
