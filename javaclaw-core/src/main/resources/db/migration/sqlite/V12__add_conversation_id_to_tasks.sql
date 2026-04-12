ALTER TABLE tasks ADD COLUMN conversation_id TEXT;
UPDATE tasks SET conversation_id = source_channel_name WHERE source_channel_name IS NOT NULL;
