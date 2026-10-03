-- Owner-connected Facebook account for the optional social analysis. The access token is
-- stored AES-GCM encrypted; ownership is proven by the OAuth login, never by a password.
CREATE TABLE social_connections (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(16) NOT NULL CHECK (provider IN ('FACEBOOK')),
    provider_user_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(200),
    token_ciphertext TEXT NOT NULL,
    token_expires_at TIMESTAMPTZ,
    connected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

-- Reports built from the connected owner account are labelled differently from fixtures.
ALTER TABLE demo_signal_reports DROP CONSTRAINT demo_signal_reports_data_source_check;
ALTER TABLE demo_signal_reports ADD CONSTRAINT demo_signal_reports_data_source_check
    CHECK (data_source IN ('SYNTHETIC', 'OWNER_ACCOUNT'));
