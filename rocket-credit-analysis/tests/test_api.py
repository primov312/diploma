from fastapi.testclient import TestClient

from app.main import app

HEADERS = {"X-Analysis-Token": "test-secret"}


def test_health_is_public():
    with TestClient(app) as c:
        r = c.get("/health")
    assert r.status_code == 200 and r.json()["status"] == "ok" and r.json()["policyVersion"] == "rules-v1"


def test_score_requires_token(good_bundle):
    with TestClient(app) as c:
        assert c.post("/score", json=good_bundle).status_code == 401
        assert c.post("/score", json=good_bundle, headers={"X-Analysis-Token": "wrong"}).status_code == 401


def test_score_rejects_invalid_bundle(good_bundle):
    good_bundle["requestedAmount"] = "-5"
    with TestClient(app) as c:
        assert c.post("/score", json=good_bundle, headers=HEADERS).status_code == 422
    good_bundle["requestedAmount"] = "10"
    good_bundle["currency"] = "EUR"
    with TestClient(app) as c:
        assert c.post("/score", json=good_bundle, headers=HEADERS).status_code == 422
    del good_bundle["currency"]
    good_bundle["email"] = "leak@example.com"  # identifying fields are refused
    with TestClient(app) as c:
        assert c.post("/score", json=good_bundle, headers=HEADERS).status_code == 422


def test_score_returns_explained_decision(good_bundle):
    with TestClient(app) as c:
        r = c.post("/score", json=good_bundle, headers=HEADERS)
    assert r.status_code == 200
    body = r.json()
    assert body["decisionStatus"] == "APPROVED"
    assert set(body["factors"]) == {"profile", "history", "affordability"}
    assert body["policyVersion"] == "rules-v1"
    assert body["aiStatus"] == "NOT_REQUESTED"


def _repeating_posts():
    return [
        {"id": f"post-{n:02d}", "date": f"2026-08-{n:02d}T09:00:00Z", "location": None, "text": "Limited offer, message me now!",
         "reactions": 1, "comments": []}
        for n in range(1, 7)
    ]


def test_social_account_reports_owner_account_data():
    with TestClient(app) as c:
        body = c.post("/demo-analysis/social-account", json={"posts": _repeating_posts()}, headers=HEADERS).json()
    assert body["dataSource"] == "OWNER_ACCOUNT"
    assert body["analysisMode"] == "FIXTURE"
    assert {f["label"] for f in body["findings"]} >= {"REPETITION", "REGULAR_TIMING"}
    assert body["metrics"]["repeatedTextCount"] == 5
    assert body["evidence"][0]["id"] == "post-01"


def test_social_account_with_no_posts_is_inconclusive():
    with TestClient(app) as c:
        body = c.post("/demo-analysis/social-account", json={"posts": []}, headers=HEADERS).json()
    assert body["findings"][0]["confidence"] == "INCONCLUSIVE"


def test_social_account_requires_token_and_rejects_unknown_fields():
    with TestClient(app) as c:
        assert c.post("/demo-analysis/social-account", json={"posts": []}).status_code == 401
        assert c.post("/demo-analysis/social-account", json={"posts": [], "token": "x"}, headers=HEADERS).status_code == 422
