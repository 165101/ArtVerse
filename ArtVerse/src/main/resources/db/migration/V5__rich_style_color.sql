-- Drop old constraints first so data migration can use new values
ALTER TABLE stories DROP CONSTRAINT IF EXISTS ck_stories_manga_style;
ALTER TABLE chapters DROP CONSTRAINT IF EXISTS ck_chapters_color_mode;

-- Normalize stories manga_style (old merged values → new style-only values)
UPDATE stories SET manga_style = 'japanese' WHERE manga_style IN ('japanese_bw', 'japanese_color');

-- Normalize chapters color_mode (mixed case from V1/V2 → match JPA @Enumerated(EnumType.STRING))
UPDATE chapters SET color_mode = 'BW' WHERE color_mode IN ('bw', 'BW');
UPDATE chapters SET color_mode = 'COLOR' WHERE color_mode IN ('color', 'COLOR');
ALTER TABLE chapters ALTER COLUMN color_mode SET DEFAULT 'BW';

-- Add new constraints (uppercase matches JPA EnumType.STRING behavior)
ALTER TABLE stories ADD CONSTRAINT ck_stories_manga_style CHECK (manga_style IN ('japanese', 'korean', 'american', 'european', 'chinese_ink', 'semi_realistic'));
ALTER TABLE chapters ADD CONSTRAINT ck_chapters_color_mode CHECK (color_mode IN ('BW', 'GRAYSCALE', 'COLOR', 'DUOTONE'));
