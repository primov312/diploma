# Social account analysis (own Facebook account)

The optional social analysis can run on **the user's own Facebook account**. The user signs in with Facebook; the
backend then fetches recent posts automatically and the analysis service checks them for automation-like patterns.
The result is informational: it has **zero weight** in affordability and credit scoring.

## Flow

1. Dashboard → **Activity** tab → *Social activity* → **Connect Facebook**.
2. Facebook's own login page opens (`auth_type=rerequest`, so a previously declined permission is asked for again). Signing in there is the ownership proof; this app never sees the password.
3. Facebook redirects to `/api/me/social-connection/facebook/callback`. The backend checks the one-time `state`,
   exchanges the code for a token, reads `/me/permissions` and **refuses the connection if `user_posts` was not granted**
   (the token is revoked, nothing is stored), otherwise stores the token encrypted (AES-256-GCM) and returns to
   `/account-dashboard?section=activity&social=<outcome>`. Outcomes: `connected`, `denied` (cancelled), `permission`
   (posts not allowed), `linked` (account already used by another user), `session` (the app session expired during
   the Facebook login — the route is public so the user is sent back to sign in instead of seeing a bare 401), `error`.
4. **Analyze posts** with source *My Facebook posts* (`scenarioId = facebook-live`):
   backend reads `/me/posts` (max 50 posts, 25 comments each) → normalises to the analysis shape with local IDs
   (`post-01`, `comment-01-1`; Graph IDs never leave the backend) → `POST /demo-analysis/social-account` →
   report saved in `demo_signal_reports` with `data_source = OWNER_ACCOUNT`.
5. **Disconnect** deletes the stored token and asks Facebook to revoke the app's permissions.

## What is analysed

Deterministic metrics (post/comment/reaction counts, exact and near-duplicate text, mean posting interval,
interval variation, distinct posting hours) and, in `AI` mode with a server-side key, model findings
with evidence IDs (Jev: per-post yes/no judgments turned into findings by code; Gemini/Claude: written findings checked against the post IDs). Findings use `LOW`/`MEDIUM`/`HIGH`/`INCONCLUSIVE` and never assert that an account is fake or that
a person is a bot. Without credentials the report says `FIXTURE`/`AI_UNAVAILABLE`, never a fabricated live run.
Post text is treated as untrusted content in the model prompt.

## Meta app setup (once)

1. <https://developers.facebook.com> → **Create app**. Use case: *Authenticate and request data from users with
   Facebook Login*. Keep the app in **Development** mode; your account is its administrator.
2. Add the **Facebook Login** product. Under *Valid OAuth Redirect URIs* add the URL(s) you will use:
   - dev server: `http://localhost:5173/api/me/social-connection/facebook/callback`
   - Docker runtime: `http://localhost:8080/api/me/social-connection/facebook/callback`
3. Add the `user_posts` permission to the app. In Development mode, people with an app role (administrator,
   developer, tester) can use it without App Review. Add other demo users as *Testers*.
4. Put the credentials in the ignored `rocket-credit-deployment/diploma/.env` (Compose) or the backend environment:

   | Variable | Meaning |
   | --- | --- |
   | `META_APP_ID`, `META_APP_SECRET` | From the Meta app dashboard. Without them the UI shows "not configured". |
   | `PUBLIC_BASE_URL` | Origin the browser uses; must match a registered redirect URI. Default `http://localhost:5173` (backend) / `http://localhost:8080` (Compose). |
   | `SOCIAL_TOKEN_KEY` | Optional passphrase for token encryption; defaults to a key derived from the app secret. |
   | `META_GRAPH_VERSION` | Graph API version, default `v23.0`. |

Verify the current permission names and Graph version in Meta's documentation when setting up; they change.

## Limits and privacy

- Only the connected account's own data is read, and only when the user presses run. Nothing runs in the background.
- Stored per user: the encrypted token, the display name, and report JSON (metrics, findings, and 120-character
  excerpts of cited posts). Disconnecting removes the token; reports stay until the user is deleted.
- With `ANALYSIS_DEMO_PROVIDER_MODE=AI`, the sampled posts are sent to the configured AI provider for that run (Jev receives at most 40 posts, text shortened to 280 characters). Use `FIXTURE` to keep
  everything local.
- Instagram is not supported: its API requires a Business/Creator account.
- Facebook only returns posts the app has permission to read; an empty result produces an `INCONCLUSIVE` report.

## Verification

- Python: `cd rocket-credit-analysis && .venv/bin/python -m pytest` (account endpoint, auth, validation).
- Java: `mvn test -Dtest='FacebookClientTest,SocialConnectionFlowTest'` — normalisation, URL building, token-error and
  permission-error mapping, cipher, and the full OAuth round trip with Graph mocked (forged state, declined `user_posts`,
  expired session, permission withdrawn later → `409 SOCIAL_PERMISSION_REQUIRED` instead of a generic 503).
- Browser: `e2e/dashboard-sections.spec.ts` checks the `connected`, `permission` and `session` messages.
- Meta credentials check (2026-10-22): the app ID/secret in `diploma/.env` obtain an app access token from Graph
  (`grant_type=client_credentials`), so the configured app is valid. A full login round trip needs the developer's own
  Facebook account and was not automated.
- Manual (needs your Meta app): connect, run *My Facebook posts*, confirm the badge reads *Your Facebook posts* and the
  affordable amount is unchanged, then disconnect. If you untick “posts” in Facebook's dialog, the app shows the
  *permission* message and stores nothing.
