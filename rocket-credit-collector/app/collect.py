# AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
"""Consent-based collection of a public profile page.

Order of checks, each one a hard stop:
  1. the URL is plain http(s) without credentials;
  2. its host is on the operator's allow-list;
  3. the site's robots.txt allows our user agent to read that path;
  4. the response is HTML, small enough, and not a redirect;
  5. the page contains posts in the documented format.
Nothing is stored. The caller (the Java backend) decides whether the user has given consent.
"""
from __future__ import annotations

from datetime import datetime, timezone
from urllib.parse import urlsplit
from urllib.robotparser import RobotFileParser

import httpx

from app.parser import parse_profile
from app.settings import Settings


class CollectError(Exception):
    def __init__(self, code: str, message: str, http_status: int) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.http_status = http_status


def validate_url(url: str, settings: Settings):
    try:
        parts = urlsplit(url.strip())
        host = (parts.hostname or "").lower()
        parts.port  # raises ValueError for a malformed port
    except ValueError as exc:
        raise CollectError("INVALID_URL", "The address is not a valid web address.", 400) from exc
    if parts.scheme not in ("http", "https") or not host:
        raise CollectError("INVALID_URL", "Use a full http or https address.", 400)
    if parts.username or parts.password:
        raise CollectError("INVALID_URL", "Addresses with a user name or password are not accepted.", 400)
    if host not in settings.allowed_host_set():
        raise CollectError("SOURCE_NOT_ALLOWED", "This website is not on the list of allowed sources.", 403)
    return parts


def _robots_allows(client: httpx.Client, parts, settings: Settings) -> bool:
    robots_url = f"{parts.scheme}://{parts.netloc}/robots.txt"
    try:
        response = client.get(robots_url)
    except httpx.HTTPError:
        return False  # cannot tell, so do not read
    if response.status_code in (404, 410):
        return True  # no robots.txt means no restriction
    if response.status_code != 200:
        return False
    parser = RobotFileParser()
    parser.parse(response.text[:20_000].splitlines())
    path = parts.path or "/"
    if parts.query:
        path += "?" + parts.query
    return parser.can_fetch(settings.user_agent, f"{parts.scheme}://{parts.netloc}{path}")


def _read_limited(client: httpx.Client, url: str, limit: int) -> tuple[httpx.Response, bytes]:
    with client.stream("GET", url) as response:
        body = bytearray()
        for chunk in response.iter_bytes():
            body.extend(chunk)
            if len(body) > limit:
                raise CollectError("SOURCE_TOO_LARGE", "The page is larger than the collector accepts.", 502)
        return response, bytes(body)


def collect(url: str, settings: Settings, client: httpx.Client | None = None) -> dict:
    parts = validate_url(url, settings)
    own_client = client is None
    if client is None:
        client = httpx.Client(timeout=settings.timeout_seconds, follow_redirects=False,
                              headers={"User-Agent": settings.user_agent, "Accept": "text/html"})
    try:
        if not _robots_allows(client, parts, settings):
            raise CollectError("ROBOTS_DISALLOWED", "The website's robots.txt does not allow reading this page.", 403)
        try:
            response, body = _read_limited(client, parts.geturl(), settings.max_bytes)
        except httpx.HTTPError as exc:
            raise CollectError("FETCH_FAILED", "The page could not be fetched.", 502) from exc
    finally:
        if own_client:
            client.close()
    if 300 <= response.status_code < 400:
        raise CollectError("FETCH_FAILED", "The page redirects elsewhere; redirects are not followed.", 502)
    if response.status_code != 200:
        raise CollectError("FETCH_FAILED", f"The page answered with HTTP {response.status_code}.", 502)
    if not response.headers.get("content-type", "").lower().startswith("text/html"):
        raise CollectError("UNSUPPORTED_CONTENT", "Only HTML pages can be read.", 422)
    html = body.decode(response.encoding or "utf-8", errors="replace")
    posts, skipped = parse_profile(html, max_posts=settings.max_posts, max_text_chars=settings.max_text_chars)
    if not posts:
        raise CollectError("NO_POSTS", "No posts in the supported format were found on this page.", 422)
    return {
        "source": {"url": parts.geturl(), "host": parts.hostname, "format": "rocket-profile-v1",
                   "robots": "ALLOWED", "fetchedAt": datetime.now(timezone.utc).isoformat()},
        "posts": posts,
        "skipped": skipped,
    }
