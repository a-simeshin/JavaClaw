package ai.javaclaw.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * E2E tests for the chat interface.
 * Assumes onboarding is already completed (via {@link ChatReadyE2ETestBase}).
 *
 * <p>The chat UI uses htmx WebSocket extension. Messages are sent via WebSocket
 * and responses are pushed as OOB HTML swaps into {@code #chat-messages}.
 */
class ChatE2ETest extends ChatReadyE2ETestBase {

    /**
     * Sends a simple message and verifies the user bubble appears,
     * followed by an agent response bubble within a reasonable timeout.
     */
    @Test
    void sendMessageAndReceiveResponse() {
        navigateTo("/chat");

        // Wait for welcome bubble (proves WebSocket is connected and OOB swap worked)
        page.waitForSelector("article.ar-msg--agent",
                new Page.WaitForSelectorOptions().setTimeout(30_000));

        // Count current agent bubbles before sending (there may be history from previous tests)
        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        // Type and send a message
        String testMessage = "Say exactly: E2E_TEST_REPLY_OK";
        Locator messageInput = page.locator("#message-input");
        messageInput.fill(testMessage);

        Locator sendButton = page.locator("#send-btn");
        sendButton.click();

        // Wait for user bubble containing our message (check ALL user bubbles, not just first)
        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                "E2E_TEST_REPLY_OK",
                new Page.WaitForFunctionOptions().setTimeout(10_000));

        // Wait for agent response: should have at least one more than before
        page.waitForFunction(
                "expected => document.querySelectorAll('article.ar-msg--agent').length > expected",
                agentBubblesBefore,
                new Page.WaitForFunctionOptions().setTimeout(60_000));

        // The last agent bubble should contain some text (the actual LLM response)
        Locator agentBubbles = page.locator("article.ar-msg--agent .ar-msg__bubble");
        assertThat(agentBubbles.last()).not().isEmpty();
    }

    /**
     * Verifies that chat history persists across page reloads.
     * Sends a message, waits for response, reloads, and checks that the
     * conversation is restored from the database.
     */
    @Test
    void chatHistoryPersistsOnReload() {
        navigateTo("/chat");

        // Wait for welcome bubble or history (proves WebSocket is connected)
        page.waitForSelector("article.ar-msg--agent",
                new Page.WaitForSelectorOptions().setTimeout(30_000));

        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        // Send a distinctive message
        String uniqueMessage = "E2E_PERSIST_CHECK_" + System.currentTimeMillis();
        page.locator("#message-input").fill(uniqueMessage);
        page.locator("#send-btn").click();

        // Wait for user bubble (check ALL user bubbles)
        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                uniqueMessage,
                new Page.WaitForFunctionOptions().setTimeout(10_000));

        // Wait for agent response
        page.waitForFunction(
                "expected => document.querySelectorAll('article.ar-msg--agent').length > expected",
                agentBubblesBefore,
                new Page.WaitForFunctionOptions().setTimeout(60_000));

        // Reload the page and wait for full network-idle load
        page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));

        // After reload: htmx re-initializes, WS reconnects, afterConnectionEstablished sends OOB history.
        // Wait for the user message to re-appear from persisted history (proves persistence works).
        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                uniqueMessage,
                new Page.WaitForFunctionOptions().setTimeout(30_000));

        // Verify at least one agent bubble is present (the LLM response from history)
        page.waitForSelector("article.ar-msg--agent",
                new Page.WaitForSelectorOptions().setTimeout(10_000));
    }
}
