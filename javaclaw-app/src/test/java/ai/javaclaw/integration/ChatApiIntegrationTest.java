package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the Chat SSE streaming API.
 *
 * <p>Verifies that {@code POST /api/chat/send} returns an SSE stream with
 * the Vercel AI SDK {@code x-vercel-ai-data-stream: v1} header. Since the
 * LLM backend is unreachable in test (localhost:9999), the stream content will be
 * empty or error — we verify the response shape and headers.
 *
 * <p>Full end-to-end SSE content verification requires a running LLM backend and
 * is covered by the E2E test suite.
 */
class ChatApiIntegrationTest extends IntegrationTestBase {

    @Test
    void sendChat_returnsSseWithVercelHeader() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"ping\",\"conversationId\":\"integ-chat-1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // Verify the Vercel AI SDK header
        String vercelHeader = result.getResponse().getHeader("x-vercel-ai-data-stream");
        assertThat(vercelHeader).as("Vercel AI stream header").isEqualTo("v1");

        // Verify content type is set for streaming
        String contentType = result.getResponse().getContentType();
        assertThat(contentType).as("SSE content type").isNotNull();
    }

    @Test
    void sendChat_withBlankContent_returns400() throws Exception {
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendChat_withMissingContent_returns400() throws Exception {
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendChat_withoutConversationId_stillReturns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"test message\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("x-vercel-ai-data-stream")).isEqualTo("v1");
    }
}
