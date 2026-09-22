# Affordable Credit Dashboard — Implementation Plan

Status: planning only. Implementation has not started.

## 1. Goal and agreed scope

Add a dashboard that shows an estimated affordable credit amount, explains the inputs behind it, and displays its monthly history in a green graph. Users can update financial information and provide an address with supporting evidence. Relevant saved changes automatically produce a new estimate.

The diploma demonstration also includes a working process for analyzing synthetic location history and synthetic Instagram/Facebook-style activity. These results are informational and do not influence credit eligibility, the scoring model, or the affordable amount.

The following decisions incorporate the notes added to the original plan:

| Topic | Implementation direction |
| --- | --- |
| Main graph | Green line/area chart, monthly points, default last 12 months |
| Financial inputs | User-editable income, living expenses, and obligations |
| Address | AI-assisted review of a sample letter/address card; matched district selects local expense references |
| Salary and expenses | Financial amounts affect affordability; city salary is comparison context, never assumed personal income |
| Location history | Synthetic Google Maps-style visit data, visibly demonstrated in the app |
| Social analysis | Working analysis of synthetic posts, locations, comments, reactions, and account consistency |
| Social account access | No Google/Meta account connection in this version |
| Demo setting | Fictional Demo City and districts; USD to match the existing application's contracts |
| Consent/retention scope | A simple permission toggle for optional analysis; no production consent portal, legal workflow, or retention-management feature |
| Formula | Documented below before code changes; constants are illustrative demo policy choices |

This is an academic simulation with synthetic evidence and no lending, payment, or legal compliance claim. A real jurisdiction has not been selected. Mapping it to a real country or real financial decisions is a separate project requirement.

## 2. Existing implementation and integration points

| Current code | Current behavior | Planned change |
| --- | --- | --- |
| `demo-repository/src/pages/AccountDashboard.tsx` | Read-only synthetic finances, purchases, and saved applications | Add affordability summary, monthly graph, input editors, and analysis panels |
| `demo-repository/src/api/{client,rocket,types}.ts` | Same-origin session requests with CSRF protection | Add typed APIs for financial inputs, address, estimates, and analysis jobs |
| `rocket-credit-backend/.../users/ProfileController.java` | `GET /api/me/profile`; no financial write endpoint | Preserve the existing response and introduce a separate versioned financial-input API |
| `rocket-credit-backend/.../applications/features/DbFinanceFeatureProvider.java` | Reads three totals from `demo_financial_profiles` | Read one immutable, resolved financial-input revision |
| `rocket-credit-backend/.../applications/ApplicationService.java` | Prepare features → call Python → persist decision and snapshot | Use the same affordability inputs and policy as the dashboard; record their revisions |
| `rocket-credit-analysis/app/rules/affordability.py` | `min(partnerCap, max(0, income - expenses - obligations) × 3)` | Introduce the versioned formula below, shared by estimate and scoring endpoints |
| `rocket-credit-analysis/app/rules/combiner.py` | Affordability is a hard amount check, separate from profile/history scores | Preserve that separation and surface new missing-data reasons |
| `rocket-credit-analysis/app/ai.py` | Offline logistic-regression credit-risk model | Keep credit-risk inference distinct from document/social AI processing |
| `rocket-credit-backend/src/main/resources/db/migration/` | V1 schema, V2 partner/product seed | Add new Flyway migrations; do not edit applied migrations |

In the Java paths above, `...` stands for `src/main/java/com/rocketcredit/backend`.

Keep the existing three-service runtime: React served by the Java backend, PostgreSQL owned by Java, and stateless Python analysis. No additional broker, microservice, or live social-platform integration is required.

## 3. User journey

1. The user signs in and opens the dashboard. The current estimate, its calculation date, and monthly history appear above the existing account cards.
2. The user opens **Financial information**, enters monthly net income, expense categories, and existing monthly debt payments, and saves.
3. The backend saves the new input revision and schedules recalculation. The dashboard shows the last estimate as updating until the new revision is ready.
4. The user opens **Living address**, selects a demo district, enters the address, and submits a sample letter/address card for review.
5. The app shows extraction and address-match results. An accepted match activates the district's reference expenses for subsequent estimates.
6. The user sees the change in amount and an explanation such as “Estimated grocery costs increased by $50.”
7. With optional analysis enabled, the user loads a location or social scenario and runs analysis. The app shows progress and evidence-linked findings.
8. The user selects a partner and applies. The application's `possibleAmount` matches the dashboard for the same inputs, policy, and partner cap. Approval still depends on the existing profile/history/risk checks.

