package ai.javaclaw.e2e.playwright;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * E2E test that verifies chat messages are persisted to the shared database
 * (forward-compat proof for multi-instance deployments).
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class MultiInstanceE2ETest extends PlaywrightE2ETestBase {

    @Test
    void chatMessagePersistedToSharedDatabase() {
        navigateTo("/chat");

        page.waitForSelector("article.ar-msg--agent", new Page.WaitForSelectorOptions().setTimeout(30_000));

        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        String uniqueMessage = "MULTI_INSTANCE_E2E_" + System.currentTimeMillis();
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

        boolean messageExists = false;
        for (String table : new String[] {"spring_ai_chat_memory", "chat_memory", "messages", "ai_chat_memory"}) {
            try {
                int count = jdbcClient
                        .sql("SELECT COUNT(*) FROM " + table + " WHERE content LIKE :content")
                        .param("content", "%" + uniqueMessage + "%")
                        .query(Integer.class)
                        .single();
                if (count > 0) {
                    messageExists = true;
                    break;
                }
            } catch (Exception ignored) {
                // Table does not exist, try next
            }
        }
        assertTrue(
                messageExists, "User message should be persisted in the shared database for multi-instance support.");
    }
}
