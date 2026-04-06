package ai.javaclaw.agent;

import ai.javaclaw.agent.pipeline.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Реализация {@link Agent} по умолчанию — делегирует запросы к {@link ChatService}.
 *
 * <p>ChatService самостоятельно управляет системным промптом, историей и tool callbacks,
 * поэтому DefaultAgent остаётся тонкой обёрткой над pipeline.
 */
@Component
public class DefaultAgent implements Agent {

    /** Сервис-оркестратор pipeline запросов к LLM. */
    private final ChatService chatService;

    /**
     * Создаёт DefaultAgent с указанным ChatService.
     *
     * @param chatService оркестратор pipeline, не может быть null
     */
    public DefaultAgent(final ChatService chatService) {
        Assert.notNull(chatService, "chatService must not be null");
        this.chatService = chatService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Делегирует синхронный вызов к {@link ChatService#call(String, String)}.
     */
    @Override
    public String respondTo(final String conversationId, final String question) {
        return chatService.call(conversationId, question);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Делегирует структурированный вызов к {@link ChatService#call(String, String, Class)}.
     */
    @Override
    public <T> T prompt(final String conversationId, final String input, final Class<T> result) {
        return chatService.call(conversationId, input, result);
    }
}
