CREATE TABLE wallet_accounts (
 user_id BIGINT PRIMARY KEY REFERENCES users(id),
 balance NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
 currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency='USD'),
 version BIGINT NOT NULL DEFAULT 0,
 obligation_date DATE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO wallet_accounts(user_id) SELECT id FROM users;
CREATE TABLE financial_operations (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 operation_type VARCHAR(40) NOT NULL, idempotency_key VARCHAR(120) NOT NULL,
 fingerprint TEXT NOT NULL, result JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(user_id,idempotency_key)
);
CREATE TABLE credit_quotes (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 partner_id BIGINT NOT NULL REFERENCES partners(id), product_id BIGINT REFERENCES products(id),
 principal NUMERIC(12,2) NOT NULL CHECK(principal>0),
 term_unit VARCHAR(5) NOT NULL CHECK(term_unit IN ('WEEK','MONTH')),
 installment_count INT NOT NULL,
 annual_rate NUMERIC(9,6) NOT NULL CHECK(annual_rate>=0), pricing_version VARCHAR(80) NOT NULL,
 generation BIGINT NOT NULL, start_date DATE NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 schedule JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK ((term_unit='WEEK' AND installment_count BETWEEN 1 AND 3) OR (term_unit='MONTH' AND installment_count BETWEEN 1 AND 24))
);
ALTER TABLE credit_applications ADD COLUMN quote_id BIGINT UNIQUE REFERENCES credit_quotes(id);
CREATE TABLE loans (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 application_id BIGINT NOT NULL UNIQUE REFERENCES credit_applications(id),
 quote_id BIGINT NOT NULL UNIQUE REFERENCES credit_quotes(id),
 partner_id BIGINT NOT NULL REFERENCES partners(id), product_id BIGINT REFERENCES products(id),
 principal NUMERIC(12,2) NOT NULL CHECK(principal>0), term_unit VARCHAR(5) NOT NULL,
 installment_count INT NOT NULL, annual_rate NUMERIC(9,6) NOT NULL,
 pricing_version VARCHAR(80) NOT NULL, start_date DATE NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','PAID','SETTLED_EARLY')),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), closed_at TIMESTAMPTZ
);
CREATE INDEX loans_user ON loans(user_id,status);
CREATE TABLE wallet_entries (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES wallet_accounts(user_id),
 operation_id BIGINT NOT NULL REFERENCES financial_operations(id),
 kind VARCHAR(30) NOT NULL CHECK(kind IN ('TOP_UP','PURCHASE','REPAYMENT','EARLY_SETTLEMENT')),
 amount NUMERIC(12,2) NOT NULL CHECK(amount<>0), balance_after NUMERIC(12,2) NOT NULL CHECK(balance_after>=0),
 loan_id BIGINT REFERENCES loans(id), description VARCHAR(200) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK ((kind='TOP_UP' AND amount>0) OR (kind<>'TOP_UP' AND amount<0)),
 UNIQUE(operation_id)
);
CREATE INDEX wallet_entries_history ON wallet_entries(user_id,created_at DESC,id DESC);
CREATE TABLE repayment_installments (
 id BIGSERIAL PRIMARY KEY, loan_id BIGINT NOT NULL REFERENCES loans(id), sequence INT NOT NULL,
 period_start DATE NOT NULL, due_date DATE NOT NULL,
 principal NUMERIC(12,2) NOT NULL CHECK(principal>0), interest NUMERIC(12,2) NOT NULL CHECK(interest>=0),
 principal_paid NUMERIC(12,2) NOT NULL DEFAULT 0, interest_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
 interest_waived NUMERIC(12,2) NOT NULL DEFAULT 0,
 payment_entry_id BIGINT REFERENCES wallet_entries(id), paid_at TIMESTAMPTZ,
 UNIQUE(loan_id,sequence), CHECK(due_date>period_start),
 CHECK(principal_paid>=0 AND principal_paid<=principal),
 CHECK(interest_paid>=0 AND interest_waived>=0 AND interest_paid+interest_waived<=interest),
 CHECK((payment_entry_id IS NULL AND paid_at IS NULL AND principal_paid=0 AND interest_paid=0 AND interest_waived=0)
 OR (payment_entry_id IS NOT NULL AND paid_at IS NOT NULL AND principal_paid=principal AND interest_paid+interest_waived=interest))
);
CREATE INDEX repayment_due ON repayment_installments(due_date,loan_id,sequence) WHERE paid_at IS NULL;
CREATE TABLE repayment_attempts (
 id BIGSERIAL PRIMARY KEY, installment_id BIGINT NOT NULL REFERENCES repayment_installments(id),
 operation_id BIGINT REFERENCES financial_operations(id), mode VARCHAR(10) NOT NULL,
 outcome VARCHAR(40) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE settlement_quotes (
 id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), loan_id BIGINT NOT NULL REFERENCES loans(id),
 loan_version BIGINT NOT NULL, as_of DATE NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 principal NUMERIC(12,2) NOT NULL, interest NUMERIC(12,2) NOT NULL, waived NUMERIC(12,2) NOT NULL,
 allocations JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE transactions ALTER COLUMN fixture_id DROP NOT NULL;
ALTER TABLE transactions ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'HISTORICAL';
ALTER TABLE transactions ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'HISTORICAL';
ALTER TABLE transactions ADD COLUMN product_id BIGINT REFERENCES products(id);
ALTER TABLE transactions ADD COLUMN loan_id BIGINT UNIQUE REFERENCES loans(id);
ALTER TABLE transactions ADD COLUMN wallet_entry_id BIGINT UNIQUE REFERENCES wallet_entries(id);
ALTER TABLE transactions ADD CONSTRAINT transaction_origin CHECK(origin IN ('HISTORICAL','DEMO_PURCHASE'));
ALTER TABLE transactions ADD CONSTRAINT transaction_payment CHECK(payment_method IN ('HISTORICAL','BALANCE','CREDIT'));
