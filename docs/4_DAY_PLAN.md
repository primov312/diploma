# Rocket Credit — Four-Day Implementation Plan

Status: implementation plan, updated 2026-10-01. Four working days are available before the target deadline of 2026-10-08 (Europe/Budapest).

## Goal and scope

Deliver a coherent local diploma demo in four working days: working affordability and optional analysis, three populated demo stores, **repayment schedules only**, and an interactive customer dashboard/history. The seven-day deadline leaves three calendar days for access, asset review, and unexpected fixes; no fifth implementation day is assumed. Keep the current React app (`demo-repository`), Java backend (`rocket-credit-backend`), stateless Python analysis (`rocket-credit-analysis`), and PostgreSQL Compose runtime. See [affordability plan](AFFORDABILITY_FEATURE_PLAN.md), [demo scenarios](DEMO_SCENARIOS.md), [simplified architecture](DIPLOMA_ARCHITECTURE.md), and [runtime instructions](../rocket-credit-deployment/DIPLOMA_RUNTIME.md). The older [full web application plan](FULL_WEB_APPLICATION_PLAN.md) and [partner integration guide](partner_intergration.md) describe a wider product and are not the contract for this four-day demo.

Decisions from the project owner: retain the three partner records and show their real-world prototype names for this private diploma demo: Netflix replaces StreamBox, Amazon replaces MarketHub, and Zara replaces Threadly. The Netflix catalog mixes films, series, subscriptions, and bundles; all listed prices are illustrative demo prices, not claims about Netflix offers. A script should obtain product imagery from a free stock source. Repayments show a **projected schedule only**: no plan activation, paid/overdue status, payment recording, or money movement. Use an academic-demo/no-affiliation label wherever real brand names appear; stock photos illustrate catalog entries and are not official listings or posters.

### Time budget and dependency order

| Workday | Main deliverable | Required first | Checkpoint |
| --- | --- | --- | --- |
| 1 | Verified finances, address context, optional-analysis isolation, consistent decision math | Existing stack/fixtures | One complete dashboard → application flow |
| 2 | Three branded catalogs with ≥30 priced and imaged entries each | Partner naming and image-source manifest | One product handoff per store |
| 3 | Frozen six-month projected schedules for approved applications | Day 1 policy contract | Schedule visible after reload; no payment state |
| 4 | Dashboard/history redesign and full browser walkthrough | Days 1–3 APIs | Fresh-start demo and accurate documentation |

Timebox optional Day 1 research tasks after the core flow passes. Use the three non-working calendar days for responses/credentials, checking the photo manifest, and rerunning the demo, rather than expanding the feature scope.

### Current baseline

- The affordability `rules-v2` / `affordability-v2` calculation, financial edits, saved graph, address revisions, seeded Budapest district costs, fixture/Gemini adapters, and optional synthetic location/social panels are present. The affordability plan records remaining tests, historical snapshots, live provider proof, and some background work. Its “planning only” header is stale.
- StreamBox, MarketHub, and Threadly have five seeded products each. `/api/partners/{slug}` returns names and prices; the store UI uses emoji placeholders. There are no product image or logo fields in the catalog contract. Keep internal slugs/IDs stable when changing visible names so old applications and links still resolve.
- Applications persist `APPROVED`, `REVIEW`, and `REJECTED` results, but there is no repayment schedule schema, API, or UI. The former repayment microservice was removed from the diploma runtime.
- The affordability formula assumes six monthly payments with zero interest and fees. `PaymentCalculator.tsx` currently advertises different schedules and interest, so its copy/calculation must be aligned with the real demo terms.
- `Layout.tsx` currently puts the same large marketing header/footer around account pages. `Footer.tsx` contains placeholder `#` links and unsubstantiated copy; `AccountDashboard.tsx` stacks long panels before history. Several frontend files have uncommitted edits. Review their diffs and preserve that work before touching them.

### Rules for all four days

1. Use backend product prices, saved financial revisions, and versioned policy as authoritative values. Store links carry partner/product IDs, not a trusted price.
2. Keep synthetic inputs, reference values, product data, and projected schedules visibly labeled. Use USD, as required by the current schema; Budapest reference values are synthetic USD context.
3. Add Flyway migrations after V6. Do not alter applied migrations or rewrite old decisions.
4. Keep location/social reports informational: they have zero weight in both affordability and credit scoring.
5. End each day with a focused integration check so Day 4 is for final browser quality rather than first discovery of backend failures. If time runs short, prioritize the four checkpoint flows above over optional Day 1 provider/publication work or decorative catalog polish.

