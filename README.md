# diploma

> Payment scope update: the application now supports simulated balances, purchases, interest-bearing credit schedules and repayments. See [Balance and repayments](docs/BALANCE_AND_REPAYMENTS.md) for the current behavior; earlier exclusions of simulated payments below are historical. Real payment providers remain outside scope.
I see the final result is the demo web app, where the user can have account with his transaction history from other demo partners - web stores(netflix, amazon, stores of clothes). So user can request the amount of money and choose one of the partners, where he wants to buy something and then our modules are analyzing him and give decision for that request. 
The main focus is to do research about the implementation of this app: how auth working, multithreading, modules of scoring, safe data keeping, AI for analyzing data. 
I need the frontend, backend and frontend of demo partners to leading to our website.
---

## Project guide

**Start:** `cd rocket-credit-deployment && docker compose -f docker-compose.diploma.yml --env-file diploma/.env up --build -d`, then once per new database `./diploma/import-prices.sh`, then open http://localhost:8080. Demo accounts: `avery|riley|drew|casey@demo.rocket.local`, password `rocket-demo-123` (one-click buttons on the sign-in page). Full guide: [DIPLOMA_RUNTIME.md](rocket-credit-deployment/DIPLOMA_RUNTIME.md).

| Read this | For |
| --- | --- |
| [docs/diploma/THESIS.md](docs/diploma/THESIS.md) | The diploma text (five research topics, results, limitations) |
| [docs/diploma/DEMO_SCRIPT.md](docs/diploma/DEMO_SCRIPT.md) | 10-minute defence walkthrough |
| [docs/DIPLOMA_COMPLETION_PLAN.md](docs/DIPLOMA_COMPLETION_PLAN.md) | What was built, step by step, and its status |
| [docs/VERIFICATION.md](docs/VERIFICATION.md) | Test results and the fresh-start check |
| [docs/DEMO_SCENARIOS.md](docs/DEMO_SCENARIOS.md) | Demo personas and expected decisions |
| [docs/DIPLOMA_ARCHITECTURE.md](docs/DIPLOMA_ARCHITECTURE.md), [docs/SAFE_DATA_KEEPING.md](docs/SAFE_DATA_KEEPING.md), [docs/SOCIAL_ACCOUNT_ANALYSIS.md](docs/SOCIAL_ACCOUNT_ANALYSIS.md) | Design, data protection, Facebook analysis |
| [research/](research/) | AI training/evaluation and the multithreading benchmark |
