package ai.javaclaw.api.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.agent.config.RoleAgentConfigService;
import ai.javaclaw.agent.quota.AgentQuotaService;
import ai.javaclaw.api.chat.error.SseExceptionHandler;
import ai.javaclaw.api.chat.service.SseStreamingService;
import ai.javaclaw.conversations.ConversationEnsurer;
import ai.javaclaw.tasks.RateLimitExceededException;
import ai.javaclaw.users.UserResolver;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

class ChatRestControllerTest {

    private SseStreamingService streamingService;
    private ConversationEnsurer conversationEnsurer;
    private UserResolver userResolver;
    private AgentQuotaService agentQuotaService;
    private RoleAgentConfigService roleAgentConfigService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        streamingService = mock(SseStreamingService.class);
        conversationEnsurer = mock(ConversationEnsurer.class);
        userResolver = mock(UserResolver.class);
        agentQuotaService = mock(AgentQuotaService.class);
        roleAgentConfigService = mock(RoleAgentConfigService.class);
        when(userResolver.resolveUserId("admin")).thenReturn("admin-uuid");
        when(userResolver.resolveUserRole("admin")).thenReturn("ADMIN");
        mockMvc = standaloneSetup(new ChatRestController(
                        streamingService, conversationEnsurer, userResolver, agentQuotaService, roleAgentConfigService))
                .defaultRequest(post("/").principal(adminPrincipal()))
                .setControllerAdvice(new SseExceptionHandler())
                .build();
    }

    private static Principal adminPrincipal() {
        return () -> "admin";
    }

    @Test
    void postSendReturnsOkWithVercelHeader() throws Exception {
        when(streamingService.createEmitter()).thenReturn(new ResponseBodyEmitter(5000L));
        doNothing().when(streamingService).stream(any(), anyString(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\",\"conversationId\":\"web\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("x-vercel-ai-data-stream", "v1"))
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"));

        verify(streamingService).stream(any(), anyString(), anyString(), anyString(), any());
        verify(conversationEnsurer).ensureExistsForUser("web", "admin-uuid");
        verify(conversationEnsurer).touch("web", "hello");
    }

    @Test
    void postSendReturnsTooManyRequestsWhenCapacityExhausted() throws Exception {
        when(streamingService.createEmitter()).thenReturn(null);

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void postSendGeneratesDefaultConversationIdWhenBlank() throws Exception {
        when(streamingService.createEmitter()).thenReturn(new ResponseBodyEmitter(5000L));

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isOk());

        verify(streamingService).stream(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void postSendReturnsOkForShortContent() throws Exception {
        when(streamingService.createEmitter()).thenReturn(new ResponseBodyEmitter(5000L));

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void postSendReturnsTooManyRequestsWhenQuotaExceeded() throws Exception {
        when(streamingService.createEmitter()).thenReturn(new ResponseBodyEmitter(5000L));
        org.mockito.Mockito.doThrow(new RateLimitExceededException("admin-uuid", "daily_agent_quota", 100, 100))
                .when(agentQuotaService)
                .checkAndIncrement("admin-uuid");

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\",\"conversationId\":\"web\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cancelReturnsOkWhenStreamActive() throws Exception {
        when(streamingService.cancel("conv-123")).thenReturn(true);

        mockMvc.perform(post("/api/chat/cancel/conv-123")).andExpect(status().isOk());

        verify(streamingService).cancel("conv-123");
    }

    @Test
    void cancelReturnsNotFoundWhenNoActiveStream() throws Exception {
        when(streamingService.cancel("conv-999")).thenReturn(false);

        mockMvc.perform(post("/api/chat/cancel/conv-999")).andExpect(status().isNotFound());
    }
}
