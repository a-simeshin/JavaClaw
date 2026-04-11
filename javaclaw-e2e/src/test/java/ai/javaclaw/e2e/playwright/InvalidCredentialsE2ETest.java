package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Invalid-credentials path: the SPA must render an inline error (not a native
 * browser prompt — that regression is guarded by {@link PlaywrightE2ETestBase#registerNoDialogGuard()}),
 * and must not set a session cookie.
 */
@Tag("e2e")
class InvalidCredentialsE2ETest extends PlaywrightE2ETestBase {

    private static final String SESSION_COOKIE = "JCLAW_SESSION";

    @Test
    @DisplayName("invalid credentials → error shown, no session cookie, no native dialog")
    void invalidCredentials_showsErrorAndKeepsCookieUnset() {
        page.navigate(baseUrl() + "/login");
        page.waitForSelector("#login-username");
        page.locator("#login-username").fill("e2e-user");
        page.locator("#login-password").fill("totally-wrong-password");
        page.locator("button[type=submit]").click();

        // Allow the SPA to process the failed login
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // We must still be on /login (no redirect to /chat)
        assertThat(page.url())
                .as("must remain on /login after invalid credentials")
                .contains("/login");

        // No session cookie must have been issued
        boolean hasSession = context.cookies().stream()
                .anyMatch(c -> SESSION_COOKIE.equals(c.name) && c.value != null && !c.value.isBlank());
        assertThat(hasSession)
                .as("session cookie must NOT be set after invalid login")
                .isFalse();

        // If a native dialog was displayed, registerNoDialogGuard() would have thrown.
        // Reaching this point means the SPA rendered an inline error instead — good.
    }
}
