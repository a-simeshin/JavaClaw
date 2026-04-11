package ai.javaclaw.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

/**
 * Direct-JDBC assertion helper used by non-browser integration tests.
 *
 * <p>This is a standalone variant of {@link PlaywrightE2ETestBase.JdbcAssertions}
 * so that tests can extend {@link IntegrationTestBase} (lightweight, no browser)
 * rather than the heavyweight browser base class.
 */
public final class JdbcAssertions {

    private final DataSource dataSource;

    public JdbcAssertions(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Asserts the latest chat_audit_log row for the conversation has non-blank user_id. */
    public void assertAuditUserIdNotNull(String conversationId) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT user_id FROM chat_audit_log WHERE conversation_id = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, conversationId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next())
                    .as("audit row for conversation %s", conversationId)
                    .isTrue();
            assertThat(rs.getString("user_id")).isNotNull().isNotBlank();
        } catch (Exception e) {
            throw new AssertionError("DB assertion failed", e);
        }
    }

    /** Asserts chat_audit_log has the user_id column (schema-level verification). */
    public void assertAuditUserIdColumnExists() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'chat_audit_log' AND column_name = 'user_id'")) {
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).as("chat_audit_log.user_id column exists").isTrue();
        } catch (Exception e) {
            throw new AssertionError("DB assertion failed", e);
        }
    }

    /** Asserts chat_audit_log has the token_usage column (schema-level verification). */
    public void assertAuditTokenUsageColumnExists() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'chat_audit_log' AND column_name = 'token_usage'")) {
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).as("chat_audit_log.token_usage column exists").isTrue();
        } catch (Exception e) {
            throw new AssertionError("DB assertion failed", e);
        }
    }

    /** Asserts chat_audit_log has the system_prompt column (schema-level verification). */
    public void assertAuditSystemPromptColumnExists() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'chat_audit_log' AND column_name = 'system_prompt'")) {
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next())
                    .as("chat_audit_log.system_prompt column exists")
                    .isTrue();
        } catch (Exception e) {
            throw new AssertionError("DB assertion failed", e);
        }
    }

    /** Asserts conversation_summaries table exists with the messages_covered column. */
    public void assertSummaryTableExists() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'conversation_summaries' AND column_name = 'messages_covered'")) {
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next())
                    .as("conversation_summaries.messages_covered column exists")
                    .isTrue();
        } catch (Exception e) {
            throw new AssertionError("DB assertion failed", e);
        }
    }

    /** Asserts tool_examples table row count. */
    public void assertToolExampleCount(int expected) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM tool_examples")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(expected);
        } catch (Exception e) {
            throw new AssertionError("DB assertion failed", e);
        }
    }

    /** Asserts at least one tool_examples row has the given marker in user_message. */
    public void assertToolExampleContainsMarker(String marker) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM tool_examples WHERE user_message LIKE ?")) {
            ps.setString(1, "%" + marker + "%");
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1))
                    .as("at least one tool_examples row with marker %s", marker)
                    .isGreaterThanOrEqualTo(1);
        } catch (Exception e) {
            throw new AssertionError("DB assertion failed", e);
        }
    }

    /** Cleans tool_examples rows by marker. */
    public void cleanupToolExamplesByMarker(String marker) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM tool_examples WHERE user_message LIKE ?")) {
            ps.setString(1, "%" + marker + "%");
            ps.executeUpdate();
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
