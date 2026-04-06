package ai.javaclaw.agent.pipeline;

import ai.javaclaw.tools.AutoDiscoveredTool;
import ai.javaclaw.tools.CheckListTool;
import ai.javaclaw.tools.McpTool;
import ai.javaclaw.tools.TaskTool;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * Собирает все {@link ToolCallback} из всех источников для использования в {@link ChatService}.
 *
 * <p>Не является Spring bean — создаётся через {@link ChatServiceConfiguration}. Кеширует
 * результат при первом вызове {@link #resolve()} (lazy init, thread-safe через {@link
 * AtomicReference}).
 */
public class ToolCallbackResolver {

    /** Провайдер MCP tool callbacks (синхронный). */
    private final SyncMcpToolCallbackProvider mcpToolProvider;

    /** Инструмент для управления задачами агента. */
    private final TaskTool taskTool;

    /** Инструмент для управления чеклистами. */
    private final CheckListTool checkListTool;

    /** Инструмент для управления MCP серверами. */
    private final McpTool mcpTool;

    /** Инструмент для работы с файловой системой. */
    private final FileSystemTools fileSystemTools;

    /** Набор auto-discovered tool instances, обнаруженных Spring. */
    private final Set<AutoDiscoveredTool<?>> autoDiscoveredTools;

    /** Кеш результата resolve() — заполняется один раз при первом вызове. */
    private final AtomicReference<List<ToolCallback>> cache = new AtomicReference<>();

    /**
     * Создаёт резолвер с полным набором источников tool callbacks.
     *
     * @param mcpToolProvider провайдер MCP callbacks, не может быть null
     * @param taskTool инструмент задач, не может быть null
     * @param checkListTool инструмент чеклистов, не может быть null
     * @param mcpTool инструмент управления MCP, не может быть null
     * @param fileSystemTools инструмент файловой системы, не может быть null
     * @param autoDiscoveredTools набор auto-discovered tools, не может быть null
     */
    public ToolCallbackResolver(
            final SyncMcpToolCallbackProvider mcpToolProvider,
            final TaskTool taskTool,
            final CheckListTool checkListTool,
            final McpTool mcpTool,
            final FileSystemTools fileSystemTools,
            final Set<AutoDiscoveredTool<?>> autoDiscoveredTools) {
        Assert.notNull(mcpToolProvider, "mcpToolProvider must not be null");
        Assert.notNull(taskTool, "taskTool must not be null");
        Assert.notNull(checkListTool, "checkListTool must not be null");
        Assert.notNull(mcpTool, "mcpTool must not be null");
        Assert.notNull(fileSystemTools, "fileSystemTools must not be null");
        Assert.notNull(autoDiscoveredTools, "autoDiscoveredTools must not be null");
        this.mcpToolProvider = mcpToolProvider;
        this.taskTool = taskTool;
        this.checkListTool = checkListTool;
        this.mcpTool = mcpTool;
        this.fileSystemTools = fileSystemTools;
        this.autoDiscoveredTools = autoDiscoveredTools;
    }

    /**
     * Возвращает объединённый список всех {@link ToolCallback} из всех источников. Результат
     * кешируется при первом вызове — последующие вызовы возвращают тот же список.
     *
     * @return неизменяемый список всех tool callbacks
     */
    public List<ToolCallback> resolve() {
        final List<ToolCallback> existing = cache.get();
        if (existing != null) {
            return existing;
        }
        final List<ToolCallback> resolved = buildCallbacks();
        cache.compareAndSet(null, resolved);
        return cache.get();
    }

    /**
     * Строит полный список callbacks из всех источников. Вызывается один раз при ленивой
     * инициализации.
     *
     * @return список всех собранных callbacks
     */
    private List<ToolCallback> buildCallbacks() {
        final List<ToolCallback> result = new ArrayList<>();

        // MCP tool callbacks
        result.addAll(Arrays.asList(mcpToolProvider.getToolCallbacks()));

        // Built-in tool callbacks
        result.addAll(Arrays.asList(ToolCallbacks.from(taskTool, checkListTool, mcpTool, fileSystemTools)));

        // Auto-discovered tool callbacks
        for (final AutoDiscoveredTool<?> autoDiscoveredTool : autoDiscoveredTools) {
            result.addAll(Arrays.asList(ToolCallbacks.from(autoDiscoveredTool.tool())));
        }

        return List.copyOf(result);
    }
}