Saving valid information can increase, decrease, or leave the amount unchanged. Adding data does not automatically improve creditworthiness.

## 4. Affordability policy: `affordability-v2`

Use `affordability-v2` as the formula version inside a new overall scoring policy `rules-v2`. Keep those identifiers distinct from the independent credit-risk model version. The active policy reported by `/policy`, health responses, estimates, and newly saved applications must agree.

### 4.1 Input definitions

All financial amounts are monthly USD, non-negative, with at most two decimal places. Use Java `BigDecimal` and Python `Decimal` for authoritative calculations.

| Symbol | Input | Meaning |
| --- | --- | --- |
| `I` | `monthlyNetIncome` | User's declared net monthly income; explicitly marked as declared or synthetic, not independently verified |
| `R` | `housingCost` | User's monthly housing payment, including zero where valid |
| `G` | `groceriesCost` | User's share of monthly groceries |
| `U` | `utilitiesCost` | Monthly utilities |
| `T` | `transportCost` | Monthly transport |
| `O` | `otherLivingCosts` | Other living costs; excludes debt repayments |
| `D` | `monthlyObligations` | Existing monthly debt repayments, counted once |
| `Rref`, `Gref` | District reference values | Per-person monthly housing/grocery references, with source and version |
| `L` | `legacyLivingExpenses` | Existing aggregate expenses when category details have not yet been entered |

The first demo models one adult's own income and share of costs. Store `housingSituation = RENTING | OWNER | FAMILY | OTHER`; do not apply a rental floor to an owner or someone living with family. Household-level estimation and currency conversion are deferred.

Missing and zero are different values. An explicit zero income means no capacity; a missing income means insufficient evidence. Never fill missing personal income with a city average salary.

### 4.2 Resolve expenses

Use district references only if the current address revision has passed the demo verification checks and a compatible reference dataset exists.

For complete category inputs:

```text
effectiveRent = max(R, Rref)       if RENTING and district references are eligible
                R                otherwise
effectiveGroceries = max(G, Gref) if district references are eligible
                     G           otherwise
E = effectiveRent + effectiveGroceries + U + T + O
```

For accounts still using their legacy aggregate:

```text
localFloor = (Rref if RENTING else 0) + Gref
E = max(L, localFloor)            if district references and housing situation are available
    L                            otherwise
```

This compatibility mode is labeled **Aggregate expenses**. Do not fabricate category splits, and never add `L` to category totals. Switching to category mode requires all categories to be present, with zero allowed. If neither a complete category set nor a legacy total exists, the estimate is unavailable.

If address verification or references are unavailable, use declared expenses and expose `LOCAL_COSTS_NOT_APPLIED`. The calculation is still a demo estimate; verification of an address does not verify income.

City salary research displays the source's monthly net/gross basis, period, currency, and geographic coverage. A salary comparison is shown only when bases are compatible. It is not a multiplier, an income replacement, or a reason for approval/rejection.

### 4.3 Calculate capacity

Proposed versioned constants:

```text
reserveRate = 0.10
disposablePaymentShare = 0.50
totalDebtServiceRate = 0.30
termMonths = 6
demoInterestRate = 0
demoFees = 0

reserve = I × reserveRate
disposableAfterReserve = max(0, I - E - D - reserve)
paymentFromDisposable = disposableAfterReserve × disposablePaymentShare
paymentFromDebtLimit = max(0, I × totalDebtServiceRate - D)
monthlyPaymentCapacity = max(0, min(paymentFromDisposable, paymentFromDebtLimit))
baseAffordableAmount = monthlyPaymentCapacity × termMonths
partnerAffordableAmount = min(baseAffordableAmount, partnerCap)
```

Round `monthlyPaymentCapacity` down to cents before multiplying by the term. Round final monetary outputs down to cents and use the same rules in both endpoints. Retain sufficient precision in intermediate calculations and serialize display components consistently.

The amount represents six equal monthly payments under the demo's zero-interest, zero-fee assumption. It is not a repayment schedule or an offer. Avoid adding selectable terms until both the form and application contract can preserve the selected term.

### 4.4 Weights and decision rules

- Income, expenses, obligations, reserves, and partner caps use the formula above; they are not averaged into a score.
- Address affects the amount only through eligible expense references. There is no neighborhood desirability, reputation, or location-risk score.
- Social and location findings have exactly zero weight and are excluded from credit model inputs.
- Keep the existing profile/history weights (`0.4`/`0.6`) and optional rules/risk-model blend (`0.8`/`0.2`) unchanged initially.
- An amount within capacity can still receive `REVIEW` or `REJECTED` from the existing score. Display the distinction between estimated capacity and an application decision.
- Missing required financial evidence yields an unavailable dashboard estimate (`baseAmount: null`) and `REVIEW` on submission. Known zero capacity yields `baseAmount: 0` and rejects a positive request.

