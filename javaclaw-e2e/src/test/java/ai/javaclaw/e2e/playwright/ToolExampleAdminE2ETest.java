package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.IntegrationTestBase;
import ai.javaclaw.e2e.support.JdbcAssertions;
import ai.javaclaw.e2e.support.RestTestClient;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Verifies admin can CRUD tool examples via REST (bug #23). */
class ToolExampleAdminE2ETest extends IntegrationTestBase {

    private static final String MARKER = "E2E-MARKER-42";

    @LocalServerPort
    int port;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanup() {
        new JdbcAssertions(dataSource).cleanupToolExamplesByMarker(MARKER);
    }

    @Test
    @DisplayName("Admin creates tool example → row appears in tool_examples")
    void adminCreatesToolExampleAndRowAppears() {
        RestTestClient admin = new RestTestClient("http://localhost:" + port, "admin", "admin");
        String body = "{\"toolName\":\"test\","
                + "\"ownerId\":null,"
                + "\"exampleOrder\":0,"
                + "\"userMessage\":\"" + MARKER + "\","
                + "\"assistantMessage\":\"x\","
                + "\"toolCall\":\"test()\","
                + "\"toolResult\":\"ok\"}";
        HttpResponse<String> res = admin.post("/api/tool-examples", body);
        assertThat(res.statusCode()).isBetween(200, 299);

        new JdbcAssertions(dataSource).assertToolExampleContainsMarker(MARKER);

        HttpResponse<String> list = admin.get("/api/tool-examples");
        assertThat(list.body()).contains(MARKER);
    }
}
