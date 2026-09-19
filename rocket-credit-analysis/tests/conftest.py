import os

import pytest

os.environ.setdefault("ANALYSIS_SHARED_SECRET", "test-secret")

from app.policy import load_policy  # noqa: E402


@pytest.fixture(scope="session")
def policy():
    return load_policy("app/policy.json")


@pytest.fixture
def good_bundle():
    """A customer with long, clean partner history and clear disposable income."""
    return {
        "requestedAmount": "300.00",
        "currency": "USD",
        "partnerCap": "1500.00",
        "useAi": False,
        "observedAt": "2026-09-19T10:00:00Z",
        "profile": {"accountAgeMonths": 30, "profileComplete": True, "emailVerified": True},
        "history": {
            "partnerOrders12m": 18,
            "partnerAvgOrderValue": "125.00",
            "partnerRefundRate": 0.02,
            "partnerOnTimeRatio": 0.98,
            "partnerTenureMonths": 36,
            "totalOrders12m": 40,
        },
        "finance": {"monthlyIncome": "4500.00", "monthlyExpenses": "2500.00", "monthlyObligations": "300.00"},
    }
