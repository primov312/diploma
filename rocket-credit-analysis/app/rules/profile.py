# app/rules/profile.py
from __future__ import annotations

from app.models import ProfileFeatures
from app.policy import Policy
from app.reasons import PROFILE_ACCOUNT_NEW, PROFILE_EMAIL_UNVERIFIED, PROFILE_INCOMPLETE
from app.rules.common import RuleResult, clamp01, safe_minmax


def score_profile(p: ProfileFeatures, policy: Policy) -> RuleResult:
    """Account age and completeness. Seeded profiles are synthetic, so this is
    intentionally simple and fully explainable."""
    cfg = policy.profile
    s_age = safe_minmax(p.accountAgeMonths, 0, cfg.get("accountAgeMonthsFullScore", 24))
    s_complete = 1.0 if p.profileComplete else 0.0
    s_verified = 1.0 if p.emailVerified else 0.0

    score = clamp01(0.50 * s_age + 0.30 * s_complete + 0.20 * s_verified)

    reasons = []
    if p.accountAgeMonths < cfg.get("newAccountMonths", 3):
        reasons.append(PROFILE_ACCOUNT_NEW)
    if not p.profileComplete:
        reasons.append(PROFILE_INCOMPLETE)
    if not p.emailVerified:
        reasons.append(PROFILE_EMAIL_UNVERIFIED)

    return RuleResult(
        score=score,
        reasons=reasons,
        details={
            "accountAgeScore": round(s_age, 4),
            "profileComplete": p.profileComplete,
            "emailVerified": p.emailVerified,
        },
    )
