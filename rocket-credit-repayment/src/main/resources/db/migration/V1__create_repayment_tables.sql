CREATE TABLE repayment_plans (
  id BIGSERIAL PRIMARY KEY,
  plan_uid TEXT UNIQUE NOT NULL,
  user_id BIGINT NOT NULL,
  currency TEXT NOT NULL,
  total_amount NUMERIC(18,2) NOT NULL,
  installments INT NOT NULL,
  provider TEXT NOT NULL,
  provider_account TEXT,
  provider_customer_id TEXT,
  provider_payment_method_id TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE repayment_installments (
  id BIGSERIAL PRIMARY KEY,
  plan_uid TEXT NOT NULL REFERENCES repayment_plans(plan_uid) ON DELETE CASCADE,
  installment_no INT NOT NULL,
  due_date DATE NOT NULL,
  amount NUMERIC(18,2) NOT NULL,
  currency TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  provider_charge_id TEXT,
  last_attempt_at TIMESTAMPTZ,
  last_error TEXT,
  UNIQUE (plan_uid, installment_no)
);

CREATE INDEX IF NOT EXISTS idx_installments_due ON repayment_installments (due_date, status);