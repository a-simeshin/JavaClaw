package ai.javaclaw.e2e.playwright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies chat audit integrity (bugs #24, #26): the unified
 * {@link ChatAuditService#log} method populates token_usage when a non-null {@link Usage}
 * is provided, and history is serialized without duplicated consecutive USER blocks.
 */
class ChatAuditIntegrityE2ETest extends IntegrationTestBase {

    @Autowired
    private ChatAuditService chatAuditService;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Audit history does not contain duplicate consecutive USER messages")
    void auditHistoryHasNoDuplicateUserMessages() throws Exception {
        String cid = "audit-nodup-" + System.nanoTime();
        Prompt prompt = new Prompt(List.of(new UserMessage("hello world")));
        chatAuditService.log(cid, "stream", prompt, "ok", 100L, "u1", null, null);

        waitForAuditRow(cid);
        new JdbcAssertions(dataSource).assertAuditUserIdColumnExists(); // sanity: schema side-effect of fix is present

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT history FROM chat_audit_log WHERE conversation_id = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, cid);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                String history = rs.getString("history");
                if (history != null) {
                    String[] parts = history.split("\\[USER\\]");
                    for (int i = 1; i < parts.length - 1; i++) {
                        assertThat(parts[i].trim())
                                .as("no duplicate consecutive USER block")
                                .isNotEqualTo(parts[i + 1].trim());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Audit token_usage column is populated when Usage metadata is provided")
    void auditTokenUsageIsPopulated() throws Exception {
        new JdbcAssertions(dataSource).assertAuditTokenUsageColumnExists();

        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(usage.getTotalTokens()).thenReturn(15);

        String cid = "audit-usage-" + System.nanoTime();
        Prompt prompt = new Prompt(List.of(new UserMessage("token check")));
        chatAuditService.log(cid, "stream", prompt, "ok", 100L, "u1", usage, null);

        waitForAuditRow(cid);
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT token_usage FROM chat_audit_log "
                        + "WHERE conversation_id = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, cid);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                String tu = rs.getString("token_usage");
                assertThat(tu)
                        .as("token_usage column")
                        .isNotNull()
                        .contains("totalTokens")
                        .contains("15");
            }
        }
    }

    private void waitForAuditRow(String conversationId) throws Exception {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM chat_audit_log WHERE conversation_id = ?")) {
            ps.setString(1, conversationId);
            for (int i = 0; i < 60; i++) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Audit row for " + conversationId + " never appeared");
    }
}
