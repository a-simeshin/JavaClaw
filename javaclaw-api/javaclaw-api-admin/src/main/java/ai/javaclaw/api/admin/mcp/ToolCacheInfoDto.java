package ai.javaclaw.api.admin.mcp;

import java.util.List;

/** Snapshot of tool discovery cache state. */
public record ToolCacheInfoDto(List<String> toolNames, int count) {}
