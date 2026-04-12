-- V2: Chat memory persistence schema owned by the javaclaw-memory module.
--
-- Backs Spring Data JDBC's SpringDataChatMemoryRepository. The table ships with the
-- final post-Spring-AI-starter layout: a surrogate BIGINT IDENTITY primary key, a
-- nullable content column (tool-response rows carry no text payload), and a
-- TIMESTAMPTZ created_at column (Spring Data JDBC maps java.time.Instant to
-- TIMESTAMPTZ — TIMESTAMP WITHOUT TIME ZONE triggers a "can't infer SQL type"
-- error on Instant binds).
--
-- The composite (conversation_id, created_at) index matches the repository's
-- ORDER BY created_at, id lookup plan. A foreign key from conversation_id to
-- conversations(id) is added later by javaclaw-core V5 once the conversations
-- table exists.
CREATE TABLE SPRING_AI_CHAT_MEMORY (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id VARCHAR(256) NOT NULL,
    content         TEXT,
    type            VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_spring_ai_chat_memory_conversation_id_created_at
    ON SPRING_AI_CHAT_MEMORY (conversation_id, created_at);
