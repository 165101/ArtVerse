ALTER TABLE stories
  ADD COLUMN manga_style VARCHAR(32) NOT NULL DEFAULT 'japanese_bw';

ALTER TABLE stories
  ADD CONSTRAINT ck_stories_manga_style CHECK (manga_style IN ('japanese_bw', 'japanese_color', 'korean_webtoon', 'american_comic'));
