# Demo Scenarios

Updated 2026-10-04. Financial profiles and visits are synthetic diploma data; researched price references are documented in `LOCATION_PRICING.md`. Older v1/v2 examples below describe historical policies. Fixture source: `rocket-credit-backend/src/main/resources/fixtures/demo-customers.json`.

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

Partner caps: StreamBox 600 USD, MarketHub 1500 USD, Threadly 800 USD.
Possible amount = min(partner cap, disposable income × 3.0) (policy `rules-v1`).

## Current automatic-budget walkthrough (`rules-v4`)

1. Open the dashboard. Financial income/debt are supplied by the demo profile and read-only; there are no duplicate expense amount fields.
2. Save the housing situation. Enter a Budapest postcode; the district resolves automatically. Save the address.
3. For the prepared fixture, sign in as Riley Review and use `1051`, `Minta utca`, building `12`. Review the prepared address card to activate cost references.
4. Edit apartment size and rent-sharing if needed. Groceries stay a fixed per-person basket; other spending is forecast automatically. Until the address is verified, credit calculations retain the supplied demo expense baseline.
5. Open **Optional synthetic activity analysis** and run `budapest-priced-month`. Location and social demos are always enabled. Sufficient history blends the benchmark and 30-day projected spending; sparse scenarios fall back to benchmarks. Open **How activity spending is forecast** to compare both figures.
6. Submit an application. Dashboard and application preparation use the same captured budget context; prices, forecast source and revisions are retained with results. Social activity does not contribute to credit calculations.

See [automatic methodology](LOCATION_PRICING.md#automatic-budgets-and-activity-forecasts--4-october-2026). Income and debt are demo data, not independently verified finances. Older expected outcomes below describe prior policies.

## Expected outcomes (policy `rules-v1`, review band 0.40–0.65; verified live 2026-09-19)

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
