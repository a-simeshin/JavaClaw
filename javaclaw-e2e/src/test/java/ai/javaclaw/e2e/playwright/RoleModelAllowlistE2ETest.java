package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.IntegrationTestBase;
import ai.javaclaw.e2e.support.RestTestClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Verifies Role-based model allowlist enforcement end-to-end (bugs #19, #20).
 *
 * <p>The allowlist check in {@code ChatRestController} runs BEFORE any LLM call, so we
 * can assert HTTP 403 without needing a real LLM.
 */
class RoleModelAllowlistE2ETest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    private RestTestClient admin;
    private RestTestClient user;

    @BeforeEach
    void setupClients() {
        String base = "http://localhost:" + port;
        admin = new RestTestClient(base, "admin", "admin");
        user = new RestTestClient(base, "user", "user");
    }

    @AfterEach
    void resetAllowlist() {
        admin.put("/api/roles/USER/allowed-models", "{\"modelIds\":[]}");
    }

    @Test
    @DisplayName("Admin sets USER allowlist excluding default → USER chat returns 403")
    void adminSetsUserAllowlistExcludingDefault_thenUserGets403() {
        HttpResponse<String> add =
                admin.put("/api/roles/USER/allowed-models", "{\"modelIds\":[\"fake/not-the-default\"]}");
        assertThat(add.statusCode()).isBetween(200, 299);

        HttpResponse<String> verify = admin.get("/api/roles/USER/allowed-models");
        assertThat(verify.body()).as("USER allowlist after PUT").contains("fake/not-the-default");

        HttpResponse<String> chat =
                user.post("/api/chat/send", "{\"content\":\"hi\",\"conversationId\":\"allowlist-block\"}");
        assertThat(chat.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Empty allowlist: chat request is NOT blocked by allowlist (not 403)")
    void emptyAllowlistAllowsAllModels() {
        admin.put("/api/roles/USER/allowed-models", "{\"modelIds\":[]}");

        HttpResponse<String> chat =
                user.post("/api/chat/send", "{\"content\":\"hi\",\"conversationId\":\"allowlist-open\"}");
        assertThat(chat.statusCode()).isNotEqualTo(403);
    }
}
