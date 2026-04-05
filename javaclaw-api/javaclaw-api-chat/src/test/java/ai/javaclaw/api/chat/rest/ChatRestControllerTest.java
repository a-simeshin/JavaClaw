package ai.javaclaw.api.chat.rest;

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

import ai.javaclaw.conversations.ConversationEnsurer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

class ChatRestControllerTest {

    private SseStreamingService streamingService;
    private ConversationEnsurer conversationEnsurer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        streamingService = mock(SseStreamingService.class);
        conversationEnsurer = mock(ConversationEnsurer.class);
        mockMvc = standaloneSetup(new ChatRestController(streamingService, conversationEnsurer))
                .setControllerAdvice(new SseExceptionHandler())
                .build();
    }

    @Test
    void postSendReturnsOkWithVercelHeader() throws Exception {
        when(streamingService.createEmitter()).thenReturn(new ResponseBodyEmitter(5000L));
        doNothing().when(streamingService).stream(any(), anyString(), anyString());

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\",\"conversationId\":\"web\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("x-vercel-ai-data-stream", "v1"))
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"));

        verify(streamingService).stream(any(), anyString(), anyString());
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

        verify(streamingService).stream(any(), anyString(), anyString());
    }

    @Test
    void postSendReturnsOkForShortContent() throws Exception {
        when(streamingService.createEmitter()).thenReturn(new ResponseBodyEmitter(5000L));

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isOk());
    }
}
