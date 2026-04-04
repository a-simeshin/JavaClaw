package ai.javaclaw.api.admin.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * MCP server registration.
 *
 * @param transport {@code stdio} or {@code http}
 * @param command stdio command (only used when {@code transport=stdio})
 * @param url http endpoint (only used when {@code transport=http})
 */
public record McpServerDto(
        String id,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "stdio|http") String transport,
        String command,
        String url,
        Map<String, String> headers,
        boolean enabled) {}
