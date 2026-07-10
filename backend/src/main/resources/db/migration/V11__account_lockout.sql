-- Account-level brute-force lockout. Previously the only protection against
-- repeated bad-password attempts was IP-based rate limiting (RateLimitFilter),
-- which does nothing against a distributed attacker rotating source IPs
-- against one specific account.
ALTER TABLE sf_users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sf_users
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