## Day 1 — Complete and verify existing functions

**Outcome:** financial inputs, address/local costs, optional synthetic analysis, and decision math agree from dashboard to saved application.

### 1. Establish a working baseline

1. Record the working-tree state, start the diploma Compose stack, check health and the reported `rules-v2` policy, and log in with a seeded account.
2. Run available focused Python, Maven, frontend build/type, and Playwright checks. Record each failure as pre-existing or introduced. Missing Docker or provider credentials count as unverified, not passed.
3. Add deterministic fixtures for the documented `$3,000` example (`$2,250` base and `$1,500` at internal partner `markethub`, displayed as Amazon) plus missing versus zero income, debt limit, partner cap, round-down cents, aggregate/itemized mode, and rental-floor exclusions.

### 2. Financial information and decision model

1. Walk through `FinancialInputsForm` → `PUT /api/me/financial-inputs` → generation-backed recalculation → graph/breakdown → `POST /api/applications`. Confirm the application uses the displayed input revision, formula version, and partner cap.
2. Fix mismatches in validation, amount explanation, v2 reason text, stale estimate handling, and application detail. Handle `409 REVISION_CONFLICT` and `409 INPUTS_CHANGED` with a clear refresh/retry path.
3. Verify that increasing expenses or debt cannot increase capacity; zero capacity differs from missing evidence; and `REVIEW` remains inconclusive.
4. If the presentation needs a full 12-month graph, seed twelve explicitly synthetic input scenarios for selected demo personas, calculate them with the shared formula, and label them “Demo history.” Leave ordinary users' missing months as gaps.

### 3. Living address and local costs

1. Exercise the prepared Budapest address card: match, mismatch, uncertainty/invalid file, edited address, and unavailable provider. Confirm only the current accepted address revision activates rent/grocery references.
2. Display effective rent/grocery floors, reference version/source, and the resulting amount change. Show city salary only as comparison context, never as assumed personal income.
3. If publication is required for the four-day demo, complete the documented local-cost extraction/publication path: validate evidence and units, publish an immutable reference version, then recalculate affected verified-address users. Otherwise keep extraction explicitly labeled as preview and document publication as deferred.

### 4. Optional synthetic activity

1. Verify permission toggles, synthetic location aggregates, social evidence IDs, and fixture-versus-AI labels. Revoked permission must prevent a late result from publishing.
2. Confirm that changing or running location/social scenarios changes neither financial inputs nor affordable amount nor decision features.
3. Move optional provider runs to the existing leased-job pattern if synchronous requests block or time out during the demo; keep visible persisted progress and recoverable failure states.
4. When a server-side `GEMINI_API_KEY` is available, run one live request on prepared synthetic evidence and record provider, model, prompt version, evidence, and outcome. Fixture playback stays the offline path and is never labeled as a live run.

**Day 1 exit:** a seeded customer can edit finances, verify the prepared address, inspect the updated estimate, run optional analysis, and save an application whose possible amount matches the selected partner estimate. The formula and signal-isolation checks pass. Credential-dependent work is recorded as pending if no key exists.

## Day 2 — Fill the demo marketplace

**Outcome:** Netflix, Amazon, and Zara each show at least 30 distinct entries with illustrative USD prices, working stock images, and a reliable application handoff.

### 1. Define product and asset data

1. Map stable internal slugs to display brands: `streambox → Netflix`, `markethub → Amazon`, `threadly → Zara`. Keep partner primary keys, slugs, old product fixture IDs, and saved application references unchanged. Update visible store names, navigation, histories, and source labels through an additive migration/frontend mapping.
2. Build a manifest of at least 30 entries per partner with stable fixture ID, internal partner slug, demo name, category/short description, USD **demo** price, search query, local image path, alt text, and source metadata. Netflix entries mix films, series, subscription plans, and bundles; use invented titles/illustrative prices so the app does not claim official Netflix title pricing. Amazon uses electronics/home and Zara uses clothing. Include a few entries above partner caps for a clear suggestion/rejection example.
3. Use `scripts/fetch_catalog_images.py` to download the reviewed photos pinned in `scripts/catalog_image_sources.json` without an API key. Select relevant images from Wikimedia Commons, Pexels, or CC0 stock collections, cache bounded JPEGs in local frontend assets, and record author/credit reference, photo page, license URL, search phrase, and download date. Reruns reuse completed files and fetch changed source URLs; `--replace FIXTURE_ID` downloads a pinned source again.
4. Add bounded requests, retry/backoff for rate limits, duplicate-photo detection, and a report of missing/unusable results. Review all 90 matches manually for relevance and offensive or misleading content; replace poor matches by explicit photo IDs. The script runs during preparation, not when the customer loads a page, so the demo works offline.
5. Provide a visible credits page with each file's author/credit reference, source, and license, following the [Commons reuse guide](https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia) and [Pexels License](https://www.pexels.com/license/) for the relevant photos. Stock photos illustrate the demo entries. Use a clean text wordmark for each brand.

