# AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
# app/main.py
"""Rocket Credit - consent-based digital footprint collector (the "web scraping module").

    GET  /health               liveness (no auth; used by Compose)
    POST /collect              allowed public page -> normalized posts (auth)
    GET  /robots.txt           robots rules of the bundled demo source
    GET  /demo-source/{slug}   bundled synthetic public profiles

The service is stateless and has no database. It never decides whether the user consented: the Java backend
checks consent first and only then calls /collect with an address the user entered.
"""
from __future__ import annotations

import hmac
from functools import lru_cache
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, PlainTextResponse
from pydantic import BaseModel, ConfigDict, Field

from app import demo_source
from app.collect import CollectError, collect
from app.settings import Settings

app = FastAPI(title="Rocket Credit - Footprint Collector", version="1.0.0")


@lru_cache
def get_settings() -> Settings:
    return Settings()


def require_token(x_collector_token: Optional[str] = Header(default=None), cfg: Settings = Depends(get_settings)) -> None:
    if not cfg.shared_secret:
        return
    if x_collector_token is None or not hmac.compare_digest(x_collector_token, cfg.shared_secret):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid collector token")


class CollectRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    url: str = Field(..., min_length=8, max_length=300)


@app.exception_handler(CollectError)
def collect_error_handler(_: Request, exc: CollectError) -> JSONResponse:
    return JSONResponse(status_code=exc.http_status, content={"code": exc.code, "message": exc.message})


@app.get("/health")
def health(cfg: Settings = Depends(get_settings)):
    return {"status": "ok", "allowedHosts": sorted(cfg.allowed_host_set()), "format": "rocket-profile-v1"}


@app.post("/collect", dependencies=[Depends(require_token)])
def collect_page(req: CollectRequest, cfg: Settings = Depends(get_settings)):
    return collect(req.url, cfg)


@app.get("/robots.txt", response_class=PlainTextResponse)
def robots() -> str:
    return demo_source.ROBOTS_TXT


@app.get("/demo-source/{slug}", response_class=HTMLResponse)
def demo_profile(slug: str) -> str:
    html = demo_source.render(slug)
    if html is None:
        raise HTTPException(status_code=404, detail="unknown demo profile")
    return html


@app.get("/private/{slug}", response_class=HTMLResponse)
def private_page(slug: str) -> str:
    """Exists only so tests can show that robots.txt is respected; the collector never requests it."""
    return "<html><body>private</body></html>"
