-- V6: Virtual files
CREATE TABLE virtual_files (
    id           TEXT    PRIMARY KEY,
    owner_id     TEXT    REFERENCES users(id) ON DELETE CASCADE,
    path         TEXT    NOT NULL,
    content      TEXT,
    content_type TEXT    NOT NULL DEFAULT 'text/plain',
    size_bytes   INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_virtual_files_owner_id ON virtual_files(owner_id);
CREATE INDEX idx_virtual_files_path     ON virtual_files(path);
CREATE UNIQUE INDEX uq_virtual_files_user_path
    ON virtual_files(owner_id, path) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uq_virtual_files_global_path
    ON virtual_files(path) WHERE owner_id IS NULL;
