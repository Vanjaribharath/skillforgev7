-- Password reset support for sf_users (admins, trainers, candidates all
-- share this table). Token is stored as a hash, never plaintext, mirroring
-- how refresh_tokens are handled on the ExecutionOS side.
ALTER TABLE sf_users
    ADD COLUMN IF NOT EXISTS reset_token_hash TEXT;
ALTER TABLE sf_users
    ADD COLUMN IF NOT EXISTS reset_token_expires_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_sf_users_reset_token_hash ON sf_users(reset_token_hash) WHERE reset_token_hash IS NOT NULL;
