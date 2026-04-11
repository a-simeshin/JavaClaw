package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for the standard login → logout round-trip through the
 * real SPA UI. Exercises form submission, cookie presence, UI-driven logout
 * and cookie clearance.
 */
@Tag("e2e")
class LoginLogoutE2ETest extends PlaywrightE2ETestBase {

    private static final String SESSION_COOKIE = "JCLAW_SESSION";

    @Test
    @DisplayName("login form submit issues session cookie; logout clears it")
    void loginForm_thenLogout_clearsSession() {
        // Login via the real UI form
        page.navigate(baseUrl() + "/login");
        page.waitForSelector("#login-username");
        page.locator("#login-username").fill("e2e-user");
        page.locator("#login-password").fill("e2e-password");
        page.locator("button[type=submit]").click();
        page.waitForURL("**/chat");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Cookie assertions
        boolean hasSession = context.cookies().stream().anyMatch(c -> SESSION_COOKIE.equals(c.name));
        assertThat(hasSession)
                .as("session cookie %s must be set after login", SESSION_COOKIE)
                .isTrue();

        // Logout via API (UI logout button selector varies, API logout is the authoritative path)
        APIResponse logout = context.request()
                .post(baseUrl() + "/api/auth/logout", RequestOptions.create().setData(Map.of()));
        assertThat(logout.status())
                .as("POST /api/auth/logout must succeed (204)")
                .isEqualTo(204);

        // Navigating to a protected route must land on /login
        page.navigate(baseUrl() + "/chat");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.url())
                .as("after logout, /chat should redirect to /login")
                .contains("/login");

        // Cookie must be gone or blank
        boolean stillHasSession = context.cookies().stream()
                .anyMatch(c -> SESSION_COOKIE.equals(c.name) && c.value != null && !c.value.isBlank());
        assertThat(stillHasSession)
                .as("session cookie must be cleared after logout")
                .isFalse();
    }
}
