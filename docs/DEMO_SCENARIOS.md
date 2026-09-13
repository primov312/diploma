# Demo Scenarios

The local database seeds four synthetic customer profiles. They exist only for the diploma demonstration and contain derived scoring features rather than real financial data.

| Customer | Partner identifier | Intended result | Suggested checkout amount |
| --- | --- | --- | ---: |
| Avery Approved | `streambox-approved` | Approved | 300 USD |
| Riley Review | `markethub-review` | Manual review | 250 USD |
| Drew Denied | `threadly-denied` | Rejected because KYC is incomplete | 150 USD |
| Casey Limited | `streambox-limited` | Credit-cap scenario for the later lower-offer flow | 700 USD |

The first three cases are supported by the current scoring API. The fourth profile is intentionally seeded now; Phase 1 will next extend the checkout contract so a request above Casey's 500 USD capacity receives a lower offer instead of a generic rejection.
