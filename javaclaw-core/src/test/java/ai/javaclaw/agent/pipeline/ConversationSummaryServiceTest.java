package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    @Mock
    private ConversationSummaryRepository summaryRepository;

    @Mock
    private ChatModel chatModel;

    private ConversationSummaryService service;

    private static final String CONV_ID = "conv-summary-test";

    @BeforeEach
    void setUp() {
        service = new ConversationSummaryService(summaryRepository, chatModel);
    }

    @Test
    @DisplayName("getExistingSummary returns null when no summary exists")
    void getExistingSummary_returnsNull_whenNotFound() {
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.empty());
        assertThat(service.getExistingSummary(CONV_ID)).isNull();
    }

    @Test
    @DisplayName("getExistingSummary returns summary text when found")
    void getExistingSummary_returnsSummaryText_whenFound() {
        final ConversationSummary summary = ConversationSummary.create(CONV_ID, "User discussed Java Spring.", 10);
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.of(summary));
        assertThat(service.getExistingSummary(CONV_ID)).isEqualTo("User discussed Java Spring.");
    }

    @Test
    @DisplayName("summarizeDroppedMessages skips empty list")
    void summarizeDroppedMessages_skipsEmptyList() {
        service.summarizeDroppedMessages(CONV_ID, List.of());
        verify(chatModel, never()).call(any(Prompt.class));
        verify(summaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("summarizeDroppedMessages calls LLM and saves summary")
    void summarizeDroppedMessages_callsLlmAndSaves() {
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.empty());

        final String llmSummary = "User asked about Spring Security. Assistant explained Basic Auth.";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(llmSummary)))));

        final List<Message> dropped = List.of(
                new UserMessage("How does Spring Security work?"),
                new AssistantMessage("Spring Security provides authentication and authorization."));

        service.summarizeDroppedMessages(CONV_ID, dropped);

        verify(summaryRepository).deleteByConversationId(CONV_ID);
        final ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().summaryText()).isEqualTo(llmSummary);
        assertThat(captor.getValue().conversationId()).isEqualTo(CONV_ID);
        assertThat(captor.getValue().messagesCovered()).isEqualTo(2);
    }

    @Test
    @DisplayName("summarizeDroppedMessages merges with existing summary")
    void summarizeDroppedMessages_mergesWithExisting() {
        final ConversationSummary existing = ConversationSummary.create(CONV_ID, "Previous context about Java.", 5);
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.of(existing));

        final String mergedSummary = "Combined summary of Java discussion and Spring Security.";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mergedSummary)))));

        final List<Message> dropped =
                List.of(new UserMessage("Tell me about Spring Security"), new AssistantMessage("It handles auth"));

        service.summarizeDroppedMessages(CONV_ID, dropped);

        // Verify the prompt includes the existing summary
        final ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        final String promptText = promptCaptor.getValue().getInstructions().stream()
                .map(Message::getText)
                .reduce("", String::concat);
        assertThat(promptText).contains("Previous context about Java.");

        verify(summaryRepository).save(any(ConversationSummary.class));
    }

    @Test
    @DisplayName("summarizeDroppedMessages handles LLM error gracefully")
    void summarizeDroppedMessages_handlesLlmError() {
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.empty());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM unavailable"));

        final List<Message> dropped = List.of(new UserMessage("test"));

        // Should not throw
        service.summarizeDroppedMessages(CONV_ID, dropped);
        verify(summaryRepository, never()).save(any());
    }
}
