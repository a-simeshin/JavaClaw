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

/** Smoke test for /api/roles management surface — admin can list and create roles. */
class AdminRoleManagementE2ETest extends IntegrationTestBase {

    private static final String CUSTOM_ROLE = "E2E_CUSTOM";

    @LocalServerPort
    int port;

    private RestTestClient admin;

    @BeforeEach
    void setupClient() {
        admin = new RestTestClient("http://localhost:" + port, "admin", "admin");
    }

    @AfterEach
    void cleanup() {
        admin.delete("/api/roles/" + CUSTOM_ROLE);
    }

    @Test
    @DisplayName("Admin can list roles and default roles are present")
    void adminCanViewRoles() {
        HttpResponse<String> res = admin.get("/api/roles");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("ADMIN").contains("USER");
    }

    @Test
    @DisplayName("Admin can create a custom role")
    void adminCanCreateCustomRole() {
        admin.delete("/api/roles/" + CUSTOM_ROLE);

        String body = "{\"name\":\"" + CUSTOM_ROLE + "\",\"description\":\"e2e test\",\"permissions\":[]}";
        HttpResponse<String> res = admin.post("/api/roles", body);
        assertThat(res.statusCode()).isBetween(200, 299);

        HttpResponse<String> list = admin.get("/api/roles");
        assertThat(list.body()).contains(CUSTOM_ROLE);
    }
}