### 4.5 Worked example and expected changes

Assume net income `$3,000`, rent `$900`, groceries `$300`, utilities `$150`, transport `$100`, other costs `$150`, and debt obligations `$200`. The verified district references are rent `$1,000` and groceries `$350`.

```text
Effective expenses       = 1,000 + 350 + 150 + 100 + 150 = 1,750
Reserve                  = 3,000 × 0.10                 =   300
Disposable after reserve = 3,000 - 1,750 - 200 - 300    =   750
Payment from disposable  = 750 × 0.50                   =   375
Payment from debt limit  = 3,000 × 0.30 - 200           =   700
Monthly payment capacity = min(375, 700)                =   375
Base affordable amount   = 375 × 6                     = 2,250
MarketHub amount         = min(2,250, 1,500)            = 1,500
```

Before district references apply, declared expenses are `$1,600` and base capacity is `$2,700`. Applying the references reduces it to `$2,250`; the MarketHub amount remains `$1,500` because its cap still applies. Changing social reactions or location visits must change neither value.

### 4.6 Existing risk model compatibility

The current risk model's `utilization` feature hardcodes disposable income multiplied by `3.0`, and the same mapping exists in `research/training/common.py`. Do not silently redefine that feature for the existing `logreg-v1` artifact.

For the first release, preserve its trained feature definition and document that it is a separate risk-model feature, while the hard affordability limit uses `affordability-v2`. Feed it resolved expense totals consistently. Evaluation must measure changes caused by those totals. Changing the feature meaning requires a new dataset evaluation, training artifact, and model version as a later step.

## 5. Financial information and living address

### 5.1 Financial editor

- Add a financial-information form within the protected account area.
- Support `AGGREGATE` and `ITEMIZED` expense modes during migration, and show which is active.
- Validate amounts, required fields, decimal precision, and server limits; match database `NUMERIC(12,2)` bounds and guard calculated overflow.
- Send the last-read revision with each save; reject stale edits with `409 REVISION_CONFLICT` so simultaneous tabs cannot overwrite each other silently.
- Preserve source labels such as `STARTER`, `FIXTURE`, and `USER_DECLARED`. Editing never upgrades an input to verified income.
- After save, invalidate frontend queries for profile, estimates, and history; do not calculate an authoritative amount in React.

### 5.2 Address input and evidence

Store structured fields: country code, city, district identifier, postal code, street, building/unit, and revision. Use a controlled district catalog for the demo so arbitrary AI output cannot select a different cost dataset.

For the first release, “letter verification” means review of an existing sample document containing the address. Physical mail delivery and an address challenge code are not part of this implementation.

Offer prepared synthetic JPG/PNG documents and a bounded image upload (proposed 5 MB limit). PDF support can follow later. Uploads use a separate multipart helper that sends the existing CSRF header and lets the browser set the multipart boundary; the current JSON-only client cannot upload them unchanged.

### 5.3 Verification process

1. Validate image size and actual file type; assign an opaque file ID and associate it with the current user/address revision.
2. Enqueue an address-analysis job and show progress.
3. Send the image to a configured vision/OCR provider, or return the explicit fixture result in offline mode.
4. Validate structured output: extracted addressee/address, document type, issue date if present, extraction confidence, and evidence references.
5. Normalize address fields and compare them deterministically with the submitted address. Require agreement on district, street, building, and unit where supplied; addressee must match the account's demo name.
6. Assign `VERIFIED_DEMO`, `NEEDS_REVIEW`, or `MISMATCH`. Missing, ambiguous, or low-confidence extraction becomes `NEEDS_REVIEW`; technical failures remain job failures.
7. On an accepted result, resolve cost references and trigger recalculation. A result for an outdated address revision must never verify the replacement address.

Address lifecycle: `UNVERIFIED → PENDING → VERIFIED_DEMO | NEEDS_REVIEW | MISMATCH`. Editing any address field resets verification. The demo has no staff review queue; users can correct input or resubmit evidence.

`VERIFIED_DEMO` means the sample evidence passed the documented checks. It does not establish document authenticity or physical residence. AI confidence alone cannot set the status.

## 6. Local expense and salary research

### 6.1 Deterministic reference data first

