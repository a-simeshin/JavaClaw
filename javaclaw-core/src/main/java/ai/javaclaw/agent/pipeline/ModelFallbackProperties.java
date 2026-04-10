package ai.javaclaw.agent.pipeline;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация цепочки fallback-моделей. При недоступности основной модели
 * последовательно пробуются fallback-модели из списка.
 *
 * <p>Пример конфигурации:
 * <pre>
 * javaclaw.chat.fallback:
 *   enabled: true
 *   models:
 *     - google/gemini-2.0-flash-001
 *     - anthropic/claude-3.5-haiku
 *   max-retries-per-model: 1
 * </pre>
 *
 * @param enabled включён ли fallback (default: false)
 * @param models список fallback-моделей в порядке приоритета
 * @param maxRetriesPerModel максимальное число попыток на каждую модель (default: 1)
 */
@ConfigurationProperties("javaclaw.chat.fallback")
public record ModelFallbackProperties(boolean enabled, List<String> models, int maxRetriesPerModel) {

    public ModelFallbackProperties {
        if (models == null) {
            models = List.of();
        }
        if (maxRetriesPerModel <= 0) {
            maxRetriesPerModel = 1;
        }
    }
}
