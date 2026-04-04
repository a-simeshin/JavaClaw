CREATE TABLE tasks (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'new',
    feedback TEXT,
    source_channel_name VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_created_at ON tasks(created_at);
CREATE INDEX idx_tasks_created_at_status ON tasks(created_at, status);

CREATE TABLE recurring_tasks (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    cron_expression VARCHAR(100) NOT NULL,
    job_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
