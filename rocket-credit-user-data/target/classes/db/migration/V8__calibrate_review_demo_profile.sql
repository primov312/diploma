-- Keep the synthetic review persona between the configured review (0.50)
-- and approval (0.60) thresholds for a representative 250 USD request.
UPDATE user_stats
SET rocket_ontime_ratio = 0.70,
    computed_at = now()
WHERE user_id = (
  SELECT id
  FROM users
  WHERE email = 'demo-review@rocket.local'
);
