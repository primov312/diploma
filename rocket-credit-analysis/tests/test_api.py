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
