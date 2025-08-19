ALTER TABLE users 
ADD COLUMN annual_income DOUBLE PRECISION DEFAULT 50000.0,
ADD COLUMN credit_bureau_score INTEGER DEFAULT 650,
ADD COLUMN social_consent BOOLEAN DEFAULT FALSE,
ADD COLUMN social_handles JSONB,
ADD COLUMN IF NOT EXISTS kyc_passed boolean DEFAULT true,
ADD COLUMN IF NOT EXISTS credit_limit numeric;

CREATE INDEX IF NOT EXISTS ix_users_kyc_passed ON users(kyc_passed);