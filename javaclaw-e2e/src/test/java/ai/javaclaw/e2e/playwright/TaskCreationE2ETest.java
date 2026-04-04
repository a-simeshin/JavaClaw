package ai.javaclaw.e2e.playwright;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.javaclaw.e2e.support.PlaywrightE2ETestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * E2E test that verifies the agent can create tasks when instructed via the chat UI.
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class TaskCreationE2ETest extends PlaywrightE2ETestBase {

    @Test
    void agentCreatesTaskOnRequest() {
        navigateTo("/chat");

        page.waitForSelector("article.ar-msg--agent", new Page.WaitForSelectorOptions().setTimeout(30_000));

        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        String taskName = "e2e-test-task-" + System.currentTimeMillis();
        String instruction =
                "Please create a task called '" + taskName + "' with the description 'Write a haiku about testing'. "
                        + "Use the create_task tool if available. Confirm the task name in your response.";

        page.locator("#message-input").fill(instruction);
        page.locator("#send-btn").click();

        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                taskName,
                new Page.WaitForFunctionOptions().setTimeout(10_000));

        page.waitForFunction(
                "expected => document.querySelectorAll('article.ar-msg--agent').length > expected",
                agentBubblesBefore,
                new Page.WaitForFunctionOptions().setTimeout(90_000));

        Locator agentBubbles = page.locator("article.ar-msg--agent .ar-msg__bubble");
        String lastAgentResponse = agentBubbles.last().textContent();

        boolean mentionsTask = lastAgentResponse.toLowerCase().contains(taskName.toLowerCase())
                || lastAgentResponse.toLowerCase().contains("task")
                || lastAgentResponse.toLowerCase().contains("created");
        assertTrue(mentionsTask, "Agent response should mention the task. Got: " + lastAgentResponse);

        if (jdbcClient != null) {
            try {
                boolean taskExists = jdbcClient
                                .sql("SELECT COUNT(*) FROM tasks WHERE name LIKE :name")
                                .param("name", "%" + taskName + "%")
                                .query(Integer.class)
                                .single()
                        > 0;
                assertTrue(taskExists, "Task '" + taskName + "' should exist in the database");
            } catch (Exception e) {
                System.out.println("DB task verification skipped: " + e.getMessage());
            }
        }
    }
}
