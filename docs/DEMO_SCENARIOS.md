# Demo Scenarios
<!-- 2026-10-28: seller and administrator walkthroughs added, AI-assisted (Claude), reviewed by the author. -->

Updated 2026-10-22 (marketplace, district map and `rules-v4` outcomes). Earlier update 2026-10-04. Financial profiles and visits are synthetic diploma data; researched price references are documented in `LOCATION_PRICING.md`. Older v1/v2 examples below describe historical policies. Fixture source: `rocket-credit-backend/src/main/resources/fixtures/demo-customers.json`.

## Demo accounts

All demo accounts use the password `rocket-demo-123`. Registering a new account works too; it receives a
clearly labelled synthetic starter dataset (`syntheticSource = STARTER`, three purchases described as
"[Synthetic starter data]"), never another account's history.

| Customer | Email | Account age | Profile | History (StreamBox / MarketHub / Threadly) | Income − expenses − obligations |
| --- | --- | ---: | --- | --- | ---: |
| Avery Approved | `avery@demo.rocket.local` | 30 months | complete, verified | 12 / 8 (1 refund) / 5, all on time | 2000 − 1000 − 50 = **950** |
| Riley Review | `riley@demo.rocket.local` | 10 months | incomplete, verified | 0 / 6 (1 late, 1 refund) / 1 (refunded) | 2500 − 1900 − 300 = **300** |
| Drew Denied | `drew@demo.rocket.local` | 1 month | incomplete, unverified | 0 / 0 / 1 (late) | 1800 − 1700 − 150 = **−50 → 0** |
| Casey Limited | `casey@demo.rocket.local` | 18 months | complete, verified | 10 / 4 / 3, all on time | 2200 − 1900 − 100 = **200** |

Seller and administrator accounts (same password; see [ROLES.md](ROLES.md)): `admin@demo.rocket.local`,
`netflix@demo.rocket.local` (Netflix / `streambox`), `zara@demo.rocket.local` (Zara / `threadly`),
`amazon@demo.rocket.local` (Amazon / `markethub`). A separate synthetic customer, `orders@demo.rocket.local`,
holds 12 financed orders so the seller screens have data; the four personas above are not changed.

Partner caps: StreamBox 600 USD, MarketHub 1500 USD, Threadly 800 USD.

## Seller and administrator walkthroughs

Added 2026-10-28 (roles). Screenshots `docs/screenshots/11-…` to `18-…`.

**Seller (Zara)**
1. Sign in, click **Zara seller** under "Demo seller and administrator accounts", then **Sign in**. Expected: `/seller`, heading "Zara overview", tiles for financed orders, amount paid by Rocket Credit, active and closed credits, and a 30-day chart (`11-seller-overview.png`).
2. **Orders**. Expected: a table of financed orders; the customer column shows only `C-XXXXXX` references, no names or e-mails (`12-seller-orders.png`).
3. **Catalogue → Add product**. Enter a name, a price in USD, a description, a category and an image. A price above 800 USD is refused before sending (`13-seller-catalog-form.png`). **Add product**: the product appears in the table and in the marketplace.
4. **Deactivate** the product: it disappears from the marketplace; a quote for it answers `PRODUCT_UNAVAILABLE`.
5. Open `/admin` or `/balance`: you are sent back to `/seller` with a notice.