Create three fictional districts with different rent/grocery costs and a city salary reference. Each record includes geographic scope, per-person basis, currency, monthly period, gross/net salary basis, source label, observed date, dataset version, and `synthetic: true`.

Only rent and grocery references enter expense resolution. Display city salary separately. Demo records are explicitly fictional and do not cite invented real-world statistics or URLs.

### 6.2 Working AI research process

Introduce a `LocalCostResearchProvider` that accepts a geographic scope and returns structured candidate references with source evidence. Provide two modes:

- `FIXTURE`: deterministic reference documents and recorded extraction results for an offline demonstration.
- `AI`: real model extraction from supplied reference documents; an optional search adapter can later retrieve public sources for a configured real city.

For the complete demo, run actual AI extraction against prepared source documents and show the extracted values and supporting passages. Calling a model on synthetic documents is still a working analysis process; mark both `dataSource: SYNTHETIC` and `analysisMode: AI`.

Validate candidates before publication: non-negative amounts, matching district/city, compatible currency and period, identifiable sources, and no gross-to-net assumption. Reject unsupported values. Model-generated numbers without evidence are never activated.

Store a new immutable reference version after validation. Publishing it schedules new estimates only for affected users with eligible addresses. Keep prior versions for reproducibility. Source lookup/model failure retains the last applicable reference and marks its freshness; no successful estimate is fabricated.

Real city search remains an extension because the chosen demo uses fictional districts. Before implementing that extension, select actual source providers and document their coverage, units, freshness rules, and access method.

## 7. Synthetic location-history demonstration

- Define a `LocationHistoryProvider` and implement `SyntheticLocationHistoryProvider`; there is no Google login or live tracking.
- Supply reproducible scenarios containing coarse demo coordinates/districts, arrival/departure times, and generic place labels.
- Let users load a scenario and run the analysis after enabling the location permission toggle.
- Compute observed date range, number of visits, distinct districts, and most frequently visited district using deterministic code.
- Show a visit timeline and a small schematic map; external map tiles and map-provider keys are unnecessary for the first demo.
- Compare only explicit visit coverage with the declared district, labeled “Observed visits,” without asserting that travel patterns prove residence, employment, or reliability.
- Include sparse and contradictory scenarios. Sparse data produces “Insufficient history,” not a negative finding.
- Location results never verify an address or enter affordability/risk calculations.

## 8. Social activity analysis demonstration

### 8.1 Inputs and permission

Provide synthetic accounts for Instagram/Facebook-style scenarios. Each contains profile metadata, timestamped posts, explicit location tags, text, comments, and reaction counts. No real login credentials, scraping, or account identifiers are needed.

Use a simple per-user permission toggle before running analysis. Disabling it prevents new work and stops pending work from publishing new results. This is the only consent UI in scope; a full consent, retention, or deletion-management system is deferred as requested.

### 8.2 Working processing stages

1. Load and schema-validate the selected scenario.
2. Normalize timestamps and group posts/comments by their synthetic IDs.
3. Compute counts, repeated text, posting intervals, and comment/reaction distributions deterministically.
4. Run an AI provider to summarize post subjects and extract explicitly mentioned locations with evidence IDs.
5. Ask for cautious observations about repetition, inconsistency, or possible automation; allow `INCONCLUSIVE` and avoid claiming reliable human/bot authorship detection from text alone.
6. Return an account-consistency summary with supporting post/comment IDs. Account authenticity remains unverified; a model must not present “fake account” as an established fact.
7. Save the analysis result and show the pipeline stages, summary, evidence, limitations, and provider/model version in the app.

Keep findings limited to the requested demo signals. Do not infer sensitive attributes or financial capacity from subjects discussed, contacts, reaction counts, or locations.

### 8.3 `social-media-research-skills` integration

The original notes request an AI agent using `social-media-research-skills`. No implementation or package with that name was found in this repository during planning; do not assume it is an installed runtime dependency.

Create a `SocialActivityAnalyzer` interface and a structured request/result contract. Once the intended skill source is identified, review its instructions and translate the relevant workflow into this adapter. A development-agent skill is not automatically an application service. The provider interface and synthetic scenarios allow work to proceed without coupling the application to that unresolved package.

The complete feature includes a real model-backed adapter selectable by configuration, plus explicit fixture playback for offline use. No provider configured means **AI unavailable** or **Fixture playback**, never a fabricated claim that live AI ran.

### 8.4 Demonstration scenarios

- Ordinary varied posts with explicit locations and plausible engagement.
- Repetitive, frequent posts and duplicated comments, producing automation observations with uncertainty.
- Conflicting dates/location tags, producing evidence-linked inconsistency observations.
- Very few posts, producing an inconclusive result.
- Instructions embedded in post text, which must be treated as content rather than instructions to the model.

