package ai.javaclaw.agent.pipeline;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация deny-паттернов для tool calling. Позволяет блокировать опасные команды
 * на уровне аргументов tool call.
 *
 * <p>Пример конфигурации:
 * <pre>
 * javaclaw.chat.tool-deny:
 *   enabled: true
 *   global-patterns:
 *     - "rm\\s+-rf\\s+/"
 *     - "DROP\\s+TABLE"
 *   per-tool:
 *     writeFile:
 *       - "\\.sh$"
 *       - "\\.jar$"
 *     deleteFile:
 *       - "AGENT\\.md"
 *       - "SOUL\\.md"
 * </pre>
 *
 * @param enabled включены ли deny-проверки (по умолчанию true)
 * @param globalPatterns regex-паттерны, блокируемые для ВСЕХ tool calls
 * @param perTool regex-паттерны, блокируемые для конкретного tool по имени
 */
@ConfigurationProperties(prefix = "javaclaw.chat.tool-deny")
public record ToolDenyProperties(boolean enabled, List<String> globalPatterns, Map<String, List<String>> perTool) {

    /** Конструктор с defaults. */
    public ToolDenyProperties {
        if (globalPatterns == null) {
            globalPatterns = List.of();
        }
        if (perTool == null) {
            perTool = Map.of();
        }
    }
}
