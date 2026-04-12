package ai.javaclaw.persistence.id;

import ai.javaclaw.agent.config.RoleAgentConfig;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link RoleAgentConfig}.
 *
 * <p>Присваивает случайный UUID новой конфигурации роли, если {@code id} не задан.
 * Убирает зависимость от DB-дефолта при сохранении.
 */
@Component
public class RoleAgentConfigIdGeneratorCallback implements BeforeConvertCallback<RoleAgentConfig> {

    @Override
    public RoleAgentConfig onBeforeConvert(final RoleAgentConfig config) {
        if (config.id() == null) {
            return new RoleAgentConfig(
                    UUID.randomUUID().toString(),
                    config.role(),
                    config.modelId(),
                    config.thinkingEnabled(),
                    config.thinkingBudget(),
                    config.fallbackModels(),
                    config.maxContextTokens());
        }
        return config;
    }
}
