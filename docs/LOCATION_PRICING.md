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

The location panel defaults to `budapest-priced-month`; `budapest-priced-week` contains all five categories across five districts. Enable **Allow location demo** and click **Run location analysis**. Each result shows an approximate HUF amount with its unit, coefficient and expandable source details. The annual membership label remains visible without opening details. Visit benchmarks retain their own units. The automatic monthly forecast below reuses these prices for affordability, with annual fees amortized and grocery visits excluded. Location history changes capacity through spending estimates; it does not change the risk-score weights.

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

## Monthly living costs and postal-code lookup

The reference dataset and original v3 behavior are documented below. For the current UI and credit workflow, see [automatic budgets and forecasts](#automatic-budgets-and-activity-forecasts--4-october-2026).

The **Living address and local cost context** panel resolves a Budapest district from the entered postal code and displays a **Monthly living costs** table. Rent and groceries are researched estimates; apartment size, rent sharing, food quantities and other spending are demo/user assumptions. The monthly dataset does not replace the five-product location-history basket or the 115 saved venue estimates.

### Postcode evidence

[Magyar Posta Partner Extra](https://secure.posta.hu/partnerextra_eng) publishes [ZipCodes.xml](https://httpmegosztas.posta.hu/PartnerExtra/OUT/ZipCodes.xml). The initial monthly snapshot was retrieved on 2026-10-03 and contains **161 exact regular Budapest postcodes covering all 23 districts**. For these regular codes, digits two and three identify the district. The importer selects codes actually present in the official list, and saves explicit canonical mappings, rather than accepting every number in a range. Examples: `1021 → budapest-ii`, `1051 → budapest-v`, `1239 → budapest-xxiii`.

Non-Budapest, unknown and special-purpose codes such as `1007` are unsupported. This mapping determines the district; it does not prove that a street or building exists. The backend validates the code again on address save, derives the district, and rejects a conflicting supplied district. Existing address revisions remain unchanged.

### Monthly food basket and defaults

The [initial monthly snapshot](../scripts/data/budapest-monthly-living-costs-2026-10-03.json) pins housing version `budapest-rent-2026-09-30-61436f58f428` and twelve regular Tesco product prices verified from their public product pages on 2026-10-03. Published housing observation and grocery retrieval dates remain separate; grocery pages have no published tariff date.

| Product | Published pack/weight price HUF | Pricing quantity | Default monthly quantity |
|---|---:|---|---:|
| [Milk](https://bevasarlas.tesco.hu/shop/hu-HU/products/210621123) | 315 | 1 litre | 8 litres |
| [Half-brown bread](https://bevasarlas.tesco.hu/shop/hu-HU/products/207803495) | 549 | 0.5 kg | 6 kg |
| [Medium eggs](https://bevasarlas.tesco.hu/shop/hu-HU/products/220181340) | 768 | 10 eggs | 30 eggs |
| [Parboiled rice](https://bevasarlas.tesco.hu/shop/hu-HU/products/100501436) | 579 | 1 kg | 2 kg |
| [Jonagold apples](https://bevasarlas.tesco.hu/shop/hu-HU/products/2004006222303) | 509 | 1 kg, loose | 4 kg |
| [Chicken breast](https://bevasarlas.tesco.hu/shop/hu-HU/products/203176258) | 2,030 | 1 kg, loose | 4 kg |
| [Cooking potatoes](https://bevasarlas.tesco.hu/shop/hu-HU/products/2004020330168) | 716 | 2 kg | 4 kg |
| [Four-egg spaghetti](https://bevasarlas.tesco.hu/shop/hu-HU/products/2005100501330) | 355 | 0.5 kg | 2 kg |
| [Vine tomatoes](https://bevasarlas.tesco.hu/shop/hu-HU/products/203274480) | 822 | 1 kg, loose | 2 kg |
| [Loose onions](https://bevasarlas.tesco.hu/shop/hu-HU/products/206481922) | 209 | 1 kg, loose | 1 kg |
| [Tesco trappista cheese](https://bevasarlas.tesco.hu/shop/hu-HU/products/2004010616075) | 2,035 | 0.7 kg | 1 kg |
| [Golden Imperial sunflower oil](https://bevasarlas.tesco.hu/shop/hu-HU/products/111272111) | 745 | 1 litre | 1 litre |

Starting housing assumptions are **50 m² and one person paying rent**, with **0 HUF other spending**. Grocery quantities describe one person's purchases and are not divided by rent sharers. These quantities are illustrative; the basket is not a researched consumption average or a complete household spending survey. Utilities, transport, dining out and other omitted purchases can be declared separately or included in other spending, without counting them twice.

```text
rent = district monthly asking rent per m² × apartment area / rent sharers
food_baseline = sum(package price / package quantity × monthly quantity)
groceries = food_baseline × (district rent per m² / unweighted district mean)
other = entered monthly amount, without district scaling
```

Calculations use decimal arithmetic and the unrounded housing coefficient, with HALF_UP rounding to two decimals for each final HUF category. Exact package normalization matters: eggs cost `768 / 10 = 76.8 HUF/egg`, not the displayed rounded 77; cheese uses `2035 / 0.7`, not the rounded shelf price 2,907 HUF/kg. Six-decimal unit prices and coefficients are display metadata only.

The default city food baseline is **31,083.14 HUF/month** before district scaling. District V's default rent is **350,000.00**, food **40,735.74**, and renting total **390,735.74 HUF/month**. Non-renters still see the rent benchmark for context, but their reference total excludes it.

### Import and refresh

Use the same isolated Python requirements and database variable as the location importer. Preview/export use the standard library. Apply Flyway V11 before importing monthly data; import the matching housing snapshot first.

```sh
python3 scripts/import_monthly_living_costs.py preview \
  --input scripts/data/budapest-monthly-living-costs-2026-10-03.json

scripts/.venv/bin/python scripts/import_monthly_living_costs.py import \
  --input scripts/data/budapest-monthly-living-costs-2026-10-03.json

python3 scripts/import_monthly_living_costs.py export \
  --input scripts/data/budapest-monthly-living-costs-2026-10-03.json \
  --output /tmp/budapest-monthly-costs

scripts/.venv/bin/python scripts/import_monthly_living_costs.py fetch \
  --housing-input scripts/data/budapest-location-prices-2026-09-30.json \
  --grocery-input scripts/data/budapest-monthly-living-costs-2026-10-03.json \
  --output /tmp/budapest-monthly-costs-refreshed.json
```

`fetch` refreshes the official postcode list. The explicit `--grocery-input` retains saved grocery prices and their original retrieval timestamps, with a printed notice; omit it to fetch all twelve exact Tesco products. Direct Tesco access currently returns 403, so a full grocery refresh can fail. Verify blocked products through their public pages and update a copy of the saved snapshot explicitly; validate with `preview`. A source failure does not overwrite the output file. Package, product or parser changes require review rather than a silently substituted item.

CSV export writes `monthly-living-costs.csv` and `postal-code-districts.csv`. Content checksums exclude retrieval timestamps and normalize row order, making equivalent imports idempotent. Price, mapping or housing-version changes produce a new dataset version. Publication inserts the snapshot, all postcodes and 23 default cost rows in one transaction; failure preserves the old active dataset.

V11 adds `monthly_living_cost_datasets`, `postal_code_districts`, `monthly_living_costs` and `user_living_cost_profiles`. It expands `demo_districts` to 23 entries while retaining existing IDs. PostgreSQL checks enforce valid districts, matching postcode digits, HUF prices, complete publication and one active dataset. Profiles have immutable revisions and pin a dataset; publishing a new dataset does not rewrite them.

### APIs, eligibility and reproducibility

All routes use the existing session and CSRF handling:

| Route | Behavior |
|---|---|
| `GET /api/address/postal-code?postalCode=1051` | District and mapping source/date/version |
| `GET /api/me/monthly-living-costs` | Saved/default profile, personalized HUF estimates, eligibility and evidence |
| `POST /api/me/monthly-living-costs/preview` | Unsaved calculation; no profiles, financial revisions or jobs are written |
| `PUT /api/me/monthly-living-costs` | Save assumptions with `expectedRevision`, preserve address verification, queue recalculation |

As of V12, the editable request includes only `apartmentSize` and integer `rentSharers`. Grocery quantities are fixed per person and other spending is automatically forecast. Requests that alter managed amounts are rejected. The earlier V11 interfaces accepted all twelve `groceryQuantities` and `otherSpending` for manual profiles. Preview optionally includes `postalCode`. Save uses `expectedRevision`; `refreshDataset: true` explicitly adopts the active dataset while preserving size and rent-sharing. Area is positive and at most 1,000 m², rent sharers 1–20, food quantities 0–1,000 with at most two decimals, eggs whole, and money bounded by the existing monetary range. Revision conflicts return a reload/review error. Unknown postcodes and missing datasets never become zero-cost or synthetic researched estimates.

The postcode input resolves only four-digit codes. Changing it clears stale results; cost assumptions can be saved only after that address is saved. **Save size and sharing** retains the pinned dataset. **Refresh cost estimates** adopts the latest published prices. The UI labels unsaved edits as preview, and sources distinguish researched prices from demo/user assumptions. The old synthetic source-extraction control is removed from this panel; legacy endpoints/data remain available for compatibility.

References become eligible only for the current `VERIFIED_DEMO` address with a matching postcode/district. Saving an address clears verification. Changing monthly assumptions preserves it. Both successful and unsuccessful image reviews queue a new calculation so a failed re-review removes formerly eligible floors. The prepared sample's `District V` label is normalized to the catalog's `Budapest District V`; all other evidence comparisons remain required. Verification still demonstrates matching synthetic content, not residence authenticity.

The shared backend resolver supplies both dashboard and application features. HUF categories are converted with the existing fixed **360 HUF/USD demo rate**, HALF_UP to USD cents; conversions can introduce a few forints of display difference when USD totals are rendered back to HUF. The conversion is an application convention, not a researched exchange-rate claim.

Historical behavior under **rules-v3 / affordability-v3** (preserved for older policies):

- Itemized expenses use `max(declared, reference)` separately for rent (renters only), groceries and other spending, then add separately declared utilities and transport.
- Aggregate expenses use `max(declared aggregate, applicable rent + groceries + other reference)`.
- Higher declared expenses are retained; other spending is never added twice. Missing or unverified references leave declared expenses in place.

The Java/Python contract adds optional `districtOtherReference`. Legacy v1/v2 policy behavior, model artifacts, weights and thresholds remain unchanged. Compose now selects `app/policy-v4.json`; deployment `.env` overrides must also select that file. Saved application decisions and history are preserved.

Every affordability job captures its address/profile revisions, selected price datasets, conversion rate and resolved references under the financial-state lock. `local_cost_context` is stored on jobs and resulting snapshots and returned as `localCostContext` by the affordability API. New profile/address/verification revisions increment the existing generation; old jobs become superseded. Dashboard and application feature preparation read the same immutable context. A bounded rollout task initializes default profiles for preexisting saved addresses and queues fresh estimates after the first import; later imports require explicit user adoption.

### Monthly verification commands

```sh
scripts/.venv/bin/python -m unittest discover -s scripts/tests -v
# Set LOCATION_PRICING_TEST_DATABASE_URL for temporary-schema transaction tests.

cd rocket-credit-analysis
.venv/bin/python -m pytest tests/test_monthly_affordability.py tests/test_rules.py tests/test_api.py -q

cd ../rocket-credit-backend
mvn -Dtest=MonthlyLivingCostsIntegrationTest,FeatureModeEqualityTest,ParallelFeaturePreparationTest test

cd ../demo-repository
npx tsc --noEmit
npm run build
npx playwright test e2e/monthly-living-costs.spec.ts --workers=1
```

Database tests cover coverage, exact units, snapshot identity, idempotence and rollback. Backend tests cover all imported codes, mismatch rejection, sharing, verification/re-review, profile conflicts, preview isolation, owner totals, application/dashboard reference consistency, pinned refreshes and saved contexts. Analysis tests cover expense modes, floor/max semantics, other-spending overlap and legacy policies. Browser checks use the imported local stack and prepared fixture to verify saved assumptions, sources, changed affordability amounts, unsupported codes, missing data and mobile layout.

### Verified local rollout — 3 October 2026

The local diploma stack runs Flyway V11 and `rules-v3`. Dataset `budapest-monthly-2026-09-30-8b6da5fc048d` is active with **23 monthly rows, 161 postcode mappings and 12 grocery products**. District V stores rent **350,000.00**, groceries **40,735.74** and total **390,735.74 HUF**. The original location-history dataset retains its 115 venue estimates.

Importer tests passed with PostgreSQL transaction checks (22 tests), analysis checks passed (29 tests), and backend monthly integration checks passed (6 tests), alongside the existing feature-mode, parallel preparation and location-pricing checks. Three monthly browser checks and three location-history browser checks passed against the rebuilt stack. The browser verified saved quantities after reload, owner rent exclusion, verification-preserving profile edits, captured affordability floors, source links, unavailable data and a 390 px layout. The private address-upload volume required an ownership repair; the runtime Dockerfile and deployment instructions now include it.

## Automatic budgets and activity forecasts — 4 October 2026

The current diploma uses **rules-v4 / affordability-v4**. Financial amounts are read-only: income, obligations and the minimum household expense baseline come from `demo_financial_profiles`, and users cannot submit monetary overrides to the financial API. They can save their housing situation, apartment size and rent-sharing count. Grocery quantities are the fixed per-person basket above. The manual grocery editor, other-spending amount, expense-mode selector and duplicate rent/grocery fields have been removed.

There is one `MonthlyCostResolver` for the dashboard and application preparation. It reads canonical rent/m² from `location_district_prices`, fixed monthly food estimates from `monthly_living_costs`, and activity prices from the existing `location_venue_prices`. No new pricing tables are introduced. The 23 monthly district rows remain the default rent/food reference; a user's activity forecast is calculated from those existing references and their saved reports, then pinned in the affordability job context.

### Forecast method

The next 30 days are estimated per activity category. Starting demo frequencies are **4 gym entries, 8 café drinks and 4 Starbucks drinks per month**, plus **one annual library membership divided by 12**. They are explicit demo assumptions, not researched consumption averages. Baseline prices use the saved address district and the same pinned housing/venue dataset.

The latest enabled location report is sufficient when it spans at least **seven inclusive calendar days** and includes at least **three priced gym/café/Starbucks visits**. A new `budapest-priced-month` scenario supplies 13 synthetic September visits across 30 days to demonstrate the calculation; existing scenarios remain available.

```text
projected_monthly_category = sum(priced visit amounts) × 30 / observed days
forecast_category = (monthly_category_baseline + projected_monthly_category) / 2
```

If history is missing, disabled, unpriced or too sparse, use the monthly category baseline. Categories absent from an otherwise sufficient report also retain their baseline. Grocery visits are excluded because the monthly food basket already accounts for groceries. Library visits represent one annual membership, amortized over 12 months; repeated visits do not incur repeated memberships. If library evidence exists in sufficient history, average its amortized benchmark with the address-district baseline. Unsupported places do not contribute.

The UI exposes the benchmark, projected amount and chosen forecast for each category, along with source URLs and dates. These are synthetic spending estimates, not confirmed purchases. The researched benchmark budget covers rent, groceries and these activities; utilities and transport are not individually priced. The supplied household expense baseline remains a floor for credit calculations rather than treating omitted categories as free.

```text
automatic_monthly_total = applicable rent + monthly groceries + activity forecast
```

With no sufficient history, District V's 50 m² / one-person renting example is **350,000.00 rent + 40,735.74 food + 55,775.79 activities = 446,511.53 HUF/month**. Non-renters exclude rent. Saved size/sharing changes preserve address verification; address changes still clear it.

### Credit behavior and rollout

Under v4, researched automatic costs are applied only for a current verified address with complete reference data. The supplied demo financial profile provides a household expense baseline that users cannot edit. Effective expenses are the greater of that baseline and the verified automatic budget. Missing or unverified references retain the supplied baseline; if both are unavailable, affordability is unavailable and an application requires review. Missing references never produce zero living expenses. Each HUF category is converted at the fixed demo 360 HUF/USD rate with HALF_UP cent rounding. The risk-model artifact, scoring weights and thresholds are unchanged. Older policy files and historical application decisions remain stored.

A successful location run or a location-permission change queues a new generation with the resolved forecast, report ID, baseline/report dataset versions and method `automatic-living-costs-v1`. Prior queued calculations become superseded; prior contexts remain reproducible. Social reports do not change this budget.

Flyway **V12** preserves historical revisions and adds new automatic financial revisions using supplied demo income, expense baseline and debt. Existing monthly profiles retain their dataset pin, apartment size and rent-sharing count; grocery/other monetary overrides are reset to managed defaults. No dataset reimport is required. The rollout worker queues contexts for saved addresses after migration. Publishing later datasets still requires explicit **Refresh cost estimates** adoption.

The financial save request now contains only `expectedRevision`, `housingSituation` and `expenseMode: "AUTOMATIC"`. The monthly profile save request contains `expectedRevision`, `apartmentSize`, `rentSharers` and optional `refreshDataset`. Older grocery fields are accepted only if they equal the managed default basket; nonzero manual other spending is rejected.


### Verified automatic rollout

On 4 October 2026, the rebuilt local stack applied V12 and reported `rules-v4` health. Existing data retained 23 monthly district rows and 115 location venue prices. Current financial revisions use AUTOMATIC mode; saved address contexts identify `automatic-living-costs-v1`.

Verification passed: 36 analysis tests, eight monthly backend integration tests, existing feature-mode/parallel/location checks, TypeScript compilation, frontend/backend builds and six live browser checks. Browser coverage includes removed monetary inputs, persisted size/sharing, sufficient-history averaging, sparse fallback, address verification, captured credit expenses, sources, unavailable datasets and 390 px layout. Historical reports, profiles and decisions remain stored.
