package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Simulates two "devices" (two independent {@link BrowserContext}s). When the
 * first device calls {@code POST /api/auth/logout-all}, the second device's
 * session must be invalidated too.
 */
@Tag("e2e")
class MultiDeviceLogoutE2ETest extends PlaywrightE2ETestBase {

    @Test
    @DisplayName("logout-all from device A invalidates device B's session")
    void logoutAll_fromOneDevice_invalidatesOtherSessions() {
        // Device A = the inherited `context` / `page`
        loginOnContext(context, page, "e2e-user", "e2e-password");

        // Device B — separate context
        Browser browser = context.browser();
        BrowserContext deviceB = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(1920, 1080).setLocale("en-US"));
        Page pageB = deviceB.newPage();
        pageB.onDialog(d -> {
            throw new AssertionError("Unexpected native dialog on device B: " + d.message());
        });

        try {
            loginOnContext(deviceB, pageB, "e2e-user", "e2e-password");

            // Sanity check: device B is authenticated
            APIResponse meBefore = deviceB.request().get(baseUrl() + "/api/auth/me");
            assertThat(meBefore.status())
                    .as("device B must be authenticated before logout-all")
                    .isEqualTo(200);

            // Device A triggers logout-all
            APIResponse logoutAll = context.request()
                    .post(
                            baseUrl() + "/api/auth/logout-all",
                            RequestOptions.create().setData(Map.of()));
            assertThat(logoutAll.status())
                    .as("POST /api/auth/logout-all must return 204")
                    .isEqualTo(204);

            // Device B's /api/auth/me should now return 401
            APIResponse meAfter = deviceB.request().get(baseUrl() + "/api/auth/me");
            assertThat(meAfter.status())
                    .as("device B must be unauthenticated after logout-all")
                    .isEqualTo(401);

            // And navigation to /chat should redirect to /login
            pageB.navigate(baseUrl() + "/chat");
            pageB.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(pageB.url())
                    .as("device B must be redirected to /login after logout-all")
                    .contains("/login");
        } finally {
            deviceB.close();
        }
    }

    private void loginOnContext(BrowserContext ctx, Page p, String username, String password) {
        APIResponse response = ctx.request()
                .post(
                        baseUrl() + "/api/auth/login",
                        RequestOptions.create()
                                .setHeader("Content-Type", "application/json")
                                .setData(Map.of("username", username, "password", password)));
        if (response.status() != 200) {
            throw new RuntimeException("Login failed: " + response.status() + " " + response.text());
        }
        p.navigate(baseUrl() + "/chat");
        p.waitForLoadState(LoadState.NETWORKIDLE);
    }

    @SuppressWarnings("unused")
    private static List<String> unused() {
        return List.of();
    }
}
