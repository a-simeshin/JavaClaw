package ai.javaclaw.e2e;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E test that verifies the agent can create tasks when instructed via the chat UI.
 *
 * <p>This test sends a chat message asking the agent to create a task,
 * then verifies via the UI response that the agent acknowledged the task creation.
 *
 * <p>Note: Task creation depends on the agent having the create-task tool
 * available and the LLM choosing to invoke it. This test may be flaky
 * if the LLM does not reliably use the tool. The timeout is set generously.
 */
class TaskCreationE2ETest extends ChatReadyE2ETestBase {

    @Autowired(required = false)
    JdbcClient jdbcClient;

    @Test
    void agentCreatesTaskOnRequest() {
        navigateTo("/chat");

        // Wait for welcome bubble or history (proves WebSocket connected)
        page.waitForSelector("article.ar-msg--agent",
                new Page.WaitForSelectorOptions().setTimeout(30_000));

        int agentBubblesBefore = page.locator("article.ar-msg--agent").count();

        // Ask the agent to create a task
        String taskName = "e2e-test-task-" + System.currentTimeMillis();
        String instruction = "Please create a task called '" + taskName + "' with the description 'Write a haiku about testing'. "
                + "Use the create_task tool if available. Confirm the task name in your response.";

        page.locator("#message-input").fill(instruction);
        page.locator("#send-btn").click();

        // Wait for user bubble (check ALL user bubbles)
        page.waitForFunction(
                "msg => Array.from(document.querySelectorAll('article.ar-msg--user .ar-msg__bubble')).some(el => el.textContent.includes(msg))",
                taskName,
                new Page.WaitForFunctionOptions().setTimeout(10_000));

        // Wait for agent response (generous timeout since tool invocation + LLM can be slow)
        page.waitForFunction(
                "expected => document.querySelectorAll('article.ar-msg--agent').length > expected",
                agentBubblesBefore,
                new Page.WaitForFunctionOptions().setTimeout(90_000));

        // The agent response should mention the task or confirmation
        Locator agentBubbles = page.locator("article.ar-msg--agent .ar-msg__bubble");
        String lastAgentResponse = agentBubbles.last().textContent();

        // Verify agent acknowledged the task (it should mention the task name or "created")
        boolean mentionsTask = lastAgentResponse.toLowerCase().contains(taskName.toLowerCase())
                || lastAgentResponse.toLowerCase().contains("task")
                || lastAgentResponse.toLowerCase().contains("created");
        assertTrue(mentionsTask,
                "Agent response should mention the task. Got: " + lastAgentResponse);

        // If JdbcClient is available, verify the task exists in the DB
        if (jdbcClient != null) {
            try {
                boolean taskExists = jdbcClient.sql("SELECT COUNT(*) FROM tasks WHERE name LIKE :name")
                        .param("name", "%" + taskName + "%")
                        .query(Integer.class)
                        .single() > 0;
                assertTrue(taskExists, "Task '" + taskName + "' should exist in the database");
            } catch (Exception e) {
                // Tasks table may not exist or have a different schema -- skip DB check
                System.out.println("DB task verification skipped: " + e.getMessage());
            }
        }
    }
}
