package ai.javaclaw.agent.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Фильтр, оборачивающий {@link ToolCallback} и блокирующий вызовы, аргументы которых
 * совпадают с deny-паттернами. Если аргументы содержат запрещённый паттерн, вместо
 * выполнения возвращается сообщение об ошибке.
 *
 * <p>Поддерживает глобальные паттерны (применяются ко всем tools) и per-tool паттерны.
 * Compiled regex кешируется для производительности.
 */
public class ToolDenyFilter {

    private static final Logger log = LoggerFactory.getLogger(ToolDenyFilter.class);

    private final List<Pattern> globalPatterns;
    private final Map<String, List<Pattern>> perToolPatterns;

    /**
     * Создаёт фильтр из конфигурации.
     *
     * @param properties конфигурация deny-паттернов, не null
     */
    public ToolDenyFilter(final ToolDenyProperties properties) {
        Assert.notNull(properties, "properties must not be null");
        this.globalPatterns = compilePatterns(properties.globalPatterns());
        this.perToolPatterns = new ConcurrentHashMap<>();
        for (final Map.Entry<String, List<String>> entry : properties.perTool().entrySet()) {
            final List<Pattern> compiled = compilePatterns(entry.getValue());
            if (!compiled.isEmpty()) {
                this.perToolPatterns.put(entry.getKey(), compiled);
            }
        }
    }

    /**
     * Оборачивает список callbacks deny-фильтром. Каждый callback проверяет аргументы
     * перед выполнением.
     *
     * @param callbacks исходные callbacks
     * @return обёрнутые callbacks
     */
    public List<ToolCallback> wrap(final List<ToolCallback> callbacks) {
        return callbacks.stream().map(this::wrapSingle).toList();
    }

    /**
     * Проверяет, блокируется ли вызов инструмента с указанными аргументами.
     *
     * @param toolName имя инструмента
     * @param arguments JSON-строка аргументов
     * @return сообщение о блокировке или null если разрешено
     */
    @Nullable
    public String checkDenied(final String toolName, final String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }

        // Global patterns
        for (final Pattern pattern : globalPatterns) {
            if (pattern.matcher(arguments).find()) {
                log.warn(
                        "Tool call '{}' DENIED by global pattern '{}': {}",
                        toolName,
                        pattern.pattern(),
                        truncate(arguments));
                return "Tool call denied: arguments match blocked pattern '" + pattern.pattern() + "'";
            }
        }

        // Per-tool patterns
        final List<Pattern> toolPatterns = perToolPatterns.get(toolName);
        if (toolPatterns != null) {
            for (final Pattern pattern : toolPatterns) {
                if (pattern.matcher(arguments).find()) {
                    log.warn(
                            "Tool call '{}' DENIED by tool-specific pattern '{}': {}",
                            toolName,
                            pattern.pattern(),
                            truncate(arguments));
                    return "Tool call denied: arguments match blocked pattern '" + pattern.pattern() + "' for tool '"
                            + toolName + "'";
                }
            }
        }

        return null;
    }

    private ToolCallback wrapSingle(final ToolCallback delegate) {
        final String toolName = delegate.getToolDefinition().name();
        // Skip wrapping if no patterns apply to this tool
        if (globalPatterns.isEmpty() && !perToolPatterns.containsKey(toolName)) {
            return delegate;
        }
        return new DenyFilterToolCallback(delegate, this);
    }

    private static List<Pattern> compilePatterns(final List<String> regexes) {
        if (regexes == null || regexes.isEmpty()) {
            return List.of();
        }
        final List<Pattern> compiled = new ArrayList<>(regexes.size());
        for (final String regex : regexes) {
            if (regex == null || regex.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            } catch (final PatternSyntaxException e) {
                log.error("Invalid deny pattern '{}', skipping: {}", regex, e.getMessage());
            }
        }
        return List.copyOf(compiled);
    }

    private static String truncate(final String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /**
     * Декоратор {@link ToolCallback}, проверяющий аргументы через {@link ToolDenyFilter}.
     */
    static final class DenyFilterToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final ToolDenyFilter filter;

        DenyFilterToolCallback(final ToolCallback delegate, final ToolDenyFilter filter) {
            this.delegate = delegate;
            this.filter = filter;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(final String toolInput) {
            final String denied =
                    filter.checkDenied(delegate.getToolDefinition().name(), toolInput);
            if (denied != null) {
                return denied;
            }
            return delegate.call(toolInput);
        }

        @Override
        public String call(final String toolInput, final org.springframework.ai.chat.model.ToolContext toolContext) {
            final String denied =
                    filter.checkDenied(delegate.getToolDefinition().name(), toolInput);
            if (denied != null) {
                return denied;
            }
            return delegate.call(toolInput, toolContext);
        }

        /** Возвращает оригинальный callback (для тестов). */
        ToolCallback delegate() {
            return delegate;
        }
    }
}