### 2. Extend the catalog safely

1. Add the next Flyway migration for partner display-name updates and product image path/description/category fields, with suitable constraints. Add products using stable fixture IDs and idempotent seed/update behavior. Preserve existing product IDs referenced by saved applications.
2. Extend `ProductEntity`, `PartnerDtos.Product`, TypeScript `Product`, and `/api/partners/{slug}`. Only allow safe local asset paths, with a fallback if an image is missing.
3. Verify at least 30 distinct positive-price items per partner, valid images, and no duplicates after repeat startup.

### 3. Improve browsing and handoff

1. Replace emoji product placeholders with reviewed stock images and the new visible brand names. Add category filtering/search or pagination so 30 cards remain usable on mobile. Give Netflix filters for films, series, plans, and bundles.
2. Show name, **demo price**, image/source credit, no-affiliation label, and “Apply with Rocket Credit” on each entry. Preserve the existing login return path and backend price resolution. Make clear that selecting a media title is an illustrative financing scenario, not a real Netflix checkout.
3. Check loading, empty, missing-image, long-name, above-cap, and narrow-screen states. Submit one product application from each partner and confirm saved partner/product/price.

**Day 2 exit:** the three display brands and source-checked logos/text treatments load, each partner returns at least 30 priced and imaged demo entries, the image manifest is reviewable, and fresh seed/offline refresh/product handoff all work.

## Day 3 — Show projected repayment schedules

**Outcome:** each new approved application has a frozen, read-only six-month schedule. No payment tracking is implemented.

### 1. Lock the schedule contract

1. Use the current `affordability-v2` assumption: six **monthly** installments, zero interest, zero fees, USD. Call the output a **projected schedule**, because approval is not a loan agreement or payment collection.
2. Define a stable UTC anchor from the saved decision date. The first illustrative due date is one calendar month later; subsequent dates use the same day-of-month with documented month-end clamping. Use integer cents/`BigDecimal`: make the first five installments equal and put any cent remainder into the sixth, so the total equals the requested amount exactly.
3. Include application ID, amount, currency, policy/formula version, schedule version, anchor date, six due dates/amounts, and total. If an application is `REVIEW` or `REJECTED`, show its decision and possible-amount suggestion but no repayment schedule.

### 2. Save and expose the schedule

1. Add an additive Flyway migration for an optional immutable schedule snapshot on `credit_applications` (or a one-to-one schedule table if preferred). Do not rewrite historical decisions. Generate the snapshot in Java after scoring and save it in the same transaction as a newly approved application.
2. Extend the owner-scoped application detail API to return the saved schedule. The list API may return only a `hasProjectedSchedule` flag for compact cards. Existing approved rows without a snapshot display “schedule unavailable for this older decision”; they are not silently recalculated under today's terms.
3. Keep the schedule separate from seeded purchase history and scoring inputs. There are no payment events, plan activation endpoints, paid/overdue states, or external provider calls.

### 3. Build the schedule UI and verify it

1. Add a schedule section on approved application details: six numbered dates and amounts, exact total, zero interest/fees, policy label, and a visible “Illustration only — no payments are due” note. Add a compact “Projected schedules” list/shortcut from the dashboard if it helps navigation.
2. Align `PaymentCalculator.tsx` with the six-month, zero-fee terms, or remove its alternative plan selector if it cannot be made truthful in the timebox. Remove unsupported public claims about auto-pay, rescheduling, late fees, interest, and credit-bureau effects.
3. Test small and large amounts, cent remainders, leap-year/month-end dates, policy version capture, approval/review/rejection behavior, owner-only reads, and persistence after refresh/restart.

