INSERT INTO demo_districts (district_id, display_name, city, country_code, sort_order)
SELECT 'budapest-' || lower(roman), 'Budapest District ' || roman, 'Budapest', 'HU', 10 + n
FROM unnest(ARRAY['I','II','III','IV','V','VI','VII','VIII','IX','X','XI','XII','XIII',
                 'XIV','XV','XVI','XVII','XVIII','XIX','XX','XXI','XXII','XXIII']) WITH ORDINALITY AS d(roman,n)
ON CONFLICT (district_id) DO NOTHING;

CREATE TABLE monthly_living_cost_datasets (
    dataset_version VARCHAR(80) PRIMARY KEY,
    housing_dataset_version VARCHAR(64) NOT NULL REFERENCES location_price_datasets(dataset_version),
    content_checksum CHAR(64) NOT NULL UNIQUE,
    snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'),
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    active BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX monthly_living_cost_one_active ON monthly_living_cost_datasets(active) WHERE active;

CREATE TABLE postal_code_districts (
    dataset_version VARCHAR(80) NOT NULL REFERENCES monthly_living_cost_datasets(dataset_version),
    postal_code CHAR(4) NOT NULL CHECK (postal_code ~ '^1(0[1-9]|1[0-9]|2[0-3])[1-9]$'),
    district_id VARCHAR(40) NOT NULL REFERENCES demo_districts(district_id),
    source_url TEXT NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (dataset_version, postal_code),
    CHECK (district_id = 'budapest-' || (ARRAY['i','ii','iii','iv','v','vi','vii','viii','ix','x','xi',
        'xii','xiii','xiv','xv','xvi','xvii','xviii','xix','xx','xxi','xxii','xxiii'])[substring(postal_code,2,2)::integer])
);

CREATE TABLE monthly_living_costs (
    dataset_version VARCHAR(80) NOT NULL REFERENCES monthly_living_cost_datasets(dataset_version),
    district_id VARCHAR(40) NOT NULL REFERENCES demo_districts(district_id),
    monthly_rent NUMERIC(12,2) NOT NULL CHECK (monthly_rent > 0),
    monthly_groceries NUMERIC(12,2) NOT NULL CHECK (monthly_groceries > 0),
    monthly_other NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (monthly_other = 0),
    monthly_total NUMERIC(12,2) NOT NULL CHECK (monthly_total = monthly_rent + monthly_groceries + monthly_other),
    currency CHAR(3) NOT NULL DEFAULT 'HUF' CHECK (currency = 'HUF'),
    PRIMARY KEY (dataset_version, district_id)
);

CREATE TABLE user_living_cost_profiles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revision INTEGER NOT NULL CHECK (revision > 0),
    dataset_version VARCHAR(80) NOT NULL REFERENCES monthly_living_cost_datasets(dataset_version),
    apartment_size NUMERIC(8,2) NOT NULL CHECK (apartment_size > 0 AND apartment_size <= 1000),
    rent_sharers INTEGER NOT NULL CHECK (rent_sharers BETWEEN 1 AND 20),
    grocery_quantities JSONB NOT NULL CHECK (jsonb_typeof(grocery_quantities) = 'object'),
    other_spending NUMERIC(12,2) NOT NULL CHECK (other_spending >= 0),
    source VARCHAR(20) NOT NULL CHECK (source IN ('DEMO_DEFAULT','USER_DECLARED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, revision)
);

ALTER TABLE affordability_jobs ADD COLUMN local_cost_context JSONB;
ALTER TABLE affordability_snapshots ADD COLUMN local_cost_context JSONB;

CREATE FUNCTION validate_monthly_cost_publication() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.active THEN
        IF (SELECT count(*) FROM monthly_living_costs WHERE dataset_version=NEW.dataset_version) <> 23
           OR (SELECT count(DISTINCT district_id) FROM postal_code_districts WHERE dataset_version=NEW.dataset_version) <> 23
           OR COALESCE(jsonb_array_length(NEW.snapshot->'groceries'),0) <> 12 THEN
            RAISE EXCEPTION 'Monthly costs require 23 districts, postcode coverage and 12 grocery products';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER monthly_cost_publication BEFORE INSERT OR UPDATE OF active ON monthly_living_cost_datasets
FOR EACH ROW EXECUTE FUNCTION validate_monthly_cost_publication();
