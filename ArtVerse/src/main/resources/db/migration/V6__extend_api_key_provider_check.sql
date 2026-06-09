ALTER TABLE user_api_keys DROP CONSTRAINT ck_user_api_keys_provider;
ALTER TABLE user_api_keys ADD CONSTRAINT ck_user_api_keys_provider CHECK (provider IN ('deepseek', 'image2', 'coze'));
