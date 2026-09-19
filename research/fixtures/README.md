# Fixtures

The demo customers and their partner histories live with the code that loads them:
[`rocket-credit-backend/src/main/resources/fixtures/demo-customers.json`](../../rocket-credit-backend/src/main/resources/fixtures/demo-customers.json),
loaded by `DemoDataLoader` at every backend start (idempotent through stable fixture IDs).
The partner catalog is Flyway migration `V2__seed_partners_and_products.sql`.

Expected behaviour per persona: [`docs/DEMO_SCENARIOS.md`](../../docs/DEMO_SCENARIOS.md).

Synthetic *training* data for the AI experiment (Step 6) is generated separately under
`research/training/` with a fixed seed; it is not derived from these four personas.
