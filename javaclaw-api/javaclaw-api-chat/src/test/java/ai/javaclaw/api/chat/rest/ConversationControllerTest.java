package ai.javaclaw.api.chat.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.conversations.ConversationEnsurer;
import ai.javaclaw.conversations.ConversationQueryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class ConversationControllerTest {

    private ChatMemoryRepository chatMemoryRepository;
    private ConversationEnsurer conversationEnsurer;
    private ConversationQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatMemoryRepository = mock(ChatMemoryRepository.class);
        conversationEnsurer = mock(ConversationEnsurer.class);
        queryService = mock(ConversationQueryService.class);
        mockMvc = standaloneSetup(new ConversationController(chatMemoryRepository, conversationEnsurer, queryService))
                .build();
    }

    @Test
    void listReturnsPagedConversationsFromQueryService() throws Exception {
        Instant t1 = Instant.parse("2026-04-05T10:00:00Z");
        Instant t2 = Instant.parse("2026-04-05T09:00:00Z");
        when(queryService.listConversations(0, 10))
                .thenReturn(new ConversationQueryService.Page<>(
                        List.of(
                                new ConversationQueryService.ConversationSummary(
                                        "web", "Stored title", t1, t1, 2, "Hello there"),
                                new ConversationQueryService.ConversationSummary("telegram-1", null, t2, t2, 0, null)),
                        0,
                        10,
                        2L));

        mockMvc.perform(get("/api/conversations?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].id").value("web"))
                .andExpect(jsonPath("$.content[0].title").value("Stored title"))
                .andExpect(jsonPath("$.content[0].messageCount").value(2))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-04-05T10:00:00Z"))
                // Title falls back to id when there's no persisted title and no first user message.
                .andExpect(jsonPath("$.content[1].title").value("telegram-1"));
    }

    @Test
    void listFallsBackToFirstUserMessageWhenTitleIsNull() throws Exception {
        Instant t = Instant.parse("2026-04-05T10:00:00Z");
        when(queryService.listConversations(0, 20))
                .thenReturn(new ConversationQueryService.Page<>(
                        List.of(new ConversationQueryService.ConversationSummary("web", null, t, t, 1, "Hello there")),
                        0,
                        20,
                        1L));

        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Hello there"));
    }

    @Test
    void messagesMapsTypesToRolesWithRealTimestamps() throws Exception {
        Instant t1 = Instant.parse("2026-04-05T10:00:00Z");
        Instant t2 = Instant.parse("2026-04-05T10:00:05Z");
        when(queryService.listMessages("web", 0, 50))
                .thenReturn(new ConversationQueryService.Page<>(
                        List.of(
                                new ConversationQueryService.MessageRow("Hi", "USER", t1),
                                new ConversationQueryService.MessageRow("Hello", "ASSISTANT", t2)),
                        0,
                        50,
                        2L));

        mockMvc.perform(get("/api/conversations/web/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].role").value("user"))
                .andExpect(jsonPath("$.content[0].content").value("Hi"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-04-05T10:00:00Z"))
                .andExpect(jsonPath("$.content[1].role").value("assistant"))
                .andExpect(jsonPath("$.content[1].createdAt").value("2026-04-05T10:00:05Z"));
    }

    @Test
    void messagesRespectsPagination() throws Exception {
        when(queryService.listMessages("web", 2, 10))
                .thenReturn(new ConversationQueryService.Page<>(List.of(), 2, 10, 7L));

        mockMvc.perform(get("/api/conversations/web/messages?page=2&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total").value(7));
        verify(queryService).listMessages(eq("web"), eq(2), eq(10));
    }

    @Test
    void createEnsuresExistsAndReturnsId() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Chat with Claude\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Chat with Claude"));
        verify(conversationEnsurer).ensureExists(org.mockito.ArgumentMatchers.anyString());
        verify(conversationEnsurer).touch(org.mockito.ArgumentMatchers.anyString(), eq("Chat with Claude"));
    }

    @Test
    void createAcceptsNoBody() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
        verify(conversationEnsurer).ensureExists(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/conversations/web")).andExpect(status().isNoContent());
        verify(chatMemoryRepository).deleteByConversationId(eq("web"));
    }
}
