CREATE TABLE demo_districts (
    district_id VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    country_code CHAR(2) NOT NULL,
    sort_order INTEGER NOT NULL UNIQUE
);

CREATE TABLE local_cost_references (
    dataset_version VARCHAR(40) NOT NULL,
    district_id VARCHAR(40) NOT NULL REFERENCES demo_districts(district_id),
    monthly_rent NUMERIC(12,2) NOT NULL CHECK (monthly_rent >= 0),
    monthly_groceries NUMERIC(12,2) NOT NULL CHECK (monthly_groceries >= 0),
    city_salary_monthly NUMERIC(12,2) NOT NULL CHECK (city_salary_monthly >= 0),
    salary_basis VARCHAR(8) NOT NULL CHECK (salary_basis IN ('GROSS', 'NET')),
    currency CHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    period VARCHAR(8) NOT NULL DEFAULT 'MONTHLY' CHECK (period = 'MONTHLY'),
    per_person BOOLEAN NOT NULL DEFAULT TRUE,
    source_label VARCHAR(120) NOT NULL,
    observed_at DATE NOT NULL,
    synthetic BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (dataset_version, district_id)
);

CREATE TABLE user_address_revisions (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revision INTEGER NOT NULL CHECK (revision > 0),
    country_code CHAR(2) NOT NULL,
    city VARCHAR(100) NOT NULL,
    district_id VARCHAR(40) NOT NULL REFERENCES demo_districts(district_id),
    postal_code VARCHAR(20) NOT NULL,
    street VARCHAR(160) NOT NULL,
    building VARCHAR(40) NOT NULL,
    unit VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, revision)
);

CREATE TABLE user_address_state (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current_revision INTEGER NOT NULL,
    generation BIGINT NOT NULL DEFAULT 1,
    FOREIGN KEY (user_id, current_revision)
        REFERENCES user_address_revisions(user_id, revision)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE address_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    address_revision INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('VERIFIED_DEMO', 'NEEDS_REVIEW', 'MISMATCH')),
    scenario_id VARCHAR(40) NOT NULL,
    checks JSONB NOT NULL,
    evidence JSONB NOT NULL,
    data_source VARCHAR(16) NOT NULL CHECK (data_source IN ('SYNTHETIC', 'USER_UPLOAD')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (user_id, address_revision)
        REFERENCES user_address_revisions(user_id, revision)
);
CREATE INDEX idx_address_verifications_revision
    ON address_verifications(user_id, address_revision, created_at DESC);

INSERT INTO demo_districts (district_id, display_name, city, country_code, sort_order) VALUES
    ('budapest-v', 'Budapest District V', 'Budapest', 'HU', 1),
    ('budapest-xi', 'Budapest District XI', 'Budapest', 'HU', 2),
    ('budapest-xiii', 'Budapest District XIII', 'Budapest', 'HU', 3);

INSERT INTO local_cost_references
    (dataset_version, district_id, monthly_rent, monthly_groceries, city_salary_monthly,
     salary_basis, source_label, observed_at, synthetic)
VALUES
    ('budapest-demo-v1', 'budapest-v', 1000, 350, 2600, 'NET', 'Synthetic diploma reference dataset', '2026-09-01', TRUE),
    ('budapest-demo-v1', 'budapest-xi', 820, 320, 2600, 'NET', 'Synthetic diploma reference dataset', '2026-09-01', TRUE),
    ('budapest-demo-v1', 'budapest-xiii', 760, 310, 2600, 'NET', 'Synthetic diploma reference dataset', '2026-09-01', TRUE);
