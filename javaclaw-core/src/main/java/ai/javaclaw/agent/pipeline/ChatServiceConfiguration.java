package ai.javaclaw.agent.pipeline;

import ai.javaclaw.agent.audit.ChatAuditService;
import ai.javaclaw.agent.config.DefaultChatModelProperties;
import ai.javaclaw.agent.memory.ChatMemory;
import ai.javaclaw.configuration.ConfigurationManager;
import ai.javaclaw.tasks.ApprovalService;
import ai.javaclaw.tools.AutoDiscoveredTool;
import ai.javaclaw.tools.CheckListTool;
import ai.javaclaw.tools.McpTool;
import ai.javaclaw.tools.TaskTool;
import java.util.Set;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;

/**
 * Spring configuration для pipeline компонентов фазы 1. Регистрирует {@link
 * ToolCallbackResolver} и {@link ChatService} как Spring beans.
 *
 * <p>Создаёт tool beans ({@link CheckListTool}, {@link McpTool}, {@link FileSystemTools}),
 * которые ранее создавались inline в {@code JavaClawConfiguration.chatClient()}.
 */
@Configuration
@EnableConfigurationProperties({
    TokenBudgetProperties.class,
    ModelFallbackProperties.class,
    ToolDenyProperties.class,
    DefaultChatModelProperties.class
})
public class ChatServiceConfiguration {

    /**
     * Создаёт {@link CheckListTool} bean для управления чеклистами агента.
     *
     * @return новый экземпляр CheckListTool
     */
    @Bean
    public CheckListTool checkListTool() {
        return CheckListTool.builder().build();
    }

    /**
     * Создаёт {@link McpTool} bean для управления MCP серверами.
     *
     * @param configurationManager менеджер конфигурации, не может быть null
     * @return новый экземпляр McpTool
     */
    @Bean
    public McpTool mcpTool(final ConfigurationManager configurationManager) {
        return McpTool.builder().configurationManager(configurationManager).build();
    }

    /**
     * Создаёт {@link FileSystemTools} bean для работы с файловой системой.
     *
     * @return новый экземпляр FileSystemTools
     */
    @Bean
    public FileSystemTools fileSystemTools() {
        return FileSystemTools.builder().build();
    }

    /**
     * Создаёт {@link ToolCallbackResolver} — объединяет все tool callbacks из всех источников.
     *
     * @param mcpToolProvider провайдер MCP callbacks
     * @param taskTool инструмент задач
     * @param checkListTool инструмент чеклистов
     * @param mcpTool инструмент управления MCP
     * @param fileSystemTools инструмент файловой системы
     * @param autoDiscoveredTools набор auto-discovered tools
     * @return новый экземпляр ToolCallbackResolver
     */
    @Bean("javaClawToolCallbackResolver")
    public ToolCallbackResolver toolCallbackResolver(
            final SyncMcpToolCallbackProvider mcpToolProvider,
            final TaskTool taskTool,
            final CheckListTool checkListTool,
            final McpTool mcpTool,
            final FileSystemTools fileSystemTools,
            final Set<AutoDiscoveredTool<?>> autoDiscoveredTools,
            final ToolDenyProperties toolDenyProperties) {
        final ToolDenyFilter denyFilter = toolDenyProperties.enabled() ? new ToolDenyFilter(toolDenyProperties) : null;
        return new ToolCallbackResolver(
                mcpToolProvider,
                taskTool,
                checkListTool,
                mcpTool,
                fileSystemTools,
                autoDiscoveredTools,
                denyFilter,
                ToolCallbackResolver.DEFAULT_TTL,
                java.time.Clock.systemUTC());
    }

    /**
     * Создаёт {@link ChatService} — центральный оркестратор pipeline.
     *
     * @param chatModel модель LLM
     * @param chatMemory хранилище истории
     * @param messageAssembler ассемблер промптов
     * @param toolCallbackResolver резолвер tool callbacks
     * @param chatAuditService сервис аудита
     * @param approvalService сервис одобрений (nullable — может отсутствовать)
     * @return новый экземпляр ChatService
     */
    @Bean
    @Primary
    public ChatModel fallbackChatModel(
            final org.springframework.beans.factory.ObjectProvider<ChatModel> chatModelProvider,
            final ModelFallbackProperties fallbackProperties) {
        // Use ObjectProvider to get the non-primary ChatModel (avoids circular @Primary resolution).
        // Приоритизируем ReasoningCapableChatModel (reasoning-aware) при наличии в контексте.
        final ChatModel delegate = chatModelProvider.stream()
                .filter(m -> !(m instanceof FallbackChatModel))
                .sorted(java.util.Comparator.comparingInt(m -> m instanceof ReasoningCapableChatModel ? 0 : 1))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ChatModel bean found for FallbackChatModel delegate"));
        return new FallbackChatModel(delegate, fallbackProperties);
    }

    @Bean
    public ChatService chatService(
            final ChatModel chatModel,
            final ChatMemory chatMemory,
            final MessageAssembler messageAssembler,
            final ToolCallbackResolver toolCallbackResolver,
            final ChatAuditService chatAuditService,
            @Autowired(required = false) @Nullable final ApprovalService approvalService) {
        return new ChatService(
                chatModel, chatMemory, messageAssembler, toolCallbackResolver, chatAuditService, approvalService);
    }
}
