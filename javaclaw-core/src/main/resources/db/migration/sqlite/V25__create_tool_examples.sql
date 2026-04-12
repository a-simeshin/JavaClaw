-- V25: Few-shot examples for tool calling (per-tool, optionally per-user)
CREATE TABLE tool_examples (
    id                TEXT PRIMARY KEY,
    tool_name         TEXT NOT NULL,
    owner_id          TEXT REFERENCES users(id) ON DELETE CASCADE,
    example_order     INTEGER NOT NULL DEFAULT 0,
    user_message      TEXT NOT NULL,
    assistant_message TEXT,
    tool_call         TEXT NOT NULL,
    tool_result       TEXT,
    created_at        TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (tool_name, owner_id, example_order)
);

CREATE INDEX idx_tool_examples_tool_name ON tool_examples (tool_name);
CREATE INDEX idx_tool_examples_owner ON tool_examples (owner_id);
