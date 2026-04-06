package ai.javaclaw.api.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VercelSseEventTest {

    @Test
    void messageStartBuilderProducesExpectedValues() {
        VercelSseEvent.MessageStart event =
                VercelSseEvent.MessageStart.builder().messageId("msg_1").build();
        assertThat(event.type()).isEqualTo("message-start");
        assertThat(event.messageId()).isEqualTo("msg_1");
    }

    @Test
    void textDeltaBuilderProducesExpectedValues() {
        VercelSseEvent.TextDelta event =
                VercelSseEvent.TextDelta.builder().id("txt_1").delta("hello").build();
        assertThat(event.type()).isEqualTo("text-delta");
        assertThat(event.id()).isEqualTo("txt_1");
        assertThat(event.delta()).isEqualTo("hello");
    }

    @Test
    void toolInputAvailableBuilderProducesExpectedValues() {
        VercelSseEvent.ToolInputAvailable event = VercelSseEvent.ToolInputAvailable.builder()
                .toolCallId("tc_1")
                .toolName("shell")
                .input(Map.of("cmd", "ls"))
                .build();
        assertThat(event.type()).isEqualTo("tool-input-available");
        assertThat(event.toolCallId()).isEqualTo("tc_1");
        assertThat(event.toolName()).isEqualTo("shell");
        assertThat(event.input()).isEqualTo(Map.of("cmd", "ls"));
    }

    @Test
    void finishEventsHaveCorrectDiscriminators() {
        assertThat(VercelSseEvent.Finish.builder().build().type()).isEqualTo("finish");
        assertThat(VercelSseEvent.FinishStep.builder().build().type()).isEqualTo("finish-step");
    }

    @Test
    void errorBuilderCarriesMessage() {
        VercelSseEvent.Error e =
                VercelSseEvent.Error.builder().errorText("boom").build();
        assertThat(e.type()).isEqualTo("error");
        assertThat(e.errorText()).isEqualTo("boom");
    }
}
