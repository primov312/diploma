-- V1__init.sql for creditdb: Create baseline credit_requests table

CREATE TABLE IF NOT EXISTS credit_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,  -- References users.id in userdb (logical FK, no enforced constraint across dbs)
    request_amount DECIMAL(10, 2) NOT NULL,
    score INTEGER NOT NULL,  -- Rule-based score (e.g., 0-100)
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED', 'DENIED', 'PENDING')),  -- Enum-like
    reasons JSONB,  -- e.g., {"factors": ["low_history", "high_risk"]}
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for queries
CREATE INDEX IF NOT EXISTS idx_credit_requests_user_id ON credit_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_credit_requests_requested_at ON credit_requests(requested_at);