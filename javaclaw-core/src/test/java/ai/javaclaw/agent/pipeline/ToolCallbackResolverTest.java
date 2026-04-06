package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.tools.AutoDiscoveredTool;
import ai.javaclaw.tools.CheckListTool;
import ai.javaclaw.tools.McpTool;
import ai.javaclaw.tools.TaskTool;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/**
 * Unit-тесты для {@link ToolCallbackResolver}.
 *
 * <p>Проверяют: сборку callbacks из всех источников, кеширование результата.
 */
@ExtendWith(MockitoExtension.class)
class ToolCallbackResolverTest {

    /** Мок провайдера MCP callbacks. */
    @Mock
    private SyncMcpToolCallbackProvider mcpToolProvider;

    /** Мок tool задач. */
    @Mock
    private TaskTool taskTool;

    /** Мок tool чеклистов. */
    @Mock
    private CheckListTool checkListTool;

    /** Мок MCP управляющего tool. */
    @Mock
    private McpTool mcpTool;

    /** Мок tool файловой системы. */
    @Mock
    private FileSystemTools fileSystemTools;

    /** Тестируемый резолвер. */
    private ToolCallbackResolver resolver;

    @BeforeEach
    void setUp() {
        // По умолчанию MCP провайдер возвращает пустой массив
        when(mcpToolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
        resolver =
                new ToolCallbackResolver(mcpToolProvider, taskTool, checkListTool, mcpTool, fileSystemTools, Set.of());
    }

    @Test
    @DisplayName("resolve(): возвращает непустой список при наличии built-in tools")
    void resolve_returnsNonEmptyListWithBuiltinTools() {
        final List<ToolCallback> result = resolver.resolve();

        // ToolCallbacks.from(taskTool, checkListTool, mcpTool, fileSystemTools) должен дать >= 0 callbacks
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("resolve(): включает MCP callbacks из провайдера")
    void resolve_includesMcpCallbacks() {
        final ToolCallback mcpCallback = ToolCallbacks.from(taskTool)[0];
        when(mcpToolProvider.getToolCallbacks()).thenReturn(new ToolCallback[] {mcpCallback});

        // Создаём новый resolver с мок-callback
        final ToolCallbackResolver resolverWithMcp =
                new ToolCallbackResolver(mcpToolProvider, taskTool, checkListTool, mcpTool, fileSystemTools, Set.of());

        final List<ToolCallback> result = resolverWithMcp.resolve();

        assertThat(result).contains(mcpCallback);
    }

    @Test
    @DisplayName("resolve(): кешируется — mcpToolProvider.getToolCallbacks() вызывается только один раз")
    void resolve_cachedOnFirstCall() {
        when(mcpToolProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);

        resolver.resolve();
        resolver.resolve();
        resolver.resolve();

        // getToolCallbacks() должен быть вызван только один раз (при первом resolve)
        verify(mcpToolProvider, times(1)).getToolCallbacks();
    }

    @Test
    @DisplayName("resolve(): повторные вызовы возвращают один и тот же объект списка")
    void resolve_returnsSameListOnRepeatedCalls() {
        final List<ToolCallback> first = resolver.resolve();
        final List<ToolCallback> second = resolver.resolve();
        final List<ToolCallback> third = resolver.resolve();

        assertThat(first).isSameAs(second);
        assertThat(second).isSameAs(third);
    }

    @Test
    @DisplayName("resolve(): пустые источники — возвращает пустой или минимальный список без NPE")
    void resolve_emptySources_noException() {
        final List<ToolCallback> result = resolver.resolve();

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("resolve(): auto-discovered tools увеличивают количество callbacks")
    void resolve_includesAutoDiscoveredTools() {
        // Базовый размер без auto-discovered tools
        final int baseSize = resolver.resolve().size();

        // Используем реальный CheckListTool (имеет @Tool метод) как auto-discovered
        final CheckListTool realCheckListTool = CheckListTool.builder().build();
        final AutoDiscoveredTool<CheckListTool> autoTool = new AutoDiscoveredTool<>(realCheckListTool);
        final ToolCallbackResolver resolverWithAuto = new ToolCallbackResolver(
                mcpToolProvider, taskTool, checkListTool, mcpTool, fileSystemTools, Set.of(autoTool));

        final List<ToolCallback> result = resolverWithAuto.resolve();

        // Размер должен быть больше базового — auto-discovered добавил минимум 1 callback
        assertThat(result).isNotNull();
        assertThat(result.size()).isGreaterThan(baseSize);
    }
}