## 9. Storage model and migration

Proposed new tables; exact migration numbers should use the next available versions when implementation starts:

| Table | Essential fields and constraints |
| --- | --- |
| `financial_input_revisions` | User, revision, input mode, amounts, housing situation, source, created time; unique `(user_id, revision)` |
| `user_addresses` | User, revision, structured address, verification state, accepted verification ID; immutable revision rows |
| `address_verifications` | User/address revision, job ID, document ID, extracted evidence, checks, outcome, provider/model/prompt version |
| `local_cost_references` | Dataset version, geography, monetary values and units, evidence, synthetic flag, publication time |
| `user_affordability_state` | User PK, input generation, current financial/address/reference revisions, latest snapshot ID; lock/version field |
| `affordability_snapshots` | User, generation, input revisions, calculated time, formula/policy versions, component breakdown, base amount, reasons, status, synthetic flag |
| `analysis_jobs` | User, kind, source revision, permission generation, state, attempt count, deduplication key, lease/deadline, failure code, timestamps |
| `demo_signal_settings` | User PK, location/social enabled flags, permission generation |
| `demo_signal_reports` | User, job, kind, scenario ID, aggregates, findings/evidence, data source, analysis mode, model/prompt version |

Store snapshot financial values needed for reproduction, not only foreign keys. Include the partner cap map applicable at calculation time so a partner's historical graph is not rewritten when its current cap changes. A cap change schedules affected estimates just like a reference change. Use `NUMERIC` columns for amounts and JSONB for bounded breakdown/evidence objects. Add user/time indexes and foreign keys that prevent cross-user linkage of address evidence and jobs.

Documents live in a private backend volume outside static web assets, using generated names. Use authenticated, ownership-checked access when necessary. Bound image size and count; fixture files can remain in repository resources. Raw images are temporary processing inputs; a terminal job removes its temporary copy. A production retention feature is out of scope.

Backfill one aggregate financial revision per existing profile, preserving the old total exactly. Keep `demo_financial_profiles` for legacy behavior and existing profile fields. Seeders must not overwrite later user edits. New registrations create both the existing starter profile and a matching initial revision.

Do not invent address verification or past monthly snapshots during migration. Historical applications retain their original snapshots and `rules-v1` decisions.

## 10. API contracts

All public APIs below require the existing authenticated session and derive user ID from it. State-changing calls retain CSRF protection. Foreign and missing resource IDs return the same `404` response.

| Method and route | Purpose/result |
| --- | --- |
| `GET /api/me/financial-inputs` | Current inputs, mode, provenance, and revision |
| `PUT /api/me/financial-inputs` | Validate/save `{expectedRevision, ...inputs}`; return saved revision and recalculation job ID |
| `GET /api/me/address` | Current address, revision, and verification summary |
| `PUT /api/me/address` | Save a new address revision; invalidate old verification; schedule recalculation |
| `POST /api/me/address/verifications` | Multipart image or separate JSON fixture request, including address revision; `202` with job ID |
| `GET /api/me/affordability` | Current base estimate, partner caps/amounts, status, reasons, freshness, and input revisions |
| `GET /api/me/affordability/history?months=12` | Monthly points with snapshot time, amount, provenance, and policy version; allow 1–24 months |
| `POST /api/me/affordability/recalculate` | Idempotent recalculation for current generation; `202` with job ID |
| `GET /api/me/demo-signals/settings` | Current location/social demo toggles |
| `PUT /api/me/demo-signals/settings` | Update toggles and permission generation |
| `POST /api/me/demo-signals/location-runs` | Validate scenario ID and permission; `202` with job ID |
| `POST /api/me/demo-signals/social-runs` | Validate scenario ID and permission; `202` with job ID |
| `GET /api/me/demo-signals/reports?kind=SOCIAL` | Latest completed results for the requested kind |
| `GET /api/me/analysis-jobs/{id}` | Stage/state, failure code, and result reference for polling |

Use server-owned fixture IDs; never accept arbitrary file paths or external URLs from users. Reference publication is a backend CLI/demo operation, not an unprotected administrative route.

Example estimate shape (amounts remain JSON numbers, matching existing money contracts):

