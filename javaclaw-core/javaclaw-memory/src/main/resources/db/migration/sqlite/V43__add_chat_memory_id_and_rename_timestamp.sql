-- V43: full table rebuild for SQLite (ADD COLUMN + RENAME COLUMN not supported atomically).
-- Adds surrogate PK 'id' (AUTOINCREMENT), renames 'timestamp' INTEGER -> 'created_at' TEXT (ISO-8601).
-- Epoch-millis INTEGER timestamps from pre-V43 rows are converted via strftime.

CREATE TABLE SPRING_AI_CHAT_MEMORY_V43 (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    content         TEXT,
    type            TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

-- Copy existing rows, converting epoch-millis INTEGER -> ISO-8601 TEXT.
-- strftime with 'unixepoch' expects epoch seconds; divide millis by 1000.
INSERT INTO SPRING_AI_CHAT_MEMORY_V43 (conversation_id, content, type, created_at)
SELECT
    conversation_id,
    content,
    type,
    strftime('%Y-%m-%dT%H:%M:%S.000Z', CAST(timestamp AS REAL) / 1000.0, 'unixepoch')
FROM SPRING_AI_CHAT_MEMORY;

DROP TABLE SPRING_AI_CHAT_MEMORY;
ALTER TABLE SPRING_AI_CHAT_MEMORY_V43 RENAME TO SPRING_AI_CHAT_MEMORY;

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_CREATED_AT_IDX
    ON SPRING_AI_CHAT_MEMORY(conversation_id, created_at);
