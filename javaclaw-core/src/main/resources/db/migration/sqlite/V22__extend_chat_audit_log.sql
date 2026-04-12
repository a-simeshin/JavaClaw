-- V22: Extend chat_audit_log with user tracking, tool call details, and token usage
-- Spec ref: Phase 7 audit expansion (S11, S20)

ALTER TABLE chat_audit_log ADD COLUMN user_id TEXT;
ALTER TABLE chat_audit_log ADD COLUMN tool_calls_detail TEXT;   -- JSON: [{name, args, result, duration_ms}]
ALTER TABLE chat_audit_log ADD COLUMN token_usage TEXT;          -- JSON: {prompt_tokens, completion_tokens, total}

CREATE INDEX idx_chat_audit_log_user ON chat_audit_log (user_id);