**Day 3 exit:** an approved application shows the same six amounts and dates after reload, their sum equals the requested amount, review/rejection show no schedule, and another user cannot read it.

## Day 4 — Fix UI and verify the complete demo

**Outcome:** the account area works as an interactive dashboard, purchase/application history is easy to inspect, and the entire demo can be repeated.

### 1. Replace the inefficient page skeleton

1. Split the public store/login shell from the authenticated account shell in `Layout.tsx`/routes. Use a compact account top bar plus clear dashboard, history, applications, stores, and schedules navigation; use a responsive side rail or compact mobile menu so navigation does not consume the dashboard's vertical space. Keep sign-out/account identity easy to find.
2. Remove the large marketing footer from authenticated pages. On public pages, replace `Footer.tsx` placeholder `#` links, fake social links, and unsupported claims with a small set of real routes and a plain academic-demo note; remove the footer entirely if that is clearer. Keep only truthful contact/legal links if actual destinations exist.
3. Review `HomePage.tsx`, `AboutUs.tsx`, `HowItWorks.tsx`, and `ForBusinesses.tsx` for fabricated customer counts, funding, certifications, bank/card features, or payment promises. Remove or rewrite claims that the runtime cannot demonstrate. Keep the app focused on the diploma scenario rather than a fictional production company.

### 2. Make dashboard and history useful

1. Reorganize `AccountDashboard.tsx` into a scannable overview: current affordable amount and freshness, short monthly trend, recent applications, recent purchase history, and a link to projected schedules above the fold. Move the long finance/address/optional-analysis editors into clearly named tabs, sections, or dedicated routes; saving still refreshes the overview.
2. Make the history experience interactive without inventing data: partner filter, date range or search, status filter, chronological rows/cards, useful empty state, and responsive detail layout. Keep seeded purchases, credit applications, and projected schedules separate and clearly labeled. Build summaries from returned records, not hard-coded totals.
3. Give graph points keyboard/tooltips and a data table, preserve the selected filter during navigation where practical, and keep loading/stale/error states visible without displacing the whole dashboard. Check long names, narrow widths, and focus order.
4. Align spacing, typography, product image crop, button labels, status colors plus text, accessible labels, and responsive navigation. Preserve in-progress edits. Make synthetic data, capacity versus approval, fixture versus live AI, and projected schedules clear at the point of use.

### 3. Run the end-to-end acceptance path

1. Fresh-start the three-service stack, seed data, verify migrations and health, and log in as a seeded persona.
2. Change finances, inspect the graph, verify the prepared address, run optional synthetic analysis, and confirm it does not affect the estimate.
3. Browse Netflix, Amazon, and Zara; submit a product application and inspect the persisted decision. Open an approved application's projected six-month schedule, reload, and confirm it is unchanged. Exercise rejection/review and confirm neither has a schedule.
4. Filter purchase and application history, open details, change dashboard sections, and verify all displayed numbers come from saved records.
5. Check browser back/refresh, two-account ownership, keyboard navigation, and a narrow viewport. Capture screenshots or concise evidence for the diploma.

### 4. Update documentation and evidence

1. Update `DEMO_SCENARIOS.md` with the brand mapping, catalog examples, a projected-schedule example, and current `rules-v2` expectations. Update runtime instructions for the new migration, no-key stock-image script, curated source/asset manifests, and any new routes.
2. Record test commands/results and remaining limitations. Separate deterministic fixture evidence from any live Gemini result; do not claim a successful live call until observed.
3. Recheck that the calculator, public copy, affordability explanation, application decision, and projected schedule all state the same terms. Finish with a reproducible 5–10 minute demo script.

**Day 4 exit:** the flow runs after a fresh start, account navigation/dashboard/history are efficient on desktop and mobile, placeholder/fabricated marketing content is gone, the demo is reproducible offline, and docs state exactly what was verified.

## Completion boundary

The four working days are complete when the Day 1–4 exit checks pass within the seven-day calendar window. Live Gemini proof, local-cost publication, twelve synthetic historical points, and optional job backgrounding are valuable follow-ups but do not displace the agreed catalog, schedule, and dashboard deliverables if time is tight. Record which of those follow-ups actually ran. No real checkout, licensed Netflix/Amazon/Zara inventory feed, loan servicing, payment collection, or public deployment is claimed by this demo.
