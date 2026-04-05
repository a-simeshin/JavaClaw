-- V6: Virtual files
CREATE TABLE virtual_files (
    id           VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    owner_id     VARCHAR(36)  REFERENCES users(id) ON DELETE CASCADE,
    path         VARCHAR(512) NOT NULL,
    content      TEXT,
    content_type VARCHAR(100) NOT NULL DEFAULT 'text/plain',
    size_bytes   BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_virtual_files_owner_id ON virtual_files(owner_id);
CREATE INDEX idx_virtual_files_path     ON virtual_files(path);
CREATE UNIQUE INDEX uq_virtual_files_user_path
    ON virtual_files(owner_id, path) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_virtual_files_global_path
    ON virtual_files(path) WHERE owner_id IS NULL;
