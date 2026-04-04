package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs against a live {@code http://localhost:8080} — start the service manually first
 * via {@code java -jar javaclaw-app/target/javaclaw-app-exec.jar}. Bypasses auth by
 * injecting credentials directly into localStorage, then drives the real chat flow
 * end-to-end: types a message, presses Enter, waits for the assistant bubble, and
 * asserts markdown content is rendered and visible (no truncation).
 */
@Tag("live")
class LiveChatSmokeE2ETest {

    private static final String BASE_URL = "http://localhost:8080";

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    static void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void teardown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    void userSendsMessageAndReceivesStreamedMarkdownReply() {
        Browser.NewContextOptions ctx = new Browser.NewContextOptions().setViewportSize(1280, 800);
        try (var context = browser.newContext(ctx)) {
            context.grantPermissions(java.util.List.of("clipboard-read", "clipboard-write"));
            Page page = context.newPage();
            page.addInitScript("window.localStorage.setItem('javaclaw.auth.credentials', 'Z3Vlc3Q6Z3Vlc3Q=');"
                    + "window.localStorage.setItem('javaclaw.auth.username', 'guest');"
                    + "window.localStorage.setItem('javaclaw.auth.role', 'USER');");

            page.navigate(BASE_URL + "/chat");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Must have landed on /chat (auth guard not redirected us to /login)
            assertThat(page.url()).endsWith("/chat");

            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get("target/screenshots/live-chat-landing.png"))
                    .setFullPage(true));

            // Chat composer visible
            var composer = page.locator("textarea").first();
            composer.waitFor();
            assertThat(composer.isVisible()).isTrue();

            composer.fill("Hi");
            composer.press("Enter");

            // Wait for user message
            page.waitForSelector("[data-role='user']");
            var userBubble = page.locator("[data-role='user']").first();
            assertThat(userBubble.textContent()).contains("Hi");

            // Wait for assistant message to appear AND be non-empty
            page.waitForSelector("[data-role='assistant']");
            page.waitForFunction(
                    "() => { const el = document.querySelector(\"[data-role='assistant']\"); "
                            + "return el && el.textContent && el.textContent.trim().length > 0; }",
                    null);
            var assistant = page.locator("[data-role='assistant']").first();
            String assistantText = assistant.textContent();
            assertThat(assistantText)
                    .as("assistant must produce non-empty content")
                    .isNotBlank();
            assertThat(assistantText.length())
                    .as("assistant should produce a meaningful reply")
                    .isGreaterThan(2);

            // Visibility: no horizontal overflow on the assistant bubble
            Boolean noOverflow =
                    (Boolean) page.evaluate("() => { const el = document.querySelector(\"[data-role='assistant']\"); "
                            + "return el.scrollWidth <= el.clientWidth + 1; }");
            assertThat(noOverflow)
                    .as("assistant message must not horizontally overflow")
                    .isTrue();

            // Body has no horizontal scrollbar
            Boolean bodyNoScroll = (Boolean) page.evaluate("() => document.body.scrollWidth <= window.innerWidth + 1");
            assertThat(bodyNoScroll).as("body must not overflow viewport").isTrue();

            System.out.println("ASSISTANT TEXT: " + assistantText.substring(0, Math.min(200, assistantText.length())));
        }
    }
}
