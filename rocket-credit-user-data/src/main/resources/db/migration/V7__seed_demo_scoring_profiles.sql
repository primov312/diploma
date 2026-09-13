-- Repeatable diploma-demo personas. These rows contain only derived,
-- synthetic scoring features; no real bank account or card data is stored.

INSERT INTO users (name, email, partner_user_id, kyc_passed, email_verified)
VALUES
  ('Avery Approved', 'demo-approved@rocket.local', 'streambox-approved', TRUE, TRUE),
  ('Riley Review', 'demo-review@rocket.local', 'markethub-review', TRUE, TRUE),
  ('Drew Denied', 'demo-denied@rocket.local', 'threadly-denied', FALSE, TRUE),
  ('Casey Limited', 'demo-limited@rocket.local', 'streambox-limited', TRUE, TRUE)
ON CONFLICT (email) DO UPDATE SET
  name = EXCLUDED.name,
  partner_user_id = EXCLUDED.partner_user_id,
  kyc_passed = EXCLUDED.kyc_passed,
  email_verified = EXCLUDED.email_verified;

INSERT INTO user_stats (
  user_id, computed_at, kyc_passed,
  partner_orders_12m, partner_avg_order_value, partner_refund_rate, partner_ontime_ratio, partner_tenure_months,
  rocket_ontime_ratio, rocket_dpd30_12m, rocket_active_plans, rocket_tenure_months,
  income, credit_limit
)
SELECT
  u.id,
  now(),
  scenario.kyc_passed,
  scenario.partner_orders_12m, scenario.partner_avg_order_value, scenario.partner_refund_rate,
  scenario.partner_ontime_ratio, scenario.partner_tenure_months,
  scenario.rocket_ontime_ratio, scenario.rocket_dpd30_12m, scenario.rocket_active_plans,
  scenario.rocket_tenure_months, scenario.income, scenario.credit_limit
FROM (
  VALUES
    ('demo-approved@rocket.local', TRUE, 18, 125.00, 0.02, 0.98, 36, 0.99, 0, 0, 24, 4500.00, 1500.00),
    ('demo-review@rocket.local', TRUE, 4, 60.00, 0.15, 0.72, 6, 0.80, 0, 1, 8, 2500.00, 900.00),
    ('demo-denied@rocket.local', FALSE, 8, 90.00, 0.10, 0.85, 12, 0.85, 0, 0, 12, 3000.00, 1000.00),
    ('demo-limited@rocket.local', TRUE, 12, 95.00, 0.05, 0.93, 18, 0.95, 0, 1, 18, 2200.00, 500.00)
) AS scenario(
  email, kyc_passed, partner_orders_12m, partner_avg_order_value, partner_refund_rate,
  partner_ontime_ratio, partner_tenure_months, rocket_ontime_ratio, rocket_dpd30_12m,
  rocket_active_plans, rocket_tenure_months, income, credit_limit
)
JOIN users u ON u.email = scenario.email
ON CONFLICT (user_id) DO UPDATE SET
  computed_at = EXCLUDED.computed_at,
  kyc_passed = EXCLUDED.kyc_passed,
  partner_orders_12m = EXCLUDED.partner_orders_12m,
  partner_avg_order_value = EXCLUDED.partner_avg_order_value,
  partner_refund_rate = EXCLUDED.partner_refund_rate,
  partner_ontime_ratio = EXCLUDED.partner_ontime_ratio,
  partner_tenure_months = EXCLUDED.partner_tenure_months,
  rocket_ontime_ratio = EXCLUDED.rocket_ontime_ratio,
  rocket_dpd30_12m = EXCLUDED.rocket_dpd30_12m,
  rocket_active_plans = EXCLUDED.rocket_active_plans,
  rocket_tenure_months = EXCLUDED.rocket_tenure_months,
  income = EXCLUDED.income,
  credit_limit = EXCLUDED.credit_limit;
