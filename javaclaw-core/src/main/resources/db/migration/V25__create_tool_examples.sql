-- V25: Few-shot examples for tool calling (per-tool, optionally per-user)
CREATE TABLE tool_examples (
    id              VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tool_name       VARCHAR(120) NOT NULL,
    owner_id        VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
    example_order   INT          NOT NULL DEFAULT 0,
    user_message    TEXT         NOT NULL,
    assistant_message TEXT,
    tool_call       TEXT         NOT NULL,
    tool_result     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (tool_name, owner_id, example_order)
);

CREATE INDEX idx_tool_examples_tool_name ON tool_examples (tool_name);
CREATE INDEX idx_tool_examples_owner ON tool_examples (owner_id);
