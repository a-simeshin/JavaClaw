package ai.javaclaw.e2e;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E test that verifies chat messages are persisted to the shared database
 * and would be visible across multiple application instances.
 *
 * <p>Rather than starting two full Spring Boot instances, this test takes a practical approach:
 * <ol>
 *   <li>Send a message via the browser on the test's app instance</li>
 *   <li>Verify the message and response are stored in the shared PostgreSQL database</li>
 *   <li>This proves that any second instance sharing the same database would see the data</li>
 * </ol>
 */
class MultiInstanceE2ETest extends ChatReadyE2ETestBase {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void chatMessagePersistedToSharedDatabase() {
        navigateTo("/chat");

        // Wait for welcome bubble or history (proves WebSocket connected)
        page.waitForSelector("article.ar-msg--agent",
                new Page.WaitForSelectorOptions().setTimeout(30_000));

        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        // Send a distinctive message
        String uniqueMessage = "MULTI_INSTANCE_E2E_" + System.currentTimeMillis();
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

        // Verify the message was persisted to the shared PostgreSQL database.
        // Spring AI JDBC chat memory stores messages in the spring_ai_chat_memory table.
        boolean messageExists = false;
        for (String table : new String[]{"spring_ai_chat_memory", "chat_memory", "messages", "ai_chat_memory"}) {
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
        assertTrue(messageExists,
                "User message should be persisted in the shared database for multi-instance support.");
    }
}