```json
{
  "status": "READY",
  "calculatedAt": "2026-09-22T12:00:00Z",
  "generation": 7,
  "financialRevision": 3,
  "addressRevision": 2,
  "referenceVersion": "demo-city-v1",
  "formulaVersion": "affordability-v2",
  "currency": "USD",
  "termMonths": 6,
  "baseAmount": 2250.00,
  "monthlyPaymentCapacity": 375.00,
  "breakdown": { "income": 3000.00, "effectiveExpenses": 1750.00, "obligations": 200.00, "reserve": 300.00 },
  "partners": [{ "slug": "markethub", "cap": 1500.00, "possibleAmount": 1500.00 }],
  "reasons": ["DISTRICT_RENT_FLOOR_APPLIED", "DISTRICT_GROCERY_FLOOR_APPLIED"],
  "dataSource": "SYNTHETIC"
}
```

Use `READY`, `UPDATING`, `UNAVAILABLE`, and `ERROR` as response freshness/availability states. When showing an old amount during updating/error, include its original generation and timestamp and explicitly set `stale: true`. A missing-data result uses `baseAmount: null`; never substitute zero.

Extend the internal Python API with authenticated `POST /affordability` for pure calculations and distinct `POST /demo-analysis/address`, `/demo-analysis/local-costs`, and `/demo-analysis/social` endpoints for provider operations. Keep `/score` as the application-decision endpoint. Java persists all jobs and results; Python does not acquire a database connection.

`/affordability` and `/score` accept the same versioned affordability input structure and invoke the same calculation function. Return formula/version metadata and a typed breakdown. Coordinate Java/Python deployment because Python currently rejects unknown fields (`extra="forbid"`).

## 11. Recalculation, concurrency, and failures

Recalculate after financial edits, address changes, accepted verification, publication of applicable cost references, or formula-policy activation. Location/social changes do not schedule affordability work.

1. Save an input revision, increment the user's generation, and insert a job in one database transaction.
2. A bounded Java worker claims queued jobs using database locking and a lease. Use a separate executor from the existing short-lived feature-preparation pool.
3. Resolve immutable input/reference revisions at that generation, then call Python outside the database transaction.
4. Save the immutable result and promote it as current only if the generation still matches. Older jobs may finish but cannot replace newer estimates.
5. Poll from React while work is pending; stop polling on unmount or terminal state. A reload resumes from the persisted job state.

Job states: `QUEUED → RUNNING → SUCCEEDED | FAILED | CANCELLED | SUPERSEDED`. Retry transient failures at most twice with bounded delays. Lease expiry allows recovery after restart. Use longer, separately configured timeouts for provider analysis than the current short scoring timeout. Cap queue depth, reject overload predictably, and deduplicate identical runs.

When saving an application, capture the financial generation and policy with its immutable input bundle. Recheck the generation before commit; if it changed, return `409 INPUTS_CHANGED` and let the user review the refreshed estimate. Preserve existing behavior that an analysis failure saves no successful application decision.

All inputs used by sequential and parallel preparation must refer to the same financial revision. Optional external/model processing never runs inside the scoring feature-preparation tasks. A provider failure must not take down rules-only affordability or the whole service health endpoint.

## 12. Monthly graph and dashboard UI

### 12.1 Graph semantics

- Default series: base affordable amount before partner caps. A partner selector can display its capped series, clearly labeled.
- Use existing `success.700` green for the main line and a light green area fill. Add point markers and readable tooltips; color is not the only cue.
- Default range: 12 calendar months including the current month; optional 6/12-month toggle. Use UTC month boundaries in both API and UI.
- Each month shows the last successful snapshot calculated in that month. The current point updates when recalculation finishes.
- A month without a successful snapshot is `null` and appears as a gap, not zero, an interpolated estimate, or a fabricated historical value.
- Show unavailable/failed events in history metadata so an earlier successful point is not mistaken for the latest attempted estimate.
- Month-over-month change compares the current and immediately previous calendar month only when both contain valid values. If previous amount is zero, show the absolute change and omit percentage.
- Tooltips include amount, snapshot date, input/reference versions, and demo provenance. Annotate policy changes so a changed formula is not mistaken for a changed customer profile.

Append a snapshot on successful recalculation; do not overwrite prior results. Repeated calculation of unchanged inputs deduplicates by generation/formula/reference identity. Monthly snapshots are observations, not forecasts. Newly registered accounts start with their real available history.

For the diploma, selected fixture personas receive 12 explicitly synthetic historical input scenarios calculated using the shared formula. Seed idempotently and label the series **Demo history**. Do not reconstruct past data from today's values.

### 12.2 Components and states

Create components under `demo-repository/src/components/affordability/`:

