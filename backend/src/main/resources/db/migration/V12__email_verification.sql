-- Email verification for the self-service register-organization flow (the
-- one signup path with no admin in the loop vouching for the email address).
-- Mirrors the existing reset_token_hash / reset_token_expires_at columns
-- added in V8 -- same pattern, separate columns so the two flows can never
-- collide with each other.
ALTER TABLE sf_users
    ADD COLUMN IF NOT EXISTS verify_token_hash TEXT;
ALTER TABLE sf_users
    ADD COLUMN IF NOT EXISTS verify_token_expires_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_sf_users_verify_token_hash ON sf_users(verify_token_hash) WHERE verify_token_hash IS NOT NULL;
