package ai.javaclaw.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E test for switching between conversations in the chat UI.
 *
 * <p>The chat interface renders a {@code <select id="channel-select">} inside
 * {@code #channel-selector} that lists all known conversation IDs. Changing the
 * selection triggers a WebSocket message of type {@code channelChanged}, which
 * reloads the chat history.
 */
class ConversationSwitchingE2ETest extends ChatReadyE2ETestBase {

    /**
     * Verifies that the channel selector is present and the default "web" conversation
     * is loaded. If multiple conversations exist, verifies switching preserves history.
     */
    @Test
    void switchBetweenConversationsPreservesHistory() {
        navigateTo("/chat");

        // Wait for welcome bubble or history (proves WebSocket connected)
        page.waitForSelector("article.ar-msg--agent",
                new Page.WaitForSelectorOptions().setTimeout(30_000));

        // Verify the channel selector is present (populated via OOB swap on WS connect)
        Locator channelSelector = page.locator("#channel-select");
        channelSelector.waitFor(new Locator.WaitForOptions().setTimeout(10_000));

        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        // Send a message in the default conversation
        String webMessage = "E2E_WEB_CONV_" + System.currentTimeMillis();
        page.locator("#message-input").fill(webMessage);
        page.locator("#send-btn").click();

        // Wait for user bubble (check ALL user bubbles)
        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                webMessage,
                new Page.WaitForFunctionOptions().setTimeout(10_000));

        // Wait for agent response
        page.waitForFunction(
                "expected => document.querySelectorAll('article.ar-msg--agent').length > expected",
                agentBubblesBefore,
                new Page.WaitForFunctionOptions().setTimeout(60_000));

        // Check if there are multiple conversations available
        Locator options = page.locator("#channel-select option");
        int optionCount = options.count();

        if (optionCount > 1) {
            // Switch to the second conversation
            String secondConvId = options.nth(1).getAttribute("value");
            page.locator("#channel-select").selectOption(secondConvId);

            // Wait for the chat history to reload (messages area changes)
            page.waitForTimeout(1_000);

            // The web message should NOT be visible in the other conversation
            Locator allBubbles = page.locator("article.ar-msg--user .ar-msg__bubble");
            boolean foundWebMessage = false;
            for (int i = 0; i < allBubbles.count(); i++) {
                if (allBubbles.nth(i).textContent().contains(webMessage)) {
                    foundWebMessage = true;
                    break;
                }
            }
            org.junit.jupiter.api.Assertions.assertFalse(foundWebMessage,
                    "Web conversation message should not appear in a different conversation");

            // Switch back to "web"
            page.locator("#channel-select").selectOption("web");
            page.waitForTimeout(1_000);

            // Verify the original message is back (check ALL user bubbles)
            Locator webBubbles = page.locator("article.ar-msg--user .ar-msg__bubble");
            boolean foundAfterSwitch = false;
            for (int i = 0; i < webBubbles.count(); i++) {
                if (webBubbles.nth(i).textContent().contains(webMessage)) {
                    foundAfterSwitch = true;
                    break;
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(foundAfterSwitch,
                    "Web conversation message should be present after switching back");
        } else {
            // Only one conversation -- just verify the selector shows "Web Chat"
            assertThat(channelSelector).containsText("Web Chat");
        }
    }
}