- `AffordabilitySummary`: base amount, monthly payment capacity, term, last updated, and change.
- `AffordabilityChart`: monthly series and partner selector; a responsive SVG is sufficient for this small fixed series.
- `AffordabilityBreakdown`: income, resolved expenses, obligations, reserve, payment constraint, and cap explanation.
- `FinancialInputsForm` and `AddressVerificationPanel`: edits, file submission, status, and retry controls.
- `LocalCostContext`: district expenses, city salary comparison, sources, dates, and reference status.
- `LocationHistoryPanel` and `SocialAnalysisPanel`: permission toggle, scenario selection, stages, and findings.

Provide keyboard-accessible point selection and an equivalent data table. Test mobile layout, long district names, zero capacity, missing months, one point, updating/error states, and large values. Reuse existing cards/forms/error components. Preserve the existing purchase-history and applications cards below the new summary.

## 13. AI provider boundaries and configuration

Define narrow, independently replaceable providers for address extraction, local-cost extraction, and social analysis. Keep these separate from `app/ai.py` and the existing application's `useAi` risk-scoring flag.

Configuration should include provider mode (`FIXTURE` or `AI`), model identifier, server-only credentials, timeout, request-size limit, and concurrency limit. Pin prompt/schema versions and store them with results. Choose the actual provider during implementation; no provider API, price, or platform permission is assumed here.

In AI mode, require schema-valid output with evidence references. Treat documents/posts as untrusted input; never execute embedded instructions or allow them to select tools, destinations, or data belonging to another user. Only prepared synthetic data is sent during the demo.

Allow missing keys and provider outages to produce explicit unavailable states while the app and deterministic estimate remain usable. The full AI demonstration acceptance requires at least one real model run; fixture-only tests do not establish that the AI integration works.

## 14. Implementation phases and deliverables

### Phase 1 — Contracts, formula, and baseline fixtures

- [ ] Add typed affordability request/result contracts in Java and Python.
- [ ] Extract the pure calculation function and implement `affordability-v2` with documented rounding.
- [ ] Add policy version dispatch; keep the existing `rules-v1` path available for compatibility.
- [ ] Create the worked-example fixture and boundary cases before UI changes.
- [ ] Document new reason codes and the unchanged risk-model feature semantics.

Exit: the worked example produces `$2,250` base / `$1,500` MarketHub consistently, and the old policy's test cases still pass.

### Phase 2 — Persistence, editable finances, and reliable recalculation

- [ ] Add Flyway migrations, repositories, revision state, and job worker.
- [ ] Backfill aggregate inputs and adapt starter data without overwriting user changes.
- [ ] Implement financial-input, estimate, history, and job-status endpoints.
- [ ] Implement generation checks, deduplication, restart recovery, and transient retries.
- [ ] Add a financial editor and basic current-estimate display.

Exit: editing expenses changes the saved estimate; refresh/restart preserves results; failed or stale work cannot publish a misleading current amount.

### Phase 3 — Dashboard graph and scoring integration

- [ ] Build green monthly graph, accessible table, breakdown, and partner selector.
- [ ] Seed labeled monthly scenarios for demo personas.
- [ ] Update the finance provider and `/score` to use the shared inputs/formula.
- [ ] Save generation/formula/reference metadata with application snapshots.
- [ ] Extend reason text and application detail screens for v2 calculations.

Exit: dashboard and a newly saved application agree for identical inputs and partner; historical v1 decisions remain unchanged. This is the first useful delivery slice.

### Phase 4 — Address evidence and district context

- [ ] Implement address revisions and private bounded image upload.
- [ ] Add fixture extraction and an AI extraction adapter.
- [ ] Add deterministic matching and address status transitions.
- [ ] Seed local-cost references and build the source/evidence panel.
- [ ] Implement the local-cost extraction/publication process and affected-user recalculation.

Exit: accepted sample evidence selects the correct references; mismatch, uncertainty, edited addresses, and unavailable providers are demonstrated correctly.

### Phase 5 — Location and social demonstrations

- [ ] Add optional-analysis toggles, fixture scenarios, and report APIs.
- [ ] Build location aggregates, schematic map, and timeline.
- [ ] Implement social normalization, deterministic metrics, and real AI adapter.
- [ ] Add evidence-linked social findings and visible pipeline progress.
- [ ] Resolve/document the intended `social-media-research-skills` source if it is to be used.

Exit: both demonstrations run from the app; actual AI and fixture modes are distinguishable; no report changes affordability or credit outcomes.

### Phase 6 — Verification, rollout, and diploma walkthrough

