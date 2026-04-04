package ai.javaclaw.e2e.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * E2E test for switching between conversations in the chat UI.
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class ConversationSwitchingE2ETest extends PlaywrightE2ETestBase {

    @Test
    void switchBetweenConversationsPreservesHistory() {
        navigateTo("/chat");

        page.waitForSelector("article.ar-msg--agent", new Page.WaitForSelectorOptions().setTimeout(30_000));

        Locator channelSelector = page.locator("#channel-select");
        channelSelector.waitFor(new Locator.WaitForOptions().setTimeout(10_000));

        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        String webMessage = "E2E_WEB_CONV_" + System.currentTimeMillis();
        page.locator("#message-input").fill(webMessage);
        page.locator("#send-btn").click();

        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                webMessage,
                new Page.WaitForFunctionOptions().setTimeout(10_000));
        page.waitForFunction(
                "expected => document.querySelectorAll('article.ar-msg--agent').length > expected",
                agentBubblesBefore,
                new Page.WaitForFunctionOptions().setTimeout(60_000));

        Locator options = page.locator("#channel-select option");
        int optionCount = options.count();
        if (optionCount > 1) {
            String secondConvId = options.nth(1).getAttribute("value");
            page.locator("#channel-select").selectOption(secondConvId);
            page.waitForTimeout(1_000);

            Locator allBubbles = page.locator("article.ar-msg--user .ar-msg__bubble");
            boolean foundWebMessage = false;
            for (int i = 0; i < allBubbles.count(); i++) {
                if (allBubbles.nth(i).textContent().contains(webMessage)) {
                    foundWebMessage = true;
                    break;
                }
            }
            org.junit.jupiter.api.Assertions.assertFalse(
                    foundWebMessage, "Web conversation message should not appear in a different conversation");

            page.locator("#channel-select").selectOption("web");
            page.waitForTimeout(1_000);

            Locator webBubbles = page.locator("article.ar-msg--user .ar-msg__bubble");
            boolean foundAfterSwitch = false;
            for (int i = 0; i < webBubbles.count(); i++) {
                if (webBubbles.nth(i).textContent().contains(webMessage)) {
                    foundAfterSwitch = true;
                    break;
                }
            }
            org.junit.jupiter.api.Assertions.assertTrue(
                    foundAfterSwitch, "Web conversation message should be present after switching back");
        } else {
            assertThat(channelSelector).containsText("Web Chat");
        }
    }
}
