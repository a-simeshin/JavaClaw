ALTER TABLE tasks ADD COLUMN conversation_id VARCHAR(256);
UPDATE tasks SET conversation_id = source_channel_name WHERE source_channel_name IS NOT NULL;
