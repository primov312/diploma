-- AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
-- Consent management. Every grant and every withdrawal is a new row; nothing is updated or deleted, so the
-- table is the audit trail. The current state of a purpose is its newest row.
CREATE TABLE consent_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('DATA_PROCESSING', 'AI_ANALYSIS', 'DIGITAL_FOOTPRINT')),
    granted BOOLEAN NOT NULL,
    policy_version VARCHAR(24) NOT NULL,
    text_sha256 CHAR(64) NOT NULL,          -- hash of the exact text the user saw
    source VARCHAR(16) NOT NULL CHECK (source IN ('USER', 'DEMO_FIXTURE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_consent_user_purpose ON consent_records(user_id, purpose, id DESC);

-- What the customer had agreed to when the decision was made. Older applications have no snapshot.
ALTER TABLE credit_applications ADD COLUMN consent_snapshot JSONB;

-- Reports built from one allowed public page read by the collector service.
ALTER TABLE demo_signal_reports DROP CONSTRAINT demo_signal_reports_data_source_check;
ALTER TABLE demo_signal_reports ADD CONSTRAINT demo_signal_reports_data_source_check
    CHECK (data_source IN ('SYNTHETIC', 'OWNER_ACCOUNT', 'PUBLIC_PAGE'));
