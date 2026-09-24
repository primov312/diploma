CREATE TABLE demo_signal_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    location_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    social_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    permission_generation BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE analysis_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('ADDRESS', 'LOCAL_COSTS', 'LOCATION', 'SOCIAL')),
    source_revision INTEGER NOT NULL DEFAULT 0,
    permission_generation BIGINT NOT NULL,
    state VARCHAR(12) NOT NULL CHECK (state IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'SUPERSEDED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_code VARCHAR(40),
    scenario_id VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    deduplication_key VARCHAR(160) NOT NULL,
    UNIQUE (user_id, deduplication_key)
);
CREATE INDEX idx_analysis_jobs_user_created ON analysis_jobs(user_id, created_at DESC);

CREATE TABLE demo_signal_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('LOCATION', 'SOCIAL')),
    scenario_id VARCHAR(60) NOT NULL,
    permission_generation BIGINT NOT NULL,
    report JSONB NOT NULL,
    data_source VARCHAR(16) NOT NULL CHECK (data_source = 'SYNTHETIC'),
    analysis_mode VARCHAR(8) NOT NULL CHECK (analysis_mode IN ('FIXTURE', 'AI')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_demo_signal_reports_user_kind ON demo_signal_reports(user_id, kind, created_at DESC);
