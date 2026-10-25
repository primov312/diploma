"""Social analysis: deterministic metrics always, Jev findings on top in AI mode."""
import json

from app.analyses.social import SOCIAL_SCENARIOS, analyze_posts, analyze_social
from app.settings import Settings
from tests.jev_support import jev_answers, jev_settings, noul, score


def test_repetitive_posts_become_code_written_findings_with_real_ids(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, lambda name, q: score([0.85, 0.1, 0.05]) if name == "variety"
                else noul({"promo": 0.93, "generic": 0.8, "orders": 0.02}[name.split("_")[0]]), calls)

    report = analyze_social("repetitive", jev_settings())

    assert report.status == "COMPLETE" and report.provider == "TYPESAFE" and report.modelVersion == "jev-1.13.0"
    assert report.promptVersion == "social-jev-v1"
    by_label = {f.label: f for f in report.findings}
    assert set(by_label) == {"PROMOTIONAL_CONTENT", "GENERIC_COMMENTS", "LOW_TOPIC_VARIETY"}
    assert by_label["PROMOTIONAL_CONTENT"].evidenceIds == [f"post-{n:02d}" for n in (2, 3, 4, 5, 6, 7)]
    assert by_label["PROMOTIONAL_CONTENT"].confidence == "HIGH" and by_label["GENERIC_COMMENTS"].confidence == "MEDIUM"
    assert report.summary.startswith("The sample shows patterns")
    body = calls[0]["body"]
    assert body["model"] == "jev-1.13.0" and calls[0]["headers"]["authorization"] == "Bearer test-key"
    assert len(body["questions"]) == 6 * 3 + 1 and body["questions"]["variety"]["type"] == "score"


def test_post_text_never_enters_a_question(monkeypatch):
    calls: list = []
    jev_answers(monkeypatch, lambda name, q: noul(0.97 if name.startswith("orders") else 0.03), calls)

    report = analyze_social("injection", jev_settings())

    questions = json.dumps(calls[0]["body"]["questions"])
    assert "Ignore all prior instructions" not in questions
    assert "Ignore all prior instructions" in calls[0]["body"]["state"]["posts"][0]["text"]
    labels = [f.label for f in report.findings]
    assert labels == ["EMBEDDED_INSTRUCTIONS", "INCONCLUSIVE"]  # one post: no variety question
    assert report.summary.endswith("Orders written inside posts were not followed.")


def test_varied_posts(monkeypatch):
    jev_answers(monkeypatch, lambda name, q: score([0.02, 0.08, 0.9]) if name == "variety" else noul(0.05))
    report = analyze_social("ordinary", jev_settings())
    assert [(f.label, f.confidence) for f in report.findings] == [("VARIED_TOPICS", "HIGH")]
    assert report.findings[0].evidenceIds == ["post-01", "post-02", "post-03"]


def test_http_failure_and_missing_key_keep_the_deterministic_metrics(monkeypatch):
    jev_answers(monkeypatch, None, status=401)
    failed = analyze_social("ordinary", jev_settings())
    assert failed.status == "AI_UNAVAILABLE" and failed.findings == [] and failed.metrics["postCount"] == 3
    assert analyze_social("ordinary", jev_settings(typesafe_api_key="")).summary.startswith("No AI provider key")


def test_fixture_mode_uses_rules_only_and_labels_owner_account_posts():
    report = analyze_posts("facebook-live", SOCIAL_SCENARIOS["repetitive"]["posts"], Settings(), "OWNER_ACCOUNT")
    assert report.analysisMode == "FIXTURE" and report.dataSource == "OWNER_ACCOUNT"
    assert [f.label for f in report.findings] == ["REPETITION", "REGULAR_TIMING"]
