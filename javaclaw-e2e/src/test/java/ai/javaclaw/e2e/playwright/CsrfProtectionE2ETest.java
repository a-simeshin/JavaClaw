package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies CSRF protection: authenticated state-changing requests that omit
 * the {@code X-XSRF-TOKEN} header must be rejected with 403.
 */
@Tag("e2e")
class CsrfProtectionE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("POST /api/conversations without X-XSRF-TOKEN → 403")
    void stateChangingRequestWithoutCsrfToken_isRejected() {
        loginViaApi("e2e-user", "e2e-password");

        // Run a same-origin fetch from the page context, bypassing any CSRF
        // helper that the SPA would normally inject. We deliberately do NOT
        // send X-XSRF-TOKEN and expect the server to reject with 403.
        Object resultObj = page.evaluate("async () => {"
                + "  const r = await fetch('/api/conversations', {"
                + "    method: 'POST',"
                + "    credentials: 'include',"
                + "    headers: {'Content-Type': 'application/json'},"
                + "    body: JSON.stringify({title: 'csrf-test'})"
                + "  });"
                + "  return {status: r.status};"
                + "}");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultObj;
        int status = ((Number) result.get("status")).intValue();

        assertThat(status)
                .as("state-changing POST without X-XSRF-TOKEN must be rejected with 403")
                .isEqualTo(403);
    }
}
