package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.agent.audit.ChatAuditService;
import ai.javaclaw.e2e.support.IntegrationTestBase;
import ai.javaclaw.e2e.support.JdbcAssertions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies chat_audit_log.user_id is populated when {@link ChatAuditService#log} is
 * invoked with a non-null userId (bug #25).
 *
 * <p>Calls the audit service directly so the test is deterministic and does not
 * depend on a real LLM round-trip.
 */
class ChatAuditUserIdE2ETest extends IntegrationTestBase {

    @Autowired
    private ChatAuditService chatAuditService;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("ChatAuditService.log writes user_id into chat_audit_log")
    void chatAuditServiceWritesUserId() throws Exception {
        JdbcAssertions jdbc = new JdbcAssertions(dataSource);
        jdbc.assertAuditUserIdColumnExists();

        String cid = "audit-user-id-" + System.nanoTime();
        String userId = "test-user-42";
        Prompt prompt = new Prompt(List.of(new UserMessage("hello")));
        chatAuditService.log(cid, "stream", prompt, "world", 123L, userId, null, null);

        // Give async logger a moment; then poll directly
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT user_id FROM chat_audit_log WHERE conversation_id = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, cid);
            String actual = null;
            for (int i = 0; i < 40 && actual == null; i++) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        actual = rs.getString("user_id");
                    }
                }
                if (actual == null) {
                    Thread.sleep(50);
                }
            }
            assertThat(actual).as("user_id for conversation %s", cid).isEqualTo(userId);
        }
    }
}
