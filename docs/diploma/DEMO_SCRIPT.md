# Demo script (about 10 minutes)

For the diploma defence. Each step names what to click, what the audience should see, and which research topic it
shows. Amounts are in HUF; all money is simulated.

## Before the session

```bash
cd rocket-credit-deployment
docker compose -f docker-compose.diploma.yml --env-file diploma/.env up --build -d
./diploma/import-prices.sh            # only after creating a new database volume
curl -s localhost:8080/api/health     # {"backend":"ok","analysis":"ok","policyVersion":"rules-v4"}
```

Open http://localhost:8080 in a normal and a private browser window. Optional: set `ANALYSIS_DEMO_PROVIDER_MODE=AI`
and a `TYPESAFE_API_KEY` (Jev, the default provider) to show live AI results in step 6.

## 1. Marketplace and sign-in — authentication (1 min)

1. Home → **Browse marketplace**. One marketplace for all three sellers; filter **Fashion**, search "linen".
2. Press **Apply for credit** on *Linen shirt* → the app asks you to sign in (private page).
3. On the sign-in page press the demo account **Avery · approved** → **Sign in**. You land back on the application
   with the product already chosen.

Say: passwords are BCrypt hashes; the session cookie is HttpOnly; every change sends a CSRF token; the session ID
changes at login.

## 2. Apply and get a decision — modular scoring (2 min)

1. **Continue** → *Monthly* → **6 months** → **See my payments**. The full schedule with interest is shown before
   anything is decided.
2. Tick **Also use AI analysis** → **Get my decision**.
3. The result page leads with **Approved**, the amount and one sentence; below are the repayment plan, the reasons
   and the score breakdown (history 60 %, profile 40 %, AI 20 % of the final score).
4. Open **Technical details**: policy `rules-v4`, model `logreg-v1`, features prepared *parallel*.

Say: profile, history and affordability are separate modules with their own reasons; the combiner applies a short
precedence list; capacity and the seller limit are checked before the score.

## 3. Accept and repay — simulated money (1 min)

1. **Accept and pay seller** → Wallet → Repayments shows six monthly payments.
2. Wallet → Balance → add 50 000 Ft → Repayments → **Pay off early** shows the interest you save.

## 4. A different customer — explainable outcomes (2 min)

Sign out, sign in as **Riley · review**.

1. Apply → **Another amount** → 18 000 HUF at Amazon → 6 months → **Get my decision** → **Needs review** with
   "Your profile is not complete" and "Many of your purchases with this seller were refunded".
2. In the private window sign in as **Drew · rejected** and request 54 000 HUF at Zara → **Not approved**
   (new account, no money left after costs).
3. Back as Avery, request 576 000 HUF at Amazon → **Not approved**, with **Try 540 000 Ft instead**.

Say: the same rules give approval, review and rejection with readable reasons
([expected outcomes](../DEMO_SCENARIOS.md)).

## 5. Budget and data keeping — safe data (1 min)

As Riley: Dashboard → **Budget**. Enter postcode 1051, *Minta utca*, building 12 → **Save address** →
**Use the sample card** (verifies the synthetic address card) → the monthly living costs for district V now apply.

Say: Python receives only derived numbers; the backend uses a restricted database role; uploaded images are checked
and deleted; tokens are encrypted ([SAFE_DATA_KEEPING.md](../SAFE_DATA_KEEPING.md)). Try opening another customer's
application URL in the private window: "does not exist or is not yours".

## 6. Where you spend time — AI and the district map (2 min)

Dashboard → **Activity**.

1. *A month in Budapest* → **Analyze visits**. The map shades the districts by visits; XIII (6 visits) has the dark
   outline, district V (the saved address) the dashed one. Click **VIII** to list only its visits with prices.
2. Social activity → *Repetitive posting (sample)* → **Analyze posts**. Rule-based metrics; with an AI key the
   badge reads *Jev · jev-1.13.0* and each finding cites post IDs. With a connected Facebook account choose
   **My Facebook posts**.

Say: the trained model only adjusts the score near the thresholds (AUC 0.71 → 0.80); Jev only answers narrow questions and code writes the findings; neither decides alone.

## 7. Multithreading — the measurement (30 s)

Show `research/benchmarks/README.md`: three independent providers on a 3-thread pool, median 2.07 → 0.72 ms locally
and about 3× with simulated I/O delay; identical results in 1,300 runs.

## If something goes wrong

| Symptom | Fix |
| --- | --- |
| Budget says “Price data is not loaded” | `./diploma/import-prices.sh` |
| Every page signs you out | The backend restarted; sessions live in memory. Sign in again. |
| "AI analysis is unavailable" | Expected without an AI provider key; the rule-based result is shown. |
| Facebook says the URL is blocked | Add `http://localhost:8080/api/me/social-connection/facebook/callback` to the Meta app. |
