-- Assume credit_requests exists; alter for factors
ALTER TABLE credit_requests
ADD COLUMN IF NOT EXISTS social_factors JSONB,  -- e.g., {"platforms": ["vkontakte"], "score_boost": 20}
ADD COLUMN IF NOT EXISTS email_factor JSONB,    -- e.g., {"verified": true, "score_boost": 15}
ADD COLUMN IF NOT EXISTS history_factor JSONB;  -- e.g., {"on_time_rate": 0.95, "delinquencies": 1, "score_boost": 25}

-- GIN index for JSONB queries if needed
CREATE INDEX IF NOT EXISTS idx_credit_social_factors ON credit_requests USING GIN (social_factors);