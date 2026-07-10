-- Refresh tokens for SkillForge sessions (admins, trainers, candidates all
-- share sf_users). Without this table, the 15-minute SkillForge access
-- token had no renewal path at all -- every session hard-expired and forced
-- a full re-login. Token is stored in plaintext here deliberately (unlike
-- the hashed invitation/reset tokens) to match the existing ExecutionOS
-- refresh_tokens table's approach: it is only ever transmitted over HTTPS
-- to the same origin that issued it, and revocation is immediate via the
-- revoked flag.
CREATE TABLE IF NOT EXISTS sf_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sf_user_id UUID NOT NULL,
    token TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sf_refresh_tokens_token ON sf_refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_sf_refresh_tokens_sf_user_id ON sf_refresh_tokens(sf_user_id);
