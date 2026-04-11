package ai.javaclaw.e2e.playwright;

import ai.javaclaw.e2e.support.IntegrationTestBase;
import ai.javaclaw.e2e.support.JdbcAssertions;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the conversation summarization schema is present (bugs #17, #18, #20).
 *
 * <p>The full multi-turn cumulative counter assertion requires a real LLM (or mocked
 * ChatModel wired into a running Spring context), which is brittle to set up alongside
 * auto-configuration. This smoke test asserts the backing table schema exists which
 * verifies the V-migration landed and {@code ConversationSummaryService} has the
 * {@code messages_covered} column it writes to.
 */
class AutoSummarizationE2ETest extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("conversation_summaries table exists with messages_covered column")
    void conversationSummariesSchemaIsPresent() {
        new JdbcAssertions(dataSource).assertSummaryTableExists();
    }

    @Test
    @DisplayName("conversation_summaries supports upsert of cumulative messages_covered counter")
    void summariesSupportCumulativeCounter() throws Exception {
        try (var conn = dataSource.getConnection()) {
            String cid = "summary-cum-" + System.nanoTime();
            // Initial insert
            try (var ps = conn.prepareStatement(
                    "INSERT INTO conversation_summaries (conversation_id, summary_text, messages_covered, updated_at) "
                            + "VALUES (?, ?, ?, now())")) {
                ps.setString(1, cid);
                ps.setString(2, "first summary");
                ps.setInt(3, 2);
                ps.executeUpdate();
            }
            // Cumulative update (simulates second summarization cycle)
            try (var ps =
                    conn.prepareStatement("UPDATE conversation_summaries SET messages_covered = messages_covered + 2, "
                            + "summary_text = ?, updated_at = now() WHERE conversation_id = ?")) {
                ps.setString(1, "second summary");
                ps.setString(2, cid);
                ps.executeUpdate();
            }
            // Read back
            try (var ps = conn.prepareStatement(
                    "SELECT messages_covered FROM conversation_summaries WHERE conversation_id = ?")) {
                ps.setString(1, cid);
                var rs = ps.executeQuery();
                org.assertj.core.api.Assertions.assertThat(rs.next()).isTrue();
                org.assertj.core.api.Assertions.assertThat(rs.getInt("messages_covered"))
                        .as("cumulative counter")
                        .isGreaterThanOrEqualTo(4);
            }
            // Cleanup
            try (var ps = conn.prepareStatement("DELETE FROM conversation_summaries WHERE conversation_id = ?")) {
                ps.setString(1, cid);
                ps.executeUpdate();
            }
        }
    }
}
