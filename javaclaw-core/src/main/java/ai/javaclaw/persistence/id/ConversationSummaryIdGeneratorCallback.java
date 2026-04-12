package ai.javaclaw.persistence.id;

import ai.javaclaw.agent.pipeline.ConversationSummary;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Генератор идентификаторов для {@link ConversationSummary}.
 *
 * <p>Проставляет случайный UUID для сводки диалога, если {@code id} не задан.
 * Делает генерацию переносимой между диалектами БД.
 */
@Component
public class ConversationSummaryIdGeneratorCallback implements BeforeConvertCallback<ConversationSummary> {

    @Override
    public ConversationSummary onBeforeConvert(final ConversationSummary summary) {
        if (summary.id() == null) {
            return new ConversationSummary(
                    UUID.randomUUID().toString(),
                    summary.conversationId(),
                    summary.summaryText(),
                    summary.messagesCovered());
        }
        return summary;
    }
}
