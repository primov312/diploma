-- Initial schema for Payment service
CREATE TABLE IF NOT EXISTS payments (
    id                  BIGSERIAL PRIMARY KEY,

    partner_payment_id  VARCHAR(128) NOT NULL,
    partner_id          VARCHAR(128),
    user_id             BIGINT NOT NULL,

    amount              NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3) NOT NULL,

    status              VARCHAR(32) NOT NULL
                          CHECK (status IN ('INITIATED','TRANSFERRED','FAILED','CANCELED')),

    payee_name          TEXT,
    payee_id       TEXT,
    payee_email               TEXT,

    provider            VARCHAR(128),
    provider_payment_id VARCHAR(128),
    transfer_reference  VARCHAR(128),
    failure_reason      TEXT,

    items               JSONB,

    idempotency_key     VARCHAR(128),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_partner_payment
    ON payments (partner_payment_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_idempotency
    ON payments (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_payments_status
    ON payments (status);

CREATE INDEX IF NOT EXISTS ix_payments_partner_id
    ON payments (partner_id);

CREATE INDEX IF NOT EXISTS ix_payments_user_id
    ON payments (user_id);

-- updated_at auto-maintenance
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_payments_updated_at ON payments;
CREATE TRIGGER trg_payments_updated_at
BEFORE UPDATE ON payments
FOR EACH ROW EXECUTE PROCEDURE set_updated_at();