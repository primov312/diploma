-- users.partner_user_id
ALTER TABLE users ADD COLUMN IF NOT EXISTS partner_user_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS ix_users_partner_user_id ON users(partner_user_id);
