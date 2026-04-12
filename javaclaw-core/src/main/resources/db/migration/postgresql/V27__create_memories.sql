-- Long-term memory for the agent: per-user and global key-value facts
CREATE TABLE memories (
    id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    owner_id    TEXT REFERENCES users(id) ON DELETE CASCADE,
    key         TEXT NOT NULL,
    content     TEXT NOT NULL,
    category    TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Each user (or global scope) can have at most one memory per key
CREATE UNIQUE INDEX uq_memories_owner_key ON memories (COALESCE(owner_id, '__global__'), key);

-- Fast lookup by owner
CREATE INDEX idx_memories_owner_id ON memories (owner_id);
