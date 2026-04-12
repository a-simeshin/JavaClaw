package ai.javaclaw.persistence.id;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.pipeline.ConversationSummary;
import org.junit.jupiter.api.Test;

class ConversationSummaryIdGeneratorCallbackTest {

    private final ConversationSummaryIdGeneratorCallback callback = new ConversationSummaryIdGeneratorCallback();

    @Test
    void generates_uuid_when_id_is_null() {
        final ConversationSummary input = ConversationSummary.create("conv-1", "user discussed cats", 12);

        final ConversationSummary result = callback.onBeforeConvert(input);

        assertThat(result.id()).isNotNull();
        assertThat(result.conversationId()).isEqualTo("conv-1");
        assertThat(result.summaryText()).isEqualTo("user discussed cats");
        assertThat(result.messagesCovered()).isEqualTo(12);
    }

    @Test
    void keeps_existing_id() {
        final ConversationSummary input = new ConversationSummary("fixed-id", "conv-2", "summary", 5);

        final ConversationSummary result = callback.onBeforeConvert(input);

        assertThat(result).isSameAs(input);
        assertThat(result.id()).isEqualTo("fixed-id");
    }
}
