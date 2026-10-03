-- Freeze illustrative terms on new approvals; historical decisions keep a NULL snapshot.
ALTER TABLE credit_applications ADD COLUMN projected_schedule JSONB;
ALTER TABLE credit_applications ADD CONSTRAINT applications_schedule_approved
    CHECK (projected_schedule IS NULL OR
           (decision_status = 'APPROVED' AND jsonb_typeof(projected_schedule) = 'object'));
