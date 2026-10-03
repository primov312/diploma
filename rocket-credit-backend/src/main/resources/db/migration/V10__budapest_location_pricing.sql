-- HUF benchmarks for informational location reports, independent of USD affordability data.
CREATE TABLE location_price_datasets (
    dataset_version VARCHAR(64) PRIMARY KEY,
    city VARCHAR(40) NOT NULL DEFAULT 'Budapest' CHECK (city = 'Budapest'),
    country_code CHAR(2) NOT NULL DEFAULT 'HU' CHECK (country_code = 'HU'),
    housing_observed_at DATE NOT NULL,
    housing_retrieved_at TIMESTAMPTZ NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    district_average NUMERIC(18, 6) NOT NULL CHECK (district_average > 0),
    content_checksum CHAR(64) NOT NULL UNIQUE,
    source_url TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX location_price_single_active ON location_price_datasets ((active)) WHERE active;

CREATE TABLE location_district_prices (
    dataset_version VARCHAR(64) NOT NULL REFERENCES location_price_datasets(dataset_version),
    district_id VARCHAR(40) NOT NULL,
    district_number SMALLINT NOT NULL CHECK (district_number BETWEEN 1 AND 23),
    display_name VARCHAR(100) NOT NULL,
    rent_per_m2 NUMERIC(12, 2) NOT NULL CHECK (rent_per_m2 > 0),
    listing_count INTEGER NOT NULL CHECK (listing_count > 0),
    coff NUMERIC(12, 6) NOT NULL CHECK (coff > 0),
    source_url TEXT NOT NULL,
    PRIMARY KEY (dataset_version, district_id),
    UNIQUE (dataset_version, district_number),
    CHECK (district_id = 'budapest-' || (ARRAY['i','ii','iii','iv','v','vi','vii','viii','ix','x',
        'xi','xii','xiii','xiv','xv','xvi','xvii','xviii','xix','xx','xxi','xxii','xxiii'])[district_number])
);

CREATE TABLE location_venue_baselines (
    dataset_version VARCHAR(64) NOT NULL REFERENCES location_price_datasets(dataset_version),
    category VARCHAR(16) NOT NULL CHECK (category IN ('GROCERY', 'LIBRARY', 'GYM', 'CAFE', 'STARBUCKS')),
    benchmark_description TEXT NOT NULL,
    unit VARCHAR(20) NOT NULL,
    baseline_price NUMERIC(12, 2) NOT NULL CHECK (baseline_price > 0),
    currency CHAR(3) NOT NULL DEFAULT 'HUF' CHECK (currency = 'HUF'),
    source_url TEXT NOT NULL,
    observed_at DATE,
    retrieved_at TIMESTAMPTZ NOT NULL,
    evidence JSONB NOT NULL CHECK (jsonb_typeof(evidence) = 'object'),
    PRIMARY KEY (dataset_version, category),
    CHECK (unit = CASE category WHEN 'GROCERY' THEN 'BASKET' WHEN 'LIBRARY' THEN 'YEAR'
        WHEN 'GYM' THEN 'ENTRY' ELSE 'DRINK' END)
);

CREATE TABLE location_venue_prices (
    dataset_version VARCHAR(64) NOT NULL,
    district_id VARCHAR(40) NOT NULL,
    category VARCHAR(16) NOT NULL,
    estimated_price NUMERIC(12, 2) NOT NULL CHECK (estimated_price > 0),
    PRIMARY KEY (dataset_version, district_id, category),
    FOREIGN KEY (dataset_version, district_id) REFERENCES location_district_prices(dataset_version, district_id),
    FOREIGN KEY (dataset_version, category) REFERENCES location_venue_baselines(dataset_version, category)
);

-- Publishing an incomplete import is prohibited, including when SQL is run outside the importer.
CREATE FUNCTION validate_location_price_publication() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.active THEN
        IF (SELECT count(*) FROM location_district_prices WHERE dataset_version = NEW.dataset_version) <> 23
            OR (SELECT count(*) FROM location_venue_baselines WHERE dataset_version = NEW.dataset_version) <> 5
            OR (SELECT count(*) FROM location_venue_prices WHERE dataset_version = NEW.dataset_version) <> 115 THEN
            RAISE EXCEPTION 'A location pricing dataset requires 23 districts, 5 baselines and 115 prices';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER location_price_complete_before_publication
    BEFORE INSERT OR UPDATE OF active ON location_price_datasets
    FOR EACH ROW EXECUTE FUNCTION validate_location_price_publication();
