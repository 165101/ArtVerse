-- Fix CHECK constraints to accept uppercase enum values from Java @Enumerated(STRING)

ALTER TABLE chapters DROP CONSTRAINT IF EXISTS ck_chapters_color_mode;
ALTER TABLE chapters ADD CONSTRAINT ck_chapters_color_mode CHECK (color_mode IN ('BW', 'bw', 'COLOR', 'color'));

ALTER TABLE chapters DROP CONSTRAINT IF EXISTS ck_chapters_content_source;
ALTER TABLE chapters ADD CONSTRAINT ck_chapters_content_source CHECK (content_source IS NULL OR content_source IN ('CHAT', 'chat', 'IMPORT', 'import'));

ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS ck_chat_messages_role;
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_role CHECK (role IN ('USER', 'user', 'ASSISTANT', 'assistant', 'SYSTEM', 'system'));

ALTER TABLE manga_images DROP CONSTRAINT IF EXISTS ck_manga_images_storage_provider;
ALTER TABLE manga_images ADD CONSTRAINT ck_manga_images_storage_provider CHECK (storage_provider IN ('LOCAL', 'local', 'MINIO', 'minio'));
