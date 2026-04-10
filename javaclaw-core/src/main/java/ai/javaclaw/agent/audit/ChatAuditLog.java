package ai.javaclaw.agent.audit;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("chat_audit_log")
public record ChatAuditLog(
        @Id Long id,
        @Column("conversation_id") String conversationId,
        @Column("created_at") Instant createdAt,
        @Column("method") String method,
        @Column("system_prompt") String systemPrompt,
        @Column("history") String history,
        @Column("user_content") String userContent,
        @Column("tool_names") String toolNames,
        @Column("response_text") String responseText,
        @Column("error_message") String errorMessage,
        @Column("error_trace") String errorTrace,
        @Column("duration_ms") Long durationMs,
        @Column("user_id") String userId,
        @Column("tool_calls_detail") String toolCallsDetail,
        @Column("token_usage") String tokenUsage) {}
