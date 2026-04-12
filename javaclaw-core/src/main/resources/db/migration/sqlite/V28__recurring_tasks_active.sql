-- V28: Add active flag to recurring_tasks for pause/resume support
ALTER TABLE recurring_tasks ADD COLUMN active INTEGER NOT NULL DEFAULT 1;