**Administrator: human review of Riley's request**
1. Sign in as Riley and request 18,000 HUF at Amazon over 6 months (as in the smoke test). Expected: **Needs review**, and "An administrator will look at this request".
2. Sign out, sign in with **Administrator**. Expected: `/admin`, "Platform overview" with accounts, decisions, open reviews and service health (`14-admin-overview.png`).
3. **Reviews** → **Review** on Riley's request. Expected: the automatic result, the reasons Riley was shown, factor scores and the chosen plan; no income, costs, address or footprint (`16-admin-review-detail.png`).
4. Choose **Approve** (amount at most min(possible, requested)) or **Reject**, write a note of at least 10 characters, **Review decision**, then **Confirm decision**. Expected: the decision card; a second decision is refused (`ALREADY_REVIEWED`).
5. **Audit log**. Expected: an `ADMIN_REVIEW_DECIDED` entry with outcome and amount, newest first (`17-admin-audit-log.png`).
6. Sign in as Riley again and open the request. Expected after approval: "Human review: approved", the note, the kept automatic result, and **Accept and pay seller** (`18-customer-decision-after-review.png`). Accepting creates a credit for the approved amount. After rejection: "Human review: not approved" with the note and no offer.
7. **Users** (`15-admin-users.png`): create a seller for a partner, disable or enable an account (a disabled seller's next request gets `401`), set a temporary password. **Partners**: change a financing limit or switch a partner off.
Possible amount = min(partner cap, disposable income × 3.0) (policy `rules-v1`).

## Current automatic-budget walkthrough (`rules-v4`)

1. Open the dashboard. Financial income/debt are supplied by the demo profile and read-only; there are no duplicate expense amount fields.
2. Save the housing situation. Enter a Budapest postcode; the district resolves automatically. Save the address.
3. For the prepared fixture, sign in as Riley Review and use `1051`, `Minta utca`, building `12`. Review the prepared address card to activate cost references.
4. Edit apartment size and rent-sharing if needed. Groceries stay a fixed per-person basket; other spending is forecast automatically. Until the address is verified, credit calculations retain the supplied demo expense baseline.
5. Open **Optional synthetic activity analysis** and run `budapest-priced-month`. Location and social demos are always enabled. Sufficient history blends the benchmark and 30-day projected spending; sparse scenarios fall back to benchmarks. Open **How activity spending is forecast** to compare both figures.
6. Submit an application. Dashboard and application preparation use the same captured budget context; prices, forecast source and revisions are retained with results. Social activity does not contribute to credit calculations.

See [automatic methodology](LOCATION_PRICING.md#automatic-budgets-and-activity-forecasts--4-october-2026). Income and debt are demo data, not independently verified finances. Older expected outcomes below describe prior policies.

## Expected outcomes today (policy `rules-v4`; verified 2026-10-22 on a fresh stack)

Six monthly payments, no verified address (so the supplied demo expense baseline applies). Reproduce with
`python3 research/demo/persona_matrix.py http://localhost:8080`; raw output in [`research/demo/persona-matrix.md`](../research/demo/persona-matrix.md).

| Scenario | Request | Expected | Why (shown to the user) |
| --- | --- | --- | --- |
| Avery at Amazon | 108,000 HUF | **APPROVED** (score 0.77), possible 540,000 HUF | long, clean history; amount within budget and the seller limit |
| Avery at Amazon, AI on | 108,000 HUF | **APPROVED** (0.81), `aiStatus = APPLIED`, model `logreg-v1` | the model's low risk lifts the score |
| Avery at Amazon | 576,000 HUF | **REJECTED** (`AMOUNT_ABOVE_POSSIBLE`), possible 540,000 HUF | above the Amazon limit; the page offers “Try 540 000 Ft instead” |
| Riley at Amazon | 18,000 HUF | **REVIEW** (0.47) | incomplete profile and refunds put the score in the review band |
| Riley at Amazon, AI on | 18,000 HUF | **REVIEW** (0.56) | AI lifts the score, but not over the approval line |
| Drew at Zara | 54,000 HUF | **REJECTED** (`ZERO_CAPACITY`, 0.13) | new, unverified account with no money left after costs |
| Drew at Zara, AI on | 54,000 HUF | **REJECTED** (0.11, `AI_RISK_ELEVATED`) | zero capacity still decides; the model agrees |
| Casey at Netflix / Amazon | 36,000 / 252,000 HUF | **REJECTED** (`ZERO_CAPACITY`) | under `rules-v4` the 10 % reserve and debt limit leave Casey no monthly capacity |

`REVIEW` means the automatic result is inconclusive; there is no operator queue. Only an accepted approval moves (simulated) money.

## Historical expected outcomes (policy `rules-v1`, review band 0.40–0.65; verified live 2026-09-19)

| Scenario | Request | Expected | Why |
| --- | --- | --- | --- |
| Avery at MarketHub | 300 USD | **APPROVED** (score 0.76), possible 1500 | long, clean history; capacity 5100 capped to 1500 |
| Avery at MarketHub | 1600 USD | **REJECTED**, possible 1500 | above the partner cap; lower amount suggested |
| Riley at MarketHub | 250 USD | **REVIEW** (score 0.45), possible 900 | incomplete profile, one refund and one late payment → score in the review band |
| Drew at Threadly | 150 USD | **REJECTED** (`ZERO_CAPACITY`) | no disposable income; new, unverified account |
| Casey at StreamBox | 300 USD | **APPROVED** (score 0.74), possible 600 | capacity 600 (= 200 × 3), within the StreamBox cap |
| Casey at MarketHub | 700 USD | **REJECTED**, possible 600 | above capacity; 600 shown as a suggestion for a new request |
| Avery at MarketHub, `useAi` on | 300 USD | **APPROVED** (score 0.81), `aiStatus = APPLIED`, model `logreg-v1`, risk ≈ 0.01 | hybrid = 0.8 × rules + 0.2 × (1 − risk) |
| Riley at MarketHub, `useAi` on | 250 USD | **REVIEW** (score 0.55) | AI lifts the score but it stays under the 0.65 approval line |
| Drew at Threadly, `useAi` on | 150 USD | **REJECTED**, `AI_RISK_ELEVATED` (risk ≈ 0.97) | zero capacity still decides; the AI agrees |
| Any customer, `useAi` on, no model file | any | same decision as rules, `aiStatus = UNAVAILABLE` | fallback recorded explicitly |

`REVIEW` means the automatic result is inconclusive; there is no operator queue. No money moves in any case.

## Affordability dashboard (policy `rules-v2`)

The historical v2 Compose runtime selected `rules-v2`; use `ANALYSIS_POLICY_PATH=app/policy.json` only for a deliberate
legacy rollback. New application decisions use the same saved financial revision and affordability formula as
the dashboard. Existing `rules-v1` rows are not rewritten.

For the seeded aggregate inputs with no verified address references:

| Persona | Inputs (income / expenses / obligations) | Monthly payment capacity | Base amount | MarketHub amount |
| --- | --- | ---: | ---: | ---: |
| Avery | 4,500 / 2,500 / 300 | 625 | 3,750 | 1,500 |
| Riley | 2,500 / 1,900 / 300 | 25 | 150 | 150 |
| Drew | 1,800 / 1,700 / 150 | 0 | 0 | 0 |
| Casey | 2,200 / 1,900 / 100 | 0 | 0 | 0 |

Capacity is `min(50% × max(0, income − expenses − obligations − 10% reserve), 30% × income − obligations)`,
rounded down to cents monthly, then multiplied by six. A score can still return REVIEW or REJECTED when a request
is within the amount. The existing risk model retains its separate `disposable income × 3` utilization feature.

### Walkthrough

1. Sign in as `riley@demo.rocket.local` with `rocket-demo-123`; the dashboard loads the current estimate and its snapshot history.
2. Edit monthly finances in Aggregate expenses mode, save, and wait for the generation-backed recalculation to finish.
3. Switch to Itemized mode to enter all five categories. Saving creates a new immutable revision; an older browser revision receives `409 REVISION_CONFLICT`.
4. For the prepared address-card match, use the synthetic Riley Review sample address: Budapest District V, postal `1051`, `Minta utca`, building `12`. Upload the prepared card shown in the dashboard. The result is `VERIFIED_DEMO` for the fixture only.
5. Change the address and observe verification reset. A different image becomes NEEDS_REVIEW; a mismatching or uncertain prepared result is not accepted.
6. Run local-cost extraction to see the synthetic source passage and provider mode. The preview does not publish a dataset. Only a current `VERIFIED_DEMO` address activates the seeded rent/grocery references.
7. Run a location or social scenario; both demos are always enabled. Reports show synthetic provenance and evidence IDs.
8. Submit an application and compare its `possibleAmount` with the selected partner estimate for the same revision. A concurrent financial edit returns `409 INPUTS_CHANGED`.

The generated address card and all Budapest reference values are invented fixtures. They are not real residence evidence or Hungarian market statistics. Fixture mode works offline. Set `ANALYSIS_DEMO_PROVIDER_MODE=AI` and a server-side `GEMINI_API_KEY` to enable Gemini on the prepared synthetic card, local-cost text, and synthetic social scenarios. Without credentials, the report states `AI_UNAVAILABLE`; a successful live-provider walkthrough has not been recorded yet. Twelve historical demo snapshots are also not seeded yet, so months without saved snapshots appear as gaps.

## Legacy (superseded)

The earlier five-service checkout used four personas keyed by partner identifiers (`streambox-approved`, …)
and a KYC gate. Those fixtures lived in `rocket-credit-user-data` migrations `V7`/`V8`; the service was removed from the
repository after Step 7 (git history up to `1b886c0`).
