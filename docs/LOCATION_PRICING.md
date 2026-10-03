# Budapest location pricing

Location reports combine synthetic visits with researched HUF benchmarks. Housing uses advertised apartment rent per m² per month, taken from one date and source for all 23 districts. The venue rows estimate a category benchmark in each district; they do not identify actual establishments or infer spending.

## Calculation and initial snapshot

```text
district_average = sum(23 district mean rents) / 23
coff = district mean rent / district_average
estimated_price = category baseline × coff
```

Every district has equal weight. Decimal arithmetic uses the unrounded ratio for prices, HALF_UP cent rounding, and six decimal places for stored `coff`. The source's citywide listing average is not the denominator.

The [saved snapshot](../scripts/data/budapest-location-prices-2026-09-30.json) contains the [23-district rental table](https://negyzetmeterarak.hu/statisztika?tipus=lakas&ugylet=kiado), observed **2026-09-30**, with an unweighted mean of **5,341.304348 HUF/m²/month**. District II is 1.166382×, V is 1.310541×, and XXIII is 0.790069×. Retrieval timestamps are recorded separately; a page without a published tariff date has `observedAt: null`.

| Category | Unit | Initial baseline HUF | Source |
|---|---|---:|---|
| Grocery | Fixed basket | 2,720 | Tesco component pages below |
| Library | Annual adult membership | 5,800 | [FSZEK category I/II](https://fszek.hu/Entities/87/Events/konyvtarhasznalat-2026) |
| Gym | One regular entry | 5,999 | [Life1 Corvin](https://life1.hu/wp-content/uploads/2026/04/L1-Corvin-kezi-arlista-HU.pdf) |
| Café | Cappuccino, 180 ml | 1,490 | [Madal](https://madalcafe.hu/kmenu/) |
| Starbucks | Caffè Latte starting price, unspecified size | 1,540 | [Nyugati foodora delivery menu](https://www.foodora.hu/restaurant/o4sx/starbucks-nyugati) |

The basket is [1 L 2.8% milk](https://bevasarlas.tesco.hu/shop/hu-HU/products/210621123) (315 HUF), [500 g half-brown bread](https://bevasarlas.tesco.hu/shop/hu-HU/products/207803495) (549), [ten medium eggs](https://bevasarlas.tesco.hu/shop/hu-HU/products/220181340) (768), [1 kg rice](https://bevasarlas.tesco.hu/shop/hu-HU/products/100501436) (579), and [1 kg Jonagold apples](https://bevasarlas.tesco.hu/shop/hu-HU/products/2004006222303) (509). The refreshed apple page changed the planned basket from 2,710 to 2,720 HUF. Tesco's product pages were checked through the browser-accessible source during implementation; direct HTTP fetching currently returns 403.

Baseline amounts exclude delivery/service charges, membership card administration, gym magnetic cards, first-visit discounts and loyalty offers. The library row estimates an annual membership, not the cost of a visit. The Starbucks baseline is a published starting price, not a verified Tall cappuccino or an in-store price. Scaling these baselines is the selected model; district figures are labelled estimates even where a chain's actual tariff is uniform.

## Setup and commands

Run from the repository root. Preview and export need only Python 3.10+. Fetch and PostgreSQL import need the isolated dependencies:

```sh
python3 -m venv scripts/.venv
scripts/.venv/bin/pip install -r scripts/requirements-location-pricing.txt

python3 scripts/import_budapest_location_prices.py preview \
  --input scripts/data/budapest-location-prices-2026-09-30.json

python3 scripts/import_budapest_location_prices.py export \
  --input scripts/data/budapest-location-prices-2026-09-30.json \
  --output /tmp/budapest-location-prices
```

Apply Flyway V10 by starting the updated backend before importing. Set `LOCATION_PRICING_DATABASE_URL` in your environment to a PostgreSQL URL using the application role. The diploma runtime exposes PostgreSQL on localhost:5438. Do not commit credentials or put them in the snapshot.

```sh
scripts/.venv/bin/python scripts/import_budapest_location_prices.py import \
  --input scripts/data/budapest-location-prices-2026-09-30.json
```

A full refresh fetches all selected public pages and the gym PDF. A blocked source, unexpected product or changed parser structure fails without replacing the output file:

```sh
scripts/.venv/bin/python scripts/import_budapest_location_prices.py fetch \
  --output /tmp/budapest-location-prices-new.json
```

While Tesco blocks direct fetching, explicitly retain its saved, dated basket while refreshing housing and the other four benchmarks:

```sh
scripts/.venv/bin/python scripts/import_budapest_location_prices.py fetch \
  --grocery-input scripts/data/budapest-location-prices-2026-09-30.json \
  --output /tmp/budapest-location-prices-new.json
```

This option preserves the grocery retrieval dates and prints that it was not refreshed. To update a blocked basket, verify the exact five product pages and edit their prices, retrieval timestamps and matching basket sum in a copy of the snapshot. Validate it with `preview`, then import that file explicitly. No values are silently substituted.

Inspect the preview or CSVs before importing a new snapshot. The importer requires all 23 distinct districts at one housing date and all five category baselines with correct units, positive amounts and source evidence. Reusing the same content preserves its version and original stored evidence. Changes to prices, counts, observation dates or source evidence create a new checksum version; changed retrieval timestamps alone do not.

## Storage and reports

Flyway V10 creates four independent tables: `location_price_datasets`, `location_district_prices`, `location_venue_baselines`, and `location_venue_prices`. Composite keys prevent duplicates. Database publication checks require 23 districts, five baselines and 115 prices; the active-version index prevents multiple active datasets. The importer serializes publication and activates the complete dataset inside the insert transaction. Failures preserve the previous active version and older versions remain stored.

The Java backend captures the active dataset before calling analysis and includes its version in the LOCATION job deduplication key. `demo_signal_reports.report` persists both report-level `pricing` metadata and optional per-visit `pricing` containing `districtId`, `category`, `coff`, `baselinePrice`, `estimatedPrice`, `currency`, `unit`, description and source evidence. `pricingStatus` distinguishes `PRICED`, `UNSUPPORTED_PLACE`, `UNKNOWN_DISTRICT`, `NO_DATASET` and `PRICE_UNAVAILABLE`.

Existing API paths and report fields are preserved. There is no new public endpoint. Existing reports keep their snapshots; run analysis again to attach prices to a report that predates this feature. Python remains stateless and does not connect to the database. Place matching uses explicit aliases: grocery shop/store/market, library, gym/fitness, cafe/café/coffee shop, and Starbucks. Parks, stations and offices remain unpriced.

The location panel defaults to `budapest-priced-week`, which contains all five categories across five districts. Enable **Allow location demo** and click **Run location analysis**. Each result shows an approximate HUF amount with its unit, coefficient and expandable source details. The annual membership label remains visible without opening details. No combined spending total is calculated. USD affordability references, financial revisions and scoring inputs are independent.

## Verification

```sh
scripts/.venv/bin/python -m unittest discover -s scripts/tests -v
# Include database tests by setting LOCATION_PRICING_TEST_DATABASE_URL.
# They create/drop isolated schemas and need a role with CREATE SCHEMA permission.

cd rocket-credit-backend
mvn -Dtest=LocationPricingServiceTest,LocationPricingIntegrationTest test

cd ../rocket-credit-analysis
.venv/bin/python -m pytest tests/test_location_pricing_scenario.py -q

cd ../demo-repository
npm run build
npx playwright test e2e/location-pricing.spec.ts --workers=1
```

Backend integration tests use disposable PostgreSQL via Testcontainers. Browser tests require the updated running backend and imported snapshot. Tests cover source units, coefficient normalization and rounding, parser drift and promotions, rollback after inserts, idempotence, a refresh during analysis, old reports, missing references, supported/unpriced visits, mobile layout and financial isolation.

### Verified local deployment — 2026-10-03

- Active dataset: `budapest-rent-2026-09-30-61436f58f428`; PostgreSQL contains 23 districts, five baselines and 115 estimates, with one active dataset after restart.
- Passed: 15 importer tests including three PostgreSQL transaction tests; four Java pricing unit tests; two Java integration tests; two Python scenario tests; three Playwright browser tests.
- Passed: Java package build, React production build and TypeScript `--noEmit` check. Three small existing TypeScript issues were corrected while verifying the build: a nullable estimate guard, missing financial type aliases and a numeric dropdown value.
- Tested manual online refresh with the explicit saved grocery benchmark; its retrieval date was preserved. Full online grocery refresh remains subject to Tesco's HTTP access restrictions.
- The already-applied V9 schedule migration was missing from the checkout; its exact bytes were restored from the previously deployed backend archive before adding V10. Flyway validated all ten migrations on the local deployment.
