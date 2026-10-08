ALTER TABLE demo_signal_settings
    ALTER COLUMN location_enabled SET DEFAULT TRUE,
    ALTER COLUMN social_enabled SET DEFAULT TRUE;

-- Recalculate budgets that can now use previously disabled, saved location history.
-- FinancialInputsService enqueues the new generation when the account is next read.
UPDATE financial_input_state
SET generation = generation + 1
WHERE user_id IN (
    SELECT s.user_id FROM demo_signal_settings s
    WHERE NOT s.location_enabled
      AND EXISTS (SELECT 1 FROM demo_signal_reports r WHERE r.user_id = s.user_id AND r.kind = 'LOCATION')
);

UPDATE demo_signal_settings
SET location_enabled = TRUE, social_enabled = TRUE, updated_at = now()
WHERE NOT location_enabled OR NOT social_enabled;

ALTER TABLE demo_signal_settings
    ADD CONSTRAINT demo_signals_always_enabled CHECK (location_enabled AND social_enabled);
