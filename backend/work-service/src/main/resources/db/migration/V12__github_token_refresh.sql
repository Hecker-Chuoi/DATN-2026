-- ============================================================================
-- V12: HỖ TRỢ REFRESH TOKEN VÀ TỰ ĐỘNG LÀM MỚI TOKEN GITHUB OAUTH
-- ============================================================================

ALTER TABLE user_github_tokens
    ADD COLUMN IF NOT EXISTS refresh_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS refresh_token_expires_at TIMESTAMP WITH TIME ZONE;
