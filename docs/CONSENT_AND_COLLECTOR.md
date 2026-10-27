<!-- AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools. -->
# Consent and the footprint collector

This document explains the two parts that make the application match the registered thesis topic: consent for data
processing, and a separate module that collects a consent-based digital footprint from allowed public pages.

## Consent

Three separate purposes, each with its own wording, stored in `consents/ConsentPolicy`:

| Purpose | Needed for | Withdrawing it |
| --- | --- | --- |
| `DATA_PROCESSING` | submitting any credit request | no new requests; existing decisions and repayment plans stay as a record |
| `AI_ANALYSIS` | the AI risk estimate (`useAi`) | AI estimate stops; rules-based decision stays |
| `DIGITAL_FOOTPRINT` | location and social analyses, including a public page | stored reports and the Facebook connection are deleted |

* Every grant or withdrawal is a new row in `consent_records` (append-only audit trail) with the policy version and a
  SHA-256 hash of the exact text shown. Repeating the same decision adds no row. A grant under an older wording does not count.
* Enforcement is in the backend, not only the UI: `ApplicationService.submit` requires `DATA_PROCESSING` (and `AI_ANALYSIS`
  when `useAi`), `DemoSignalService.run` requires `DIGITAL_FOOTPRINT`. A missing consent is `403 CONSENT_REQUIRED`.
* The decision stores the consents in force at that moment in `credit_applications.consent_snapshot`.
* Withdrawal publishes a `ConsentWithdrawnEvent` inside the same transaction; `signals/FootprintErasure` deletes what was
  collected, moves the permission generation on (so a run in flight is discarded) and queues a budget recalculation.
* Screens: the **Privacy** page (`/consents`), the consent box in the last step of **Apply**, and a consent card on the
  dashboard **Activity** tab.
* Demo personas (Avery, Riley, Drew, Casey) are created by the fixture loader with every consent granted; their rows have
  `source = DEMO_FIXTURE`. New registrations start with no consent.

API: `GET /api/me/consents`, `PUT /api/me/consents/{purpose}` with `{"granted": true|false}`.

## Footprint collector (the web scraping module)

`rocket-credit-collector` is a small stateless FastAPI service on the internal network (port 8100, no host port).

Order of checks for `POST /collect {url}` (each is a hard stop):

1. plain `http`/`https`, no user name or password in the address (`INVALID_URL`);
2. the host is on `COLLECTOR_ALLOWED_HOSTS` (`SOURCE_NOT_ALLOWED`); the default is only the built-in demo source;
3. the site's `robots.txt` allows our user agent to read the path (`ROBOTS_DISALLOWED`; a robots.txt that cannot be read counts as "no");
4. the answer is HTML, within 256 KiB, and not a redirect (`FETCH_FAILED`, `SOURCE_TOO_LARGE`, `UNSUPPORTED_CONTENT`);
5. the page contains posts in the documented format `rocket-profile-v1` (`NO_POSTS`).

The backend asks the collector only after it has checked the `DIGITAL_FOOTPRINT` consent. The posts it returns go through
the same analysis as Facebook posts (`/demo-analysis/social-account` with `dataSource = PUBLIC_PAGE`). The stored report
carries a `collection` object (address, host, format, robots decision, time) as evidence of where the data came from.
Nothing is stored in the collector.

The collector also serves a synthetic demo site (`/demo-source/ordinary|repetitive|sparse|empty`, `/robots.txt` that allows
`/demo-source/` and forbids `/private/`), so the flow can be shown and tested offline.

Known limits: the allow-list names hosts, not IP ranges (an operator must not allow a host that resolves to the internal
network); the parser reads only the documented markup; one page per run.
