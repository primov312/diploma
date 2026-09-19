-- Rocket Credit diploma schema (docs/DIPLOMA_ARCHITECTURE.md §7).
-- One database, owned by the Java backend. Money is NUMERIC(12,2) USD.
-- Fixture identifiers are UNIQUE so repeated seeding is idempotent.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_unique    UNIQUE (email),
    CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email)) AND email <> '')
);

CREATE TABLE demo_financial_profiles (
    user_id             BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    monthly_income      NUMERIC(12,2) NOT NULL CHECK (monthly_income >= 0),
    monthly_expenses    NUMERIC(12,2) NOT NULL CHECK (monthly_expenses >= 0),
    monthly_obligations NUMERIC(12,2) NOT NULL CHECK (monthly_obligations >= 0),
    profile_complete    BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    -- FIXTURE: seeded demo customer; STARTER: synthetic starter data for a new registration
    synthetic_source    VARCHAR(20) NOT NULL CHECK (synthetic_source IN ('FIXTURE', 'STARTER')),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE partners (
    id           BIGSERIAL PRIMARY KEY,
    slug         VARCHAR(40)  NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    amount_cap   NUMERIC(12,2) NOT NULL CHECK (amount_cap > 0),
    currency     VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    CONSTRAINT partners_slug_unique UNIQUE (slug)
);

CREATE TABLE products (
    id         BIGSERIAL PRIMARY KEY,
    partner_id BIGINT NOT NULL REFERENCES partners(id),
    fixture_id VARCHAR(80)  NOT NULL,
    name       VARCHAR(160) NOT NULL,
    price      NUMERIC(12,2) NOT NULL CHECK (price > 0),
    currency   VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    CONSTRAINT products_fixture_unique UNIQUE (fixture_id)
);
CREATE INDEX idx_products_partner ON products(partner_id);

CREATE TABLE transactions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    partner_id   BIGINT NOT NULL REFERENCES partners(id),
    fixture_id   VARCHAR(80) NOT NULL,
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency     VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    occurred_on  DATE NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'REFUNDED')),
    paid_on_time BOOLEAN NOT NULL DEFAULT TRUE,
    description  VARCHAR(200),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT transactions_fixture_unique UNIQUE (fixture_id)
);
CREATE INDEX idx_transactions_user_partner ON transactions(user_id, partner_id);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, occurred_on DESC);

CREATE TABLE credit_applications (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    partner_id       BIGINT NOT NULL REFERENCES partners(id),
    product_id       BIGINT REFERENCES products(id),
    requested_amount NUMERIC(12,2) NOT NULL CHECK (requested_amount > 0),
    currency         VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    decision_status  VARCHAR(10) NOT NULL CHECK (decision_status IN ('APPROVED', 'REJECTED', 'REVIEW')),
    score            NUMERIC(5,4) NOT NULL CHECK (score >= 0 AND score <= 1),
    possible_amount  NUMERIC(12,2) NOT NULL CHECK (possible_amount >= 0),
    reasons          JSONB NOT NULL,
    factors          JSONB NOT NULL,
    feature_snapshot JSONB NOT NULL,
    ai_requested     BOOLEAN NOT NULL,
    ai_status        VARCHAR(20) NOT NULL CHECK (ai_status IN ('NOT_REQUESTED', 'UNAVAILABLE', 'APPLIED')),
    policy_version   VARCHAR(40) NOT NULL,
    model_version    VARCHAR(40),
    preparation_mode VARCHAR(10) NOT NULL DEFAULT 'SEQUENTIAL' CHECK (preparation_mode IN ('SEQUENTIAL', 'PARALLEL')),
    observed_at      TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_applications_user_created ON credit_applications(user_id, created_at DESC);
