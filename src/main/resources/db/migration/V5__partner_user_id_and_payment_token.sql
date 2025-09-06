-- users.partner_user_id
ALTER TABLE users ADD COLUMN IF NOT EXISTS partner_user_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS ix_users_partner_user_id ON users(partner_user_id);

-- paymentMethods.token (for idempotent upsert)
ALTER TABLE paymentMethods ADD COLUMN IF NOT EXISTS token VARCHAR(255);
CREATE INDEX IF NOT EXISTS ix_paymentmethods_userid_token ON paymentMethods(user_id, token);