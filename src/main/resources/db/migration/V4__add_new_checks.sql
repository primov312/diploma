CREATE TABLE IF NOT EXISTS social_auths (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('instagram', 'facebook', 'vkontakte')),
    token_hash VARCHAR(255),
    fetched_data JSONB,
    consented_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, platform)
);

ALTER TABLE users
ADD COLUMN IF NOT EXISTS work_email VARCHAR(255),
ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS partner_histories (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    partner_id VARCHAR(50) NOT NULL,
    summary JSONB NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, partner_id)
);

CREATE INDEX IF NOT EXISTS idx_social_auths_user_id ON social_auths(user_id);
CREATE INDEX IF NOT EXISTS idx_partner_histories_user_id ON partner_histories(user_id);