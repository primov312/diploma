-- Demo partner catalog: three stores in the same React app. Idempotent through the
-- UNIQUE slug / fixture_id constraints, so re-running a fresh migration set on a restored
-- database never duplicates rows.

INSERT INTO partners (slug, display_name, amount_cap) VALUES
  ('streambox', 'StreamBox',  600.00),
  ('markethub', 'MarketHub', 1500.00),
  ('threadly',  'Threadly',   800.00)
ON CONFLICT (slug) DO UPDATE SET display_name = EXCLUDED.display_name, amount_cap = EXCLUDED.amount_cap;

INSERT INTO products (partner_id, fixture_id, name, price)
SELECT p.id, c.fixture_id, c.name, c.price
FROM (VALUES
  ('streambox', 'streambox-basic-monthly',    'Basic plan (monthly)',        9.99),
  ('streambox', 'streambox-standard-monthly', 'Standard plan (monthly)',    15.99),
  ('streambox', 'streambox-4k-monthly',       '4K + HDR plan (monthly)',    24.99),
  ('streambox', 'streambox-premium-annual',   'Premium plan (annual)',     159.99),
  ('streambox', 'streambox-family-annual',    'Family plan (annual)',      199.99),

  ('markethub', 'markethub-earbuds',          'Wireless earbuds',          129.00),
  ('markethub', 'markethub-monitor-27',       '27" QHD monitor',           279.00),
  ('markethub', 'markethub-robot-vacuum',     'Robot vacuum',              349.00),
  ('markethub', 'markethub-espresso',         'Espresso machine',          499.00),
  ('markethub', 'markethub-standing-desk',    'Standing desk',             599.00),

  ('threadly',  'threadly-linen-shirt',       'Linen shirt',                59.00),
  ('threadly',  'threadly-denim-jacket',      'Denim jacket',               99.00),
  ('threadly',  'threadly-running-shoes',     'Running shoes',             129.00),
  ('threadly',  'threadly-wool-coat',         'Wool coat',                 189.00),
  ('threadly',  'threadly-leather-boots',     'Leather boots',             219.00)
) AS c(partner_slug, fixture_id, name, price)
JOIN partners p ON p.slug = c.partner_slug
ON CONFLICT (fixture_id) DO UPDATE SET name = EXCLUDED.name, price = EXCLUDED.price, partner_id = EXCLUDED.partner_id;
