-- Repayment plan mirror tables in user-data
CREATE TABLE IF NOT EXISTS user_repayment_plans (
  id SERIAL PRIMARY KEY,
  plan_uid VARCHAR(64) NOT NULL UNIQUE,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  total_amount DOUBLE PRECISION NOT NULL,
  currency VARCHAR(16) NOT NULL,
  installments INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_user_repayment_plans_user ON user_repayment_plans(user_id);

CREATE TABLE IF NOT EXISTS user_repayment_installments (
  id SERIAL PRIMARY KEY,
  plan_uid VARCHAR(64) NOT NULL REFERENCES user_repayment_plans(plan_uid) ON DELETE CASCADE,
  installment_no INTEGER NOT NULL,
  due_date DATE NOT NULL,
  amount DOUBLE PRECISION NOT NULL,
  currency VARCHAR(16) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  CONSTRAINT uk_plan_installment UNIQUE(plan_uid, installment_no)
);

CREATE INDEX IF NOT EXISTS ix_uri_plan_uid ON user_repayment_installments(plan_uid);

