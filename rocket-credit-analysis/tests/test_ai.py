from decimal import Decimal

from fastapi.testclient import TestClient

from app.ai import featurize, load_model, predict
from app.main import app, get_model
from app.models import AiStatus, DecisionStatus, ScoreRequest
from app.reasons import AI_RISK_ELEVATED
from app.rules.combiner import combine
from tests.test_scenarios import AVERY, AVERY_MARKETHUB, DREW, DREW_THREADLY, request

HEADERS = {"X-Analysis-Token": "test-secret"}


def test_model_artifact_loads_and_is_consistent():
    m = load_model("app/model/model-v1.json")
    assert m is not None and m.version == "logreg-v1"
    assert len(m.features) == len(m.coef) == len(m.mean) == len(m.std) == 12


def test_prediction_orders_personas_sensibly(policy):
    m = load_model("app/model/model-v1.json")
    good = predict(m, request(AVERY, AVERY_MARKETHUB, "markethub", "300.00", use_ai=True))
    bad = predict(m, request(DREW, DREW_THREADLY, "threadly", "150.00", use_ai=True))
    assert 0.0 <= good.risk < bad.risk <= 1.0
    assert set(good.contributions) == set(m.features)
    assert bad.contributions["utilization"] > 0  # zero disposable income pushes risk up


def test_hybrid_uses_documented_weights(policy):
    m = load_model("app/model/model-v1.json")
    req = request(AVERY, AVERY_MARKETHUB, "markethub", "300.00", use_ai=True)
    rules_only = combine(req, policy, ai=None)
    ai = predict(m, req)
    hybrid = combine(req, policy, ai=ai)
    rules_score = rules_only.score
    expected = round(policy.weights["rules"] * rules_score + policy.weights["ai"] * (1 - ai.risk), 4)
    assert abs(hybrid.score - expected) < 0.002
    assert hybrid.aiStatus == AiStatus.APPLIED and hybrid.modelVersion == "logreg-v1"
    assert "ai" in hybrid.factors and hybrid.factors["ai"].details["modelVersion"] == "logreg-v1"
    assert hybrid.decisionStatus == DecisionStatus.APPROVED


def test_elevated_ai_risk_is_explained(policy):
    m = load_model("app/model/model-v1.json")
    req = request(DREW, DREW_THREADLY, "threadly", "150.00", use_ai=True)
    res = combine(req, policy, ai=predict(m, req))
    assert AI_RISK_ELEVATED in res.reasons
    assert res.decisionStatus == DecisionStatus.REJECTED  # zero capacity still dominates


def test_api_applies_model_only_when_requested(good_bundle):
    with TestClient(app) as c:
        h = c.get("/health").json()
        assert h["modelLoaded"] is True and h["modelVersion"] == "logreg-v1"

        off = c.post("/score", json=good_bundle, headers=HEADERS).json()
        assert off["aiStatus"] == "NOT_REQUESTED" and off["modelVersion"] is None and "ai" not in off["factors"]

        good_bundle["useAi"] = True
        on = c.post("/score", json=good_bundle, headers=HEADERS).json()
        assert on["aiStatus"] == "APPLIED" and on["modelVersion"] == "logreg-v1"
        assert on["factors"]["ai"]["details"]["predictedRisk"] < 0.5
        assert on["aiContributions"] and "utilization" in on["aiContributions"]


def test_missing_section_with_ai_falls_back_to_rules(good_bundle):
    good_bundle["useAi"] = True
    good_bundle["finance"] = None
    with TestClient(app) as c:
        r = c.post("/score", json=good_bundle, headers=HEADERS).json()
    assert r["aiStatus"] == "UNAVAILABLE" and r["decisionStatus"] == "REVIEW"


def test_missing_artifact_disables_ai_without_breaking_rules(good_bundle):
    app.dependency_overrides[get_model] = lambda: None
    try:
        good_bundle["useAi"] = True
        with TestClient(app) as c:
            assert c.get("/health").json()["modelLoaded"] is False
            r = c.post("/score", json=good_bundle, headers=HEADERS).json()
        assert r["aiStatus"] == "UNAVAILABLE" and r["decisionStatus"] == "APPROVED"
    finally:
        app.dependency_overrides.clear()
