package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a user whose session cookie has been cleared (simulating
 * expiry) is redirected to /login on the next protected-route navigation.
 */
@Tag("e2e")
class SessionExpiredE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("expired session → protected route redirects to /login")
    void clearedCookies_redirectsToLoginOnProtectedRoute() {
        loginViaApi("e2e-user", "e2e-password");

        // Sanity: after login we're on /chat
        assertThat(page.url()).contains("/chat");

        // Simulate session expiry by nuking all cookies
        context.clearCookies();

        // Attempt to reach a protected route
        page.navigate(baseUrl() + "/chat");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.url())
                .as("expired session must redirect /chat → /login")
                .contains("/login");
    }
}
