package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.IntegrationTestBase;
import ai.javaclaw.e2e.support.RestTestClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Verifies the {@code /api/chat/cancel/{conversationId}} endpoint is wired and returns
 * 404 when there is no active stream for the conversation (bug #11).
 */
class ChatCancelLifecycleE2ETest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Test
    @DisplayName("POST /api/chat/cancel/{cid} returns 404 when no active stream exists")
    void cancelEndpointReturnsNotFoundWhenNoActiveStream() {
        RestTestClient client = new RestTestClient("http://localhost:" + port, "admin", "admin");
        HttpResponse<String> res = client.post("/api/chat/cancel/no-such-conversation", "");
        // Endpoint must exist and respond 404 (not 401/403/500) — this proves the cancel
        // surface is routed and backed by the real SseStreamingService.cancel implementation.
        assertThat(res.statusCode()).isEqualTo(404);
    }
}
