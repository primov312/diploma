-- A housing-situation change needs a supporting document. Every attempt is kept; only a verified one
-- creates a new financial input revision (financial_revision), so unverified claims never reach a calculation.
CREATE TABLE housing_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    previous_situation VARCHAR(20) NOT NULL,
    claimed_situation VARCHAR(20) NOT NULL CHECK (claimed_situation IN ('RENTING', 'OWNER', 'FAMILY', 'OTHER')),
    address_revision INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('VERIFIED_DEMO', 'NEEDS_REVIEW', 'MISMATCH')),
    financial_revision INTEGER,
    scenario_id VARCHAR(40) NOT NULL,
    checks JSONB NOT NULL,
    evidence JSONB NOT NULL,
    data_source VARCHAR(16) NOT NULL CHECK (data_source IN ('SYNTHETIC', 'USER_UPLOAD')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (user_id, financial_revision) REFERENCES financial_input_revisions(user_id, revision),
    CHECK ((status = 'VERIFIED_DEMO') = (financial_revision IS NOT NULL))
);
CREATE INDEX idx_housing_verifications_user ON housing_verifications(user_id, created_at DESC);
