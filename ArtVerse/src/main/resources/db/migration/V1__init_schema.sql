CREATE TABLE stories (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  cover_image VARCHAR(500),
  ref_image VARCHAR(500),
  character_profiles TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE story_asset_groups (
  id BIGSERIAL PRIMARY KEY,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  character_profiles TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_asset_groups_story_id ON story_asset_groups(story_id);

CREATE TABLE chapters (
  id BIGSERIAL PRIMARY KEY,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  chapter_number INT NOT NULL,
  novel_content TEXT,
  content_source VARCHAR(20),
  scenes_text TEXT,
  character_profiles TEXT,
  asset_group_id BIGINT REFERENCES story_asset_groups(id) ON DELETE SET NULL,
  ref_image VARCHAR(500),
  color_mode VARCHAR(20) NOT NULL DEFAULT 'bw',
  image_count INT NOT NULL DEFAULT 10,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_chapters_story_number UNIQUE(story_id, chapter_number),
  CONSTRAINT ck_chapters_content_source CHECK (content_source IS NULL OR content_source IN ('chat', 'import')),
  CONSTRAINT ck_chapters_color_mode CHECK (color_mode IN ('bw', 'color')),
  CONSTRAINT ck_chapters_image_count CHECK (image_count IN (4, 6, 8, 10, 12, 15, 20))
);
CREATE INDEX idx_chapters_story_id ON chapters(story_id);

CREATE TABLE chat_messages (
  id BIGSERIAL PRIMARY KEY,
  chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_chat_messages_role CHECK (role IN ('user', 'assistant', 'system'))
);
CREATE INDEX idx_chat_messages_chapter_id_created_at ON chat_messages(chapter_id, created_at);

CREATE TABLE manga_images (
  id BIGSERIAL PRIMARY KEY,
  chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  image_number INT NOT NULL,
  image_path VARCHAR(500) NOT NULL,
  storage_provider VARCHAR(20) NOT NULL DEFAULT 'local',
  bucket VARCHAR(120),
  object_key VARCHAR(700),
  content_type VARCHAR(100),
  size_bytes BIGINT,
  prompt TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_manga_images_chapter_number UNIQUE(chapter_id, image_number),
  CONSTRAINT ck_manga_images_number CHECK (image_number > 0),
  CONSTRAINT ck_manga_images_storage_provider CHECK (storage_provider IN ('local', 'minio'))
);
CREATE INDEX idx_manga_images_chapter_id ON manga_images(chapter_id);
CREATE INDEX idx_manga_images_object_key ON manga_images(bucket, object_key);
