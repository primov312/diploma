# Supervisor Meeting Scenario — Rocket Credit Diploma

Updated: 2026-09-16. Related documents: [architecture](DIPLOMA_ARCHITECTURE.md), [completion plan](DIPLOMA_COMPLETION_PLAN.md), [project topic](DIPLOMA_PROJECT_TOPIC.md).

## 1. Purpose of the meeting

The purpose is to show the current prototype honestly, agree on the final diploma scope, and confirm that the research will focus on authentication, multithreading, modular scoring, safe data keeping and AI analysis.

The desired outcome is approval of this statement:

> I will build a small credit-decision demonstration, not a real payment platform. A customer will have an account and synthetic transaction history from three demo stores. They will request an amount directly or from a demo-store page. The application will return and save an explained decision. The diploma will research how the five selected technical mechanisms are implemented and measured in this application.

## 2. What exists today

Be precise about the status. The repository contains two different things:

| Area | Current state | What to say in the meeting |
| --- | --- | --- |
| Legacy backend prototype | Runnable Docker Compose stack: Gateway, User Data, Credit Analysis, Payment, Repayment, PostgreSQL, Redis and MinIO | “This proves that I can run a Java/Python service integration and evaluate seeded synthetic profiles. It is reference code, not the final diploma architecture.” |
| Current scoring | Python rules evaluate synthetic profiles; four repeatable demonstration personas are seeded | “The rules are useful starting material. I will simplify them and make the final explanation and persistence clearer.” |
| Current frontend | React/Vite screens and a static account-dashboard design | “The frontend is a visual foundation. It is not yet connected to authentication or the final API.” |
| Final diploma system | Designed and planned, but not implemented yet | “I deliberately reduced the scope before building further, so the finished result demonstrates the research topics instead of an unfinished payment workflow.” |

Do not present the current implementation as having login, per-user authorization, three working partner storefronts, a trained AI model or the thread benchmark. Those are planned milestones, not completed features.

## 3. Suggested 15-minute agenda and speaking script

### 0:00–1:30 — Open with the problem

Say:

> The project is Rocket Credit, a demonstration application for a credit decision. A customer has an account with synthetic purchase history from demo stores. They request financing, and the system analyses the available synthetic information and returns an explained decision. I am not building a real bank or payment product; the application is a controlled setting for my diploma research.

### 1:30–3:30 — Show the current status

Show the terminal with the legacy containers running, then show the Python health response and the existing React interface. Explain:

> The original prototype used several services, including payment and repayment. I tested that stack to understand the service integration. But it is too large for the five research topics, and most of that infrastructure would not help answer my research questions. I will retain the useful code as reference, but the final diploma runtime will be smaller.

### 3:30–6:00 — Present the final project

Open [DIPLOMA_ARCHITECTURE.md](DIPLOMA_ARCHITECTURE.md) and show the diagram. Say:

> The final system has four parts: one React application, one Java backend, one Python analysis service and one PostgreSQL database. The React application contains both Rocket Credit customer pages and three demo-store pages: StreamBox, MarketHub and Threadly. There is no separate partner backend and no payment processing.

Then explain the user flow:

1. A customer registers or logs in.
2. They see only their own seeded transactions from the three stores.
3. They select a partner and amount, or press “Apply with Rocket Credit” on a product page.
4. The Java backend derives the user from the session and prepares profile, history and synthetic finance features.
5. The Python service applies rules and, if selected, the trained AI model.
6. The backend saves the request and explanation, then returns APPROVED, REJECTED or REVIEW.
7. The customer can revisit their application result.

Clarify:

> A result does not move money, create a purchase, or start repayment. Store purchases are only seeded historical data. This boundary makes the research manageable and avoids claiming real financial capability.

### 6:00–11:30 — Explain the diploma research topics

Use this as the detailed explanation. Each topic has a concrete implementation and evidence, not only theory.

