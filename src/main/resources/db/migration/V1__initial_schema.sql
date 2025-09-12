-- Create users table (core profile)
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create user_stats table (profile stats)
CREATE TABLE IF NOT EXISTS user_stats (
  user_id            INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  computed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  kyc_passed         BOOLEAN,

  partner_orders_12m INTEGER,
  partner_avg_order_value DOUBLE PRECISION,
  partner_refund_rate DOUBLE PRECISION,
  partner_ontime_ratio DOUBLE PRECISION,
  partner_tenure_months INTEGER,

  rocket_ontime_ratio DOUBLE PRECISION,
  rocket_dpd30_12m    INTEGER,
  rocket_active_plans INTEGER,
  rocket_tenure_months INTEGER,

  income            DOUBLE PRECISION,
  credit_limit      DOUBLE PRECISION
);

-- Create transactions table (history)
CREATE TABLE IF NOT EXISTS transactions (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    method VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create paymentMethods table (methods)
CREATE TABLE IF NOT EXISTS paymentMethods (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance (e.g., frequent queries by user_id)
CREATE INDEX idx_stats_user_id ON user_stats(user_id);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_paymentMethods_user_id ON paymentMethods(user_id);
