package ai.javaclaw.e2e.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.Agent;
import ai.javaclaw.e2e.support.LiveTestBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Live integration tests for {@link Agent#respondTo} using a real LLM
 * (OpenRouter) and a PostgreSQL Testcontainer.
 */
class AgentLiveE2ETest extends LiveTestBase {

    @Autowired
    Agent agent;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void respondToReturnsNonEmptyAnswer() {
        String answer = agent.respondTo("live-test-basic", "What is 2+2?");
        assertThat(answer).isNotBlank();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void chatMemoryPersistsAcrossQuestions() {
        String conversationId = "live-test-memory-" + System.nanoTime();
        agent.respondTo(conversationId, "My name is TestBot. Remember it.");
        String answer = agent.respondTo(conversationId, "What is my name?");
        assertThat(answer).containsIgnoringCase("TestBot");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void chatMemorySavedInPostgreSQL() {
        String conversationId = "live-test-db-" + System.nanoTime();
        agent.respondTo(conversationId, "Hello from the live test!");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_ai_chat_memory WHERE conversation_id = ?", Integer.class, conversationId);

        assertThat(count).isGreaterThan(0);
    }
}