| Topic | What will be built | What will be researched / measured |
| --- | --- | --- |
| Authentication | Spring Security registration/login/logout, BCrypt password hashes, server-side sessions, CSRF protection and ownership checks | Follow registration → login → protected request → logout. Test incorrect password, missing session, missing CSRF token and one user attempting to read another user's record. |
| Multithreading | Three independent Java feature-preparation tasks: profile, transaction history and synthetic finances; one bounded shared executor | Compare sequential execution with concurrent execution using identical inputs. Verify equal feature bundles and decisions. Record repeated-run median and p95 latency, pool size, dataset size and errors; separately test controlled simulated I/O delay. |
| Modular scoring | Python modules for profile rules, partner-history rules, affordability, optional AI and a final decision combiner | Show each factor score and reason code. Test known inputs at approval, review, rejection, zero-capacity and over-limit boundaries. Explain versioned thresholds and weights. |
| Safe data keeping | One PostgreSQL database, password hashes, foreign keys, decimal money, validation, ownership checks, restricted credentials, redacted logs and atomic decision storage | Demonstrate that hashes never appear in API responses, private data cannot be read by another account, invalid data is rejected, and a local backup can be restored. Explain that password hashing, database access control and encryption at rest are different controls. |
| AI analysis | A logistic-regression model trained offline on reproducible synthetic data and loaded by the Python service | Compare rules-only and rules-plus-AI results on the same held-out synthetic customers. Record a confusion matrix, precision, recall and ROC-AUC where meaningful. Save model/policy versions with each decision and state synthetic-data limitations. |

For scoring, give one short technical example:

~~~text
rulesScore = 0.60 × historyScore + 0.40 × profileScore
finalScore = 0.80 × rulesScore + 0.20 × AI score   when AI is enabled

possibleAmount = min(partnerCap, disposableIncome × demoMultiplier)
~~~

Say:

> These weights and amount limits are illustrative research settings, not real lending policy. The important result is that the decision can be explained, tested and reproduced.

For AI, say:

> The model predicts a synthetic risk label. It will not be described as a real probability of default, because the data is synthetic. The comparison with rules is an experiment: improvement is not assumed.

### 11:30–13:00 — Explain what is intentionally excluded

Say:

> I am excluding real banks, payments, repayments, external identity providers, separate partner backends, queues, object storage, scraping and production compliance. They increase engineering complexity but do not strengthen the five research experiments. The result will be a complete small system instead of a partial large one.

### 13:00–15:00 — Ask for a decision and invite feedback

Ask:

1. “Do you approve this simplified scope and the five research questions as the basis for the diploma?”
2. “Is there one topic you would like me to emphasize more strongly in the written work: authentication, concurrent processing, explainability, storage controls or AI evaluation?”
3. “Is the planned evidence—tests, a benchmark table, model metrics and a live browser demonstration—sufficient for the expected diploma evaluation?”

End with:

> After your feedback, I will implement the thin complete path first: session login, own transaction history, rules-only credit request and saved decision. Then I will add the AI comparison and multithreading experiment to that working path.

## 4. Commands for the live meeting

Run the preparation commands 15–20 minutes before the meeting, not for the first time while presenting. The legacy stack is used only to show current technical progress. It is not the future final architecture.

### A. Check prerequisites and the current state

From the repository root:

~~~bash
docker version
docker compose version
docker ps
~~~

Expected today: no Rocket Credit containers are running until you start them.

### B. Start the existing legacy prototype

~~~bash
cd rocket-credit-deployment
docker compose up --build -d
docker compose ps
~~~

Wait until the user-data and credit-analysis services report healthy. If a service fails, inspect only the relevant logs:

~~~bash
docker compose logs --tail=120 user-data credit-analysis gateway
~~~

The existing Compose file includes legacy Payment, Repayment, Redis and MinIO services. Do not describe these as requirements of the final diploma design.

### C. Show that the current analysis service is live

