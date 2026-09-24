ALTER TABLE financial_input_state
    ADD COLUMN generation BIGINT NOT NULL DEFAULT 1;

CREATE TABLE affordability_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    generation BIGINT NOT NULL CHECK (generation > 0),
    financial_revision INTEGER NOT NULL,
    state VARCHAR(12) NOT NULL DEFAULT 'QUEUED'
        CHECK (state IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SUPERSEDED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    lease_until TIMESTAMPTZ,
    failure_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    FOREIGN KEY (user_id, financial_revision)
        REFERENCES financial_input_revisions(user_id, revision),
    CONSTRAINT affordability_job_generation_unique UNIQUE (user_id, generation)
);
CREATE INDEX idx_affordability_jobs_claim ON affordability_jobs(state, lease_until, id);

CREATE TABLE affordability_snapshots (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    generation BIGINT NOT NULL,
    financial_revision INTEGER NOT NULL,
    formula_version VARCHAR(40) NOT NULL,
    policy_version VARCHAR(40) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    base_amount NUMERIC(12,2) CHECK (base_amount >= 0),
    monthly_payment_capacity NUMERIC(12,2) CHECK (monthly_payment_capacity >= 0),
    breakdown JSONB NOT NULL,
    partners JSONB NOT NULL,
    reasons JSONB NOT NULL,
    data_source VARCHAR(20) NOT NULL DEFAULT 'USER_DECLARED'
        CHECK (data_source IN ('SYNTHETIC', 'USER_DECLARED')),
    FOREIGN KEY (user_id, financial_revision)
        REFERENCES financial_input_revisions(user_id, revision),
    CONSTRAINT affordability_snapshot_version_unique
        UNIQUE (user_id, generation, formula_version)
);
CREATE INDEX idx_affordability_snapshots_user_date
    ON affordability_snapshots(user_id, calculated_at DESC);

INSERT INTO affordability_jobs (user_id, generation, financial_revision)
SELECT user_id, generation, current_revision FROM financial_input_state;
