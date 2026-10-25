"""Submit the demo-script requests for every persona and print the saved decisions.

Uses only the public HTTP API (session cookie + CSRF), exactly like the browser:

    python3 research/demo/persona_matrix.py http://localhost:8080 > research/demo/persona-matrix.md

Run it against a freshly started stack: each run adds applications to the demo accounts.
"""
import http.cookiejar
import json
import sys
import urllib.request

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8080"
PASSWORD = "rocket-demo-123"
# persona, partner, amount in USD (UI shows HUF at 360/USD), installments, use AI
CASES = [
    ("avery", "markethub", 300, 6, False),
    ("avery", "markethub", 300, 6, True),
    ("avery", "markethub", 1600, 6, False),
    ("riley", "markethub", 50, 6, False),
    ("riley", "markethub", 50, 6, True),
    ("drew", "threadly", 150, 6, False),
    ("drew", "threadly", 150, 6, True),
    ("casey", "streambox", 100, 6, False),
    ("casey", "markethub", 700, 6, False),
]


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def csrf(self) -> str:
        self.call("GET", "/api/csrf")
        return next(c.value for c in self.jar if c.name == "XSRF-TOKEN")

    def call(self, method: str, path: str, body=None, key: str | None = None):
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if method != "GET":
            headers["X-XSRF-TOKEN"] = self.csrf()
        if key:
            headers["Idempotency-Key"] = key
        request = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with self.http.open(request) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            return error.code, json.loads(error.read() or b"{}")


def main():
    print("| Persona | Seller | Amount | AI | Decision | Score | Possible amount | Reasons | AI status |")
    print("| --- | --- | ---: | --- | --- | ---: | ---: | --- | --- |")
    for n, (persona, partner, usd, count, ai) in enumerate(CASES):
        client = Client()
        status, _ = client.call("POST", "/api/auth/login", {"email": f"{persona}@demo.rocket.local", "password": PASSWORD})
        assert status == 200, f"login failed for {persona}: {status}"
        status, quote = client.call("POST", "/api/credit-quotes", {"partnerSlug": partner, "requestedAmount": usd,
                                                                    "termUnit": "MONTH", "installmentCount": count})
        if status != 200:
            print(f"| {persona} | {partner} | {usd * 360:,} HUF | {'yes' if ai else 'no'} | quote refused: {quote.get('error')} | | | | |")
            continue
        status, app = client.call("POST", "/api/applications", {"partnerSlug": partner, "requestedAmount": usd,
                                                               "quoteId": quote["id"], "useAi": ai}, key=f"matrix-{persona}-{n}-{quote['id']}")
        assert status in (200, 201), (status, app)
        print(f"| {persona.title()} | {app['partnerName']} | {usd * 360:,} HUF | {'yes' if ai else 'no'} | **{app['decisionStatus']}** "
              f"| {app['score']:.2f} | {app['possibleAmount'] * 360:,.0f} HUF | {', '.join(app['reasons']) or '—'} | {app['aiStatus']} |")


if __name__ == "__main__":
    main()