~~~bash
curl --fail --silent http://localhost:8082/_health
curl --fail --silent http://localhost:8082/_policy
~~~

Expected result: JSON showing that the analysis service responds and that its current policy can be read.

### D. Optional: run one legacy end-to-end checkout request

This sends only synthetic test data to local containers. It uses Avery Approved, one of the seeded profiles in [DEMO_SCENARIOS.md](DEMO_SCENARIOS.md). No real card, bank or payment provider is used.

~~~bash
curl --silent --show-error --fail-with-body \
  -X POST http://localhost:8080/checkout \
  -H 'Content-Type: application/json' \
  -d '{
    "partnerId": "streambox",
    "partnerPaymentId": "meeting-avery-001",
    "amount": 300.00,
    "currency": "USD",
    "installmentDurationMonths": 3,
    "buyer": {
      "partnerUserId": "streambox-approved",
      "name": "Avery Approved",
      "email": "demo-approved@rocket.local",
      "cardToken": "tok_demo_avery",
      "transactions": [],
      "items": [
        {
          "sku": "streambox-demo",
          "name": "Demo subscription",
          "price": 300.00,
          "quantity": 1
        }
      ]
    }
  }'
~~~

Use a new partnerPaymentId such as meeting-avery-002 if repeating this command; the Gateway applies idempotency to an existing ID. If this request is not healthy before the meeting, skip it and show the service health endpoints plus the existing frontend. Do not troubleshoot live in front of the supervisor.

### E. Show the existing React interface

Open a second terminal from the repository root:

~~~bash
cd demo-repository
npm run dev -- --host 127.0.0.1
~~~

Open the URL printed by Vite, normally [http://localhost:5173](http://localhost:5173). Navigate to:

- Home page: existing visual prototype.
- /account-dashboard: existing static dashboard design.

Say clearly that this is the frontend foundation. It currently contains static example data and is not connected to the legacy API. The final frontend will replace it with session-authenticated data, credit applications and the three demo store routes.

### F. Stop services after the meeting

From rocket-credit-deployment:

~~~bash
docker compose down
~~~

This stops and removes containers and the network but keeps database volumes. Do not add --volumes or -v; that would remove local demonstration data.

## 5. Short contingency plan

| Situation | What to do |
| --- | --- |
| Docker does not start in time | Show the React interface and the architecture/plan documents. Say that Docker was validated separately and do not turn the meeting into debugging. |
| One container is unhealthy | Show docker compose ps and the relevant last 120 log lines. Explain the final design removes most legacy dependencies, then continue with the scope discussion. |
| Vite port 5173 is occupied | Use the URL Vite prints; it automatically selects another port. |
| The optional checkout request fails | Do not rerun it repeatedly. Show the analysis health endpoint and explain that the checkout stack is legacy reference code rather than the final target. |
| Asked why AI is not live yet | Explain that AI integration follows the rules-only vertical slice so its evaluation is meaningful and not mixed with unfinished authentication/persistence work. |

## 6. Materials to have open

1. [This meeting guide](SUPERVISOR_MEETING_SCENARIO.md).
2. [Simple architecture](DIPLOMA_ARCHITECTURE.md).
3. [Diploma project topic](DIPLOMA_PROJECT_TOPIC.md).
4. [Completion plan](DIPLOMA_COMPLETION_PLAN.md).
5. A terminal in rocket-credit-deployment.
6. A browser with the existing React app, if it was started successfully.

## 7. Next concrete work after approval

1. Create the consolidated Java backend and stateless Python analysis service with one new database.
2. Implement session-based authentication and private per-user history.
3. Implement a rules-only request that saves and displays an explanation.
4. Connect the React account and three demo-store routes.
5. Add, evaluate and document the AI model.
6. Add and benchmark the bounded multithreading experiment.

The detailed 41-task breakdown and completion criteria are in [DIPLOMA_COMPLETION_PLAN.md](DIPLOMA_COMPLETION_PLAN.md).
