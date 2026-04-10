package ai.javaclaw.agent.pipeline;

import ai.javaclaw.tools.AutoDiscoveredTool;
import ai.javaclaw.tools.CheckListTool;
import ai.javaclaw.tools.McpTool;
import ai.javaclaw.tools.TaskTool;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * результат с TTL ({@link #ttl}) и поддерживает ручную инвалидацию через {@link #invalidate()}.
 */
public class ToolCallbackResolver {

    /** TTL кэша по умолчанию — 5 минут. */
    static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

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

    /** TTL кэша — после истечения resolve() перестраивает список. */
    private final Duration ttl;

    /** Часы для проверки TTL (инжектируемые для тестируемости). */
    private final Clock clock;

    /** Кеш: пара (список callbacks, момент создания). */
    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    /** Запись кэша с timestamp создания. */
    record CacheEntry(List<ToolCallback> callbacks, Instant createdAt) {}

    /**
     * Создаёт резолвер с полным набором источников tool callbacks и TTL по умолчанию.
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
        this(
                mcpToolProvider,
                taskTool,
                checkListTool,
                mcpTool,
                fileSystemTools,
                autoDiscoveredTools,
                DEFAULT_TTL,
                Clock.systemUTC());
    }

    /**
     * Создаёт резолвер с настраиваемым TTL и часами (для тестов).
     */
    public ToolCallbackResolver(
            final SyncMcpToolCallbackProvider mcpToolProvider,
            final TaskTool taskTool,
            final CheckListTool checkListTool,
            final McpTool mcpTool,
            final FileSystemTools fileSystemTools,
            final Set<AutoDiscoveredTool<?>> autoDiscoveredTools,
            final Duration ttl,
            final Clock clock) {
        Assert.notNull(mcpToolProvider, "mcpToolProvider must not be null");
        Assert.notNull(taskTool, "taskTool must not be null");
        Assert.notNull(checkListTool, "checkListTool must not be null");
        Assert.notNull(mcpTool, "mcpTool must not be null");
        Assert.notNull(fileSystemTools, "fileSystemTools must not be null");
        Assert.notNull(autoDiscoveredTools, "autoDiscoveredTools must not be null");
        Assert.notNull(ttl, "ttl must not be null");
        Assert.notNull(clock, "clock must not be null");
        this.mcpToolProvider = mcpToolProvider;
        this.taskTool = taskTool;
        this.checkListTool = checkListTool;
        this.mcpTool = mcpTool;
        this.fileSystemTools = fileSystemTools;
        this.autoDiscoveredTools = autoDiscoveredTools;
        this.ttl = ttl;
        this.clock = clock;
    }

    /**
     * Возвращает объединённый список всех {@link ToolCallback} из всех источников. Результат
     * кешируется с TTL — после истечения перестраивается при следующем вызове.
     *
     * @return неизменяемый список всех tool callbacks
     */
    public List<ToolCallback> resolve() {
        final CacheEntry existing = cache.get();
        if (existing != null && !isExpired(existing)) {
            return existing.callbacks();
        }
        final List<ToolCallback> resolved = buildCallbacks();
        final CacheEntry newEntry = new CacheEntry(resolved, Instant.now(clock));
        cache.set(newEntry);
        return resolved;
    }

    /**
     * Инвалидирует кэш — следующий вызов {@link #resolve()} перестроит список.
     */
    public void invalidate() {
        cache.set(null);
    }

    /**
     * Возвращает количество кэшированных инструментов без пересборки.
     *
     * @return количество инструментов или 0 если кэш пуст/истёк
     */
    public int cachedToolCount() {
        final CacheEntry entry = cache.get();
        if (entry == null || isExpired(entry)) {
            return 0;
        }
        return entry.callbacks().size();
    }

    /**
     * Возвращает имена кэшированных инструментов без пересборки.
     *
     * @return список имён или пустой список если кэш пуст/истёк
     */
    public List<String> cachedToolNames() {
        final CacheEntry entry = cache.get();
        if (entry == null || isExpired(entry)) {
            return List.of();
        }
        return entry.callbacks().stream()
                .map(ToolCallback::getToolDefinition)
                .map(td -> td.name())
                .toList();
    }

    private boolean isExpired(final CacheEntry entry) {
        return Duration.between(entry.createdAt(), Instant.now(clock)).compareTo(ttl) > 0;
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
