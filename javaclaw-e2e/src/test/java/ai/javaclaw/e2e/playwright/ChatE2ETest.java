package ai.javaclaw.e2e.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * E2E tests for the chat interface. Sends a message and verifies that both
 * the user bubble and an agent response bubble appear.
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class ChatE2ETest extends PlaywrightE2ETestBase {

    @Test
    void sendMessageAndReceiveResponse() {
        navigateTo("/chat");

        page.waitForSelector("article.ar-msg--agent", new Page.WaitForSelectorOptions().setTimeout(30_000));

        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        String testMessage = "Say exactly: E2E_TEST_REPLY_OK";
        page.locator("#message-input").fill(testMessage);
        page.locator("#send-btn").click();

        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                "E2E_TEST_REPLY_OK",
                new Page.WaitForFunctionOptions().setTimeout(10_000));

        page.waitForFunction(
                "expected => document.querySelectorAll('article.ar-msg--agent').length > expected",
                agentBubblesBefore,
                new Page.WaitForFunctionOptions().setTimeout(60_000));

        Locator agentBubbles = page.locator("article.ar-msg--agent .ar-msg__bubble");
        assertThat(agentBubbles.last()).not().isEmpty();
    }

    @Test
    void chatHistoryPersistsOnReload() {
        navigateTo("/chat");

        page.waitForSelector("article.ar-msg--agent", new Page.WaitForSelectorOptions().setTimeout(30_000));
        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        String uniqueMessage = "E2E_PERSIST_CHECK_" + System.currentTimeMillis();
        page.locator("#message-input").fill(uniqueMessage);
        page.locator("#send-btn").click();

        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                uniqueMessage,
                new Page.WaitForFunctionOptions().setTimeout(10_000));
        page.waitForFunction(
                "expected => document.querySelectorAll('article.ar-msg--agent').length > expected",
                agentBubblesBefore,
                new Page.WaitForFunctionOptions().setTimeout(60_000));

        page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));

        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                uniqueMessage,
                new Page.WaitForFunctionOptions().setTimeout(30_000));

        page.waitForSelector("article.ar-msg--agent", new Page.WaitForSelectorOptions().setTimeout(10_000));
    }
}
