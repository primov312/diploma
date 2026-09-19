# Demo Scenarios

Updated 2026-09-19 for the simplified diploma runtime. Everything below is synthetic and exists only for the
demonstration. Fixture source: `rocket-credit-backend/src/main/resources/fixtures/demo-customers.json`.

## Demo accounts

All demo accounts use the password `rocket-demo-123`. Registering a new account works too; it receives a
clearly labelled synthetic starter dataset (`syntheticSource = STARTER`, three purchases described as
"[Synthetic starter data]"), never another account's history.

| Customer | Email | Account age | Profile | History (StreamBox / MarketHub / Threadly) | Income − expenses − obligations |
| --- | --- | ---: | --- | --- | ---: |
| Avery Approved | `avery@demo.rocket.local` | 30 months | complete, verified | 12 / 8 (1 refund) / 5, all on time | 4500 − 2500 − 300 = **1700** |
| Riley Review | `riley@demo.rocket.local` | 4 months | incomplete, verified | 0 / 2 (1 late) / 1 (refunded) | 2500 − 1900 − 300 = **300** |
| Drew Denied | `drew@demo.rocket.local` | 1 month | incomplete, unverified | 0 / 0 / 1 (late) | 1800 − 1700 − 150 = **−50 → 0** |
| Casey Limited | `casey@demo.rocket.local` | 18 months | complete, verified | 10 / 4 / 3, all on time | 2200 − 1900 − 100 = **200** |

Partner caps: StreamBox 600 USD, MarketHub 1500 USD, Threadly 800 USD.
Possible amount = min(partner cap, disposable income × 3.0) (policy `rules-v1`).

## Expected outcomes (policy `rules-v1`; verified in Step 4)

| Scenario | Request | Expected | Why |
| --- | --- | --- | --- |
| Avery at MarketHub | 300 USD | **APPROVED**, possible 1500 | long, clean history; capacity 5100 capped to 1500 |
| Avery at MarketHub | 1600 USD | **REJECTED**, possible 1500 | above the partner cap; lower amount suggested |
| Riley at MarketHub | 250 USD | **REVIEW** | thin history, incomplete profile → score in the review band |
| Drew at Threadly | 150 USD | **REJECTED** (`ZERO_CAPACITY`) | no disposable income; new, unverified account |
| Casey at StreamBox | 300 USD | **APPROVED**, possible 600 | capacity 600 (= 200 × 3), within the StreamBox cap |
| Casey at MarketHub | 700 USD | **REJECTED**, possible 600 | above capacity; 600 shown as a suggestion for a new request |
| Any customer, `useAi` on | any | same decision, `aiStatus = UNAVAILABLE` | no model is loaded until Step 6 |

`REVIEW` means the automatic result is inconclusive; there is no operator queue. No money moves in any case.

## Legacy (superseded)

The earlier five-service checkout used four personas keyed by partner identifiers (`streambox-approved`, …)
and a KYC gate. Those fixtures live in `rocket-credit-user-data` migrations `V7`/`V8` and are not loaded
by the diploma runtime.
