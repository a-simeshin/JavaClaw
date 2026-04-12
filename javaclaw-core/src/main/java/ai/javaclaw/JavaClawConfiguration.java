package ai.javaclaw;

import ai.javaclaw.tasks.TaskManager;
import ai.javaclaw.tools.TaskTool;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Основная Spring-конфигурация JavaClaw.
 *
 * <p>Регистрирует базовые бины: stub {@link ChatModel} и {@link TaskTool}. Сам
 * {@code ChatMemory} теперь приходит из модуля {@code javaclaw-memory} через
 * {@code JavaClawMemoryAutoConfiguration}
 * ({@code @ConditionalOnMissingBean(ChatMemory.class)}), поэтому @Bean здесь не нужен.
 */
@Configuration
public class JavaClawConfiguration {

    /** Имя файла приватного агентского промпта (приоритет над AGENT.md). */
    public static final String AGENT_MD = "AGENT.private.md";

    /**
     * Stub-реализация {@link ChatModel}, возвращающая сообщение об отсутствии конфигурации.
     * Регистрируется только если модель не задана в настройках.
     *
     * @return stub ChatModel
     */
    @Bean
    @ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = "unknown", matchIfMissing = true)
    public ChatModel chatModel() {
        return prompt -> new ChatResponse(
                List.of(
                        new Generation(
                                new AssistantMessage(
                                        "No AI model has been configured. If you did configure a model recently, restart JavaClaw manually for the changes to take effect."))));
    }

    /**
     * Создаёт {@link TaskTool} bean для управления задачами агента.
     *
     * @param taskManager менеджер задач
     * @return новый экземпляр TaskTool
     */
    @Bean
    public TaskTool taskTool(final TaskManager taskManager) {
        return TaskTool.builder().taskManager(taskManager).build();
    }
}
