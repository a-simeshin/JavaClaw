package ai.javaclaw.api.chat.rest;

import static org.mockito.ArgumentMatchers.any;
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

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class ConversationControllerTest {

    private ChatMemoryRepository chatMemoryRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatMemoryRepository = mock(ChatMemoryRepository.class);
        mockMvc = standaloneSetup(new ConversationController(chatMemoryRepository))
                .build();
    }

    @Test
    void listReturnsPagedConversations() throws Exception {
        when(chatMemoryRepository.findConversationIds()).thenReturn(List.of("web", "telegram-1"));
        when(chatMemoryRepository.findByConversationId("web"))
                .thenReturn(List.of(new UserMessage("Hello there"), new AssistantMessage("Hi!")));
        when(chatMemoryRepository.findByConversationId("telegram-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/conversations?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].id").value("web"))
                .andExpect(jsonPath("$.content[0].messageCount").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Hello there"));
    }

    @Test
    void messagesMapsUserAndAssistantRoles() throws Exception {
        when(chatMemoryRepository.findByConversationId("web"))
                .thenReturn(List.of(new UserMessage("Hi"), new AssistantMessage("Hello")));

        mockMvc.perform(get("/api/conversations/web/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("user"))
                .andExpect(jsonPath("$.content[0].content").value("Hi"))
                .andExpect(jsonPath("$.content[1].role").value("assistant"))
                .andExpect(jsonPath("$.content[1].content").value("Hello"));
    }

    @Test
    void createReturnsNewConversationId() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Chat with Claude\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Chat with Claude"));
        verify(chatMemoryRepository).saveAll(any(), any());
    }

    @Test
    void createAcceptsNoBody() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/conversations/web")).andExpect(status().isNoContent());
        verify(chatMemoryRepository).deleteByConversationId(eq("web"));
    }
}