- [ ] Run the test matrix below and record deterministic scenario results.
- [ ] Demonstrate at least one successful real provider run on synthetic evidence.
- [ ] Update `docs/DEMO_SCENARIOS.md` with separate v1/v2 expectations and a walkthrough.
- [ ] Update deployment documentation with flags, provider setup, private volume, and fallback behavior.
- [ ] Add diploma screenshots and describe formula assumptions, AI limitations, and observed failure handling.

Exit: a fresh local stack supports the walkthrough; offline fixture mode remains reproducible; AI mode works when a provider is configured.

## 15. Verification matrix

| Layer | Required checks |
| --- | --- |
| Python formula | Worked example; zero/missing income; expenses over income; debt limit binding; partner cap binding; rounding boundaries; category/aggregate modes; rent floor exclusions |
| Formula properties | Increasing expenses or debt cannot increase capacity; raising income cannot reduce it for fixed other inputs; results are non-negative and capped correctly |
| Java/Python contract | Shared v2 input/results serialize correctly; strict schema handling; v1 compatibility; identical resolved inputs produce identical dashboard/application capacity |
| Database/backend | Migration from existing rows; ownership checks; CSRF on writes/uploads; stale revision conflict; same-user foreign keys; immutable application snapshots |
| Jobs | Duplicate clicks; worker restart; retry exhaustion; queue overload; out-of-order completion; address edited during review; permission revoked mid-run |
| Address/references | Matching/mismatching/uncertain sample documents; invalid file; unsupported district; incompatible units; missing source; invalid AI output; provider unavailable |
| Signal isolation | Changing any social/location input leaves finance bundle, credit-risk inputs, score, and amount unchanged |
| Monthly history | UTC boundaries; last valid monthly snapshot; no-data gaps; current month update; zero prior value; policy transition; separate fixture and user histories |
| Frontend/browser | Save finances → update graph → verify address → updated references → submit application; reload persistence; keyboard chart/table; mobile layout; optional analysis run/failure |
| AI demonstration | Live processing of synthetic inputs with provider/model/prompt metadata and evidence; offline playback labeled correctly; embedded instructions ignored |
| Regression | Registration/login/logout, store handoff, purchase filtering, existing application reads, sequential/parallel feature equality |

Use the repository's existing pytest, Maven/JUnit/Testcontainers, TypeScript build/type check, and Playwright tooling. Mock external AI in automated CI; run the explicit real-provider demonstration separately so tests do not depend on nondeterministic text or paid API availability.

## 16. Rollout and rollback

Introduce `AFFORDABILITY_V2_ENABLED` with a default-off rollout and a separate provider-mode configuration. Deploy additive database changes first, then compatible Python contracts, Java APIs/workers, and frontend. Activate v2 together for dashboard and application submissions to avoid mixed formulas.

Switch the dashboard and new application policy back together if rollback is needed. Preserve new input revisions, reports, and snapshots; do not drop tables or rewrite historical decisions. Workers must check the active formula/configuration before promoting results so late v2 jobs cannot become current after rollback. Show a version transition or separate series when historical graph points span different formulas.

## 17. Diploma walkthrough

1. Sign in as a seeded persona and show the green 12-month demo history.
2. Open the breakdown and explain the six-payment, zero-interest formula.
3. Increase expenses, save, and show the newly calculated amount and monthly point.
4. Submit a sample address letter; show AI extraction, matching checks, district references, and the resulting amount change.
5. Show the city salary reference and its source separately from personal income.
6. Enable location analysis and display a synthetic visit scenario.
7. Enable social analysis, run a scenario through the real configured model, and inspect findings tied to specific posts/comments.
8. Show that optional social/location analysis did not change the amount.
9. Apply at a partner and compare its saved `possibleAmount` with the matching dashboard value.
10. Reload the dashboard and decision, then demonstrate an uncertain document or provider failure with a clear recoverable state.

## 18. Remaining choices and completion criteria

Working defaults are defined above, so implementation can start with the deterministic phases. The following are needed before the corresponding AI integrations are completed:

- [ ] Select a model/provider and supply server-side credentials for actual AI runs.
- [ ] Identify the intended `social-media-research-skills` repository/package, or proceed with the documented adapter implementation.
- [ ] Finalize the prepared address documents, district references, and social/location scenarios.
- [ ] If real-city research is later required, select the city, currency/basis mapping, sources, and retrieval adapter.

The feature is complete when editable finances and verified demo address context update a persisted, explained amount; the green monthly graph reflects saved history; application capacity uses the same formula; optional location/social processing works visibly without affecting credit; and the walkthrough passes in both clearly labeled offline mode and configured AI mode.
