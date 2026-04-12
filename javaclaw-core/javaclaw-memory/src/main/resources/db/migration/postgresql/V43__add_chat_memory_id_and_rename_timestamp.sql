-- V43: add surrogate PK 'id' and rename 'timestamp' -> 'created_at' in SPRING_AI_CHAT_MEMORY.
-- Removes the dependency on spring-ai-starter-model-chat-memory-repository-jdbc by giving
-- Spring Data JDBC a proper @Id column and a reserved-word-free timestamp column name.

-- Step 1: add id column with auto-generated IDENTITY value
ALTER TABLE SPRING_AI_CHAT_MEMORY
    ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY;

-- Step 2: make id the primary key
ALTER TABLE SPRING_AI_CHAT_MEMORY
    ADD CONSTRAINT pk_spring_ai_chat_memory PRIMARY KEY (id);

-- Step 3: rename timestamp -> created_at (timestamp is a reserved word in PostgreSQL)
ALTER TABLE SPRING_AI_CHAT_MEMORY
    RENAME COLUMN "timestamp" TO created_at;

-- Step 4: replace the old single-column index with a composite (conversation_id, created_at) index
DROP INDEX IF EXISTS idx_spring_ai_chat_memory_conversation_id;
CREATE INDEX idx_spring_ai_chat_memory_conversation_id_created_at
    ON SPRING_AI_CHAT_MEMORY (conversation_id, created_at);
