"""Test helpers: run the real TypeSafe SDK with its HTTP layer mocked, and the shared sample files."""
import base64
import json
from pathlib import Path

import httpx2
import typesafe_sdk

from app.analyses import jev
from app.settings import Settings

ASSETS = Path(__file__).resolve().parents[2] / "demo-repository/src/assets"


def asset(name: str) -> str:
    return base64.b64encode((ASSETS / name).read_bytes()).decode()


def jev_settings(**overrides) -> Settings:
    values = {"demo_provider_mode": "AI", "typesafe_api_key": "test-key"}
    return Settings(**{**values, **overrides})


def noul(p: float) -> dict:
    return {"type": "noul", "noul": p}


def choice(picked: str, options: list[str], p: float = 0.95) -> dict:
    rest = (1 - p) / (len(options) - 1)
    return {"type": "choice", "choice": picked, "confidence": (p - 1 / len(options)) / (1 - 1 / len(options)),
            "probabilities": {o: p if o == picked else rest for o in options}}


def score(probabilities: list[float]) -> dict:
    return {"type": "score", "score": sum(i * p for i, p in enumerate(probabilities)), "confidence": 0.8,
            "legend": {str(i): f"level {i}" for i in range(len(probabilities))},
            "probabilities": {str(i): p for i, p in enumerate(probabilities)}}


def request_json(request: httpx2.Request) -> dict:
    """The SDK sends plain JSON, or multipart with the JSON in a part named `request` when images are attached."""
    if not request.headers["content-type"].startswith("multipart/"):
        return json.loads(request.content)
    part = request.content.split(b'name="request"', 1)[1].split(b"\r\n\r\n", 1)[1]
    return json.loads(part.split(b"\r\n--", 1)[0])


def jev_answers(monkeypatch, answer, calls: list | None = None, status: int = 200):
    """`answer(name, question)` returns the answer JSON for each question the analysis asked."""
    def handler(request: httpx2.Request) -> httpx2.Response:
        body = request_json(request)
        if calls is not None:
            calls.append({"body": body, "headers": dict(request.headers), "raw": request.content})
        if status != 200:
            return httpx2.Response(status, json={"error": {"message": "unavailable"}})
        answers = {name: answer(name, question) for name, question in body["questions"].items()}
        return httpx2.Response(200, json={"model": "jev-1.13.0", "answers": answers, "usage": {"input_tokens": 1, "output_tokens": 1}})

    real = typesafe_sdk.TypeSafeClient
    monkeypatch.setattr(jev.typesafe_sdk, "TypeSafeClient",
                        lambda **kwargs: real(**kwargs, transport=httpx2.MockTransport(handler)))


def document_answers(document_type: str, checks: dict[str, float] | None = None, default: float = 0.97):
    """Answers for an address or housing check: the document type, and a probability per claimed detail."""
    return lambda name, q: choice(document_type, list(q["criteria"])) if name == "documentType" \
        else noul((checks or {}).get(name, default))
