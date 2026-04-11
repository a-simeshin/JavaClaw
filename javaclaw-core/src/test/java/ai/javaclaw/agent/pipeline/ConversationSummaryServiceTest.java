package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private TokenEstimator tokenEstimator;

    private ConversationSummaryService service;

    private static final String CONV_ID = "conv-summary-test";

    @BeforeEach
    void setUp() {
        service = new ConversationSummaryService(summaryRepository, chatModel, tokenEstimator);
    }

    // -----------------------------------------------------------------------
    // Existing tests (adapted for new 3-arg constructor)
    // -----------------------------------------------------------------------

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
    @DisplayName("summarizeDroppedMessages calls LLM and saves summary (single chunk)")
    void summarizeDroppedMessages_callsLlmAndSaves() {
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.empty());
        when(summaryRepository.findByConversationId(CONV_ID)).thenReturn(Optional.empty());

        final List<Message> dropped = List.of(
                new UserMessage("How does Spring Security work?"),
                new AssistantMessage("Spring Security provides authentication and authorization."));

        // tokenEstimator returns small values → single chunk
        when(tokenEstimator.estimate(any(Message.class))).thenReturn(10);

        final String llmSummary = "User asked about Spring Security. Assistant explained Basic Auth.";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(llmSummary)))));

        service.summarizeDroppedMessages(CONV_ID, dropped);

        // Fix #18: persistSummary uses deleteByConversationId + save (atomic)
        verify(summaryRepository).deleteByConversationId(CONV_ID);
        final ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().summaryText()).isEqualTo(llmSummary);
        assertThat(captor.getValue().conversationId()).isEqualTo(CONV_ID);
        // Fix #17: no previous → totalCovered == dropped.size()
        assertThat(captor.getValue().messagesCovered()).isEqualTo(2);
    }

    @Test
    @DisplayName("summarizeDroppedMessages merges with existing summary")
    void summarizeDroppedMessages_mergesWithExisting() {
        final ConversationSummary existing = ConversationSummary.create(CONV_ID, "Previous context about Java.", 5);
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.of(existing));
        when(summaryRepository.findByConversationId(CONV_ID)).thenReturn(Optional.of(existing));

        when(tokenEstimator.estimate(any(Message.class))).thenReturn(10);

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
        when(summaryRepository.findByConversationId(CONV_ID)).thenReturn(Optional.empty());
        when(tokenEstimator.estimate(any(Message.class))).thenReturn(10);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM unavailable"));

        final List<Message> dropped = List.of(new UserMessage("test"));

        // Should not throw
        service.summarizeDroppedMessages(CONV_ID, dropped);
        verify(summaryRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // New tests: fix #20_sum, #17, #18
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("summarizeDroppedMessages: chunked when batch exceeds token limit (fix #20_sum)")
    void summarizeDroppedMessages_chunked_whenExceedsTokenLimit() {
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.empty());
        when(summaryRepository.findByConversationId(CONV_ID)).thenReturn(Optional.empty());

        // Build 3 messages; each returns 60_000 tokens → 3 chunks (60k > 80k/2 but 60k+60k > 80k)
        // Two messages per chunk would be 120k > 80k, so each goes into its own chunk
        final List<Message> dropped =
                List.of(new UserMessage("chunk1"), new UserMessage("chunk2"), new UserMessage("chunk3"));
        when(tokenEstimator.estimate(any(Message.class))).thenReturn(60_000);

        final String chunkSummary = "partial";
        final String mergeSummary = "final merged";
        // 3 chunk calls + 1 merge call = 4 total
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(chunkSummary)))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(chunkSummary)))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(chunkSummary)))))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mergeSummary)))));

        service.summarizeDroppedMessages(CONV_ID, dropped);

        // ChatModel must have been called more than once (chunk calls + merge call)
        verify(chatModel, atLeast(2)).call(any(Prompt.class));

        // The final saved summary should be the merge result
        final ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().summaryText()).isEqualTo(mergeSummary);
    }

    @Test
    @DisplayName("summarizeDroppedMessages: messagesCovered is cumulative (fix #17)")
    void summarizeDroppedMessages_cumulativeCounter() {
        // Existing summary covers 5 messages
        final ConversationSummary existing = ConversationSummary.create(CONV_ID, "Old summary", 5);
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.of(existing));
        when(summaryRepository.findByConversationId(CONV_ID)).thenReturn(Optional.of(existing));
        when(tokenEstimator.estimate(any(Message.class))).thenReturn(10);

        final String llmSummary = "merged summary";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(llmSummary)))));

        // Drop 3 new messages
        final List<Message> dropped =
                List.of(new UserMessage("msg1"), new AssistantMessage("msg2"), new UserMessage("msg3"));

        service.summarizeDroppedMessages(CONV_ID, dropped);

        final ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(summaryRepository).save(captor.capture());
        // 5 (previous) + 3 (new) = 8
        assertThat(captor.getValue().messagesCovered()).isEqualTo(8);
    }

    @Test
    @DisplayName("summarizeDroppedMessages: atomic upsert — save called exactly once (fix #18)")
    void summarizeDroppedMessages_atomicUpsert_saveCalledOnce() {
        when(summaryRepository.findLatestByConversationId(CONV_ID)).thenReturn(Optional.empty());
        when(summaryRepository.findByConversationId(CONV_ID)).thenReturn(Optional.empty());
        when(tokenEstimator.estimate(any(Message.class))).thenReturn(10);

        final String llmSummary = "summary text";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(llmSummary)))));

        final List<Message> dropped = List.of(new UserMessage("hello"), new AssistantMessage("world"));

        service.summarizeDroppedMessages(CONV_ID, dropped);

        // persistSummary: deleteByConversationId called once, save called exactly once
        verify(summaryRepository, times(1)).deleteByConversationId(CONV_ID);
        verify(summaryRepository, times(1)).save(any(ConversationSummary.class));
    }
}
