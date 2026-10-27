# AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
import httpx
import pytest
from starlette.testclient import TestClient

from app.collect import CollectError, collect, validate_url
from app.main import app, get_settings
from app.parser import parse_profile
from app.settings import Settings

SETTINGS = Settings(shared_secret="", allowed_hosts="demo.test,localhost")


def demo_client() -> TestClient:
    """The collector's own demo site, reached without a network."""
    return TestClient(app, base_url="http://localhost", follow_redirects=False)


def code_of(call) -> str:
    with pytest.raises(CollectError) as info:
        call()
    return info.value.code


# --- URL policy -------------------------------------------------------------------------------------------

@pytest.mark.parametrize("url", ["ftp://localhost/x", "localhost/demo-source/ordinary", "http://", "javascript:alert(1)"])
def test_rejects_non_http_addresses(url):
    assert code_of(lambda: validate_url(url, SETTINGS)) == "INVALID_URL"


def test_rejects_credentials_in_address():
    assert code_of(lambda: validate_url("http://user:pw@localhost/demo-source/ordinary", SETTINGS)) == "INVALID_URL"


@pytest.mark.parametrize("url", ["http://example.com/profile", "http://169.254.169.254/latest/meta-data", "http://10.0.0.5/admin"])
def test_host_must_be_on_allow_list(url):
    assert code_of(lambda: validate_url(url, SETTINGS)) == "SOURCE_NOT_ALLOWED"


def test_allow_list_is_case_insensitive():
    assert validate_url("HTTP://LOCALHOST/demo-source/ordinary", SETTINGS).hostname == "localhost"


# --- collection against the bundled demo source ------------------------------------------------------------

def test_collects_posts_from_allowed_page():
    result = collect("http://localhost/demo-source/ordinary", SETTINGS, demo_client())
    assert result["source"]["robots"] == "ALLOWED"
    assert [p["id"] for p in result["posts"]] == ["post-01", "post-02", "post-03"]
    first = result["posts"][0]
    assert first["date"] == "2026-08-02" and first["location"] == "Budapest V" and first["reactions"] == 18
    assert first["comments"] == [{"id": "comment-01-01", "text": "Nice view"}]
    assert result["posts"][2]["location"] is None


def test_robots_txt_blocks_private_area():
    assert code_of(lambda: collect("http://localhost/private/notes", SETTINGS, demo_client())) == "ROBOTS_DISALLOWED"


def test_page_without_posts_is_reported():
    assert code_of(lambda: collect("http://localhost/demo-source/empty", SETTINGS, demo_client())) == "NO_POSTS"


def test_unknown_page_is_a_fetch_failure():
    assert code_of(lambda: collect("http://localhost/demo-source/nobody", SETTINGS, demo_client())) == "FETCH_FAILED"


# --- hostile or broken responses ---------------------------------------------------------------------------

def mock_client(handler) -> httpx.Client:
    return httpx.Client(transport=httpx.MockTransport(handler), follow_redirects=False)


def page_handler(status=200, content_type="text/html; charset=utf-8", body=b"", robots=404):
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/robots.txt":
            return httpx.Response(robots)
        return httpx.Response(status, headers={"content-type": content_type}, content=body)
    return handler


def test_missing_robots_txt_means_allowed():
    html = b'<article class="post" data-date="2026-01-02"><p class="post-text">Hi</p></article>'
    result = collect("http://demo.test/p", SETTINGS, mock_client(page_handler(body=html)))
    assert len(result["posts"]) == 1


def test_robots_server_error_is_treated_as_disallowed():
    assert code_of(lambda: collect("http://demo.test/p", SETTINGS, mock_client(page_handler(robots=503)))) == "ROBOTS_DISALLOWED"


def test_redirect_is_not_followed():
    def handler(request):
        if request.url.path == "/robots.txt":
            return httpx.Response(404)
        return httpx.Response(302, headers={"location": "http://169.254.169.254/"})
    assert code_of(lambda: collect("http://demo.test/p", SETTINGS, mock_client(handler))) == "FETCH_FAILED"


def test_non_html_content_is_refused():
    assert code_of(lambda: collect("http://demo.test/p", SETTINGS, mock_client(page_handler(content_type="application/json", body=b"{}")))) == "UNSUPPORTED_CONTENT"


def test_oversized_page_is_refused():
    small = Settings(shared_secret="", allowed_hosts="demo.test", max_bytes=100)
    assert code_of(lambda: collect("http://demo.test/p", small, mock_client(page_handler(body=b"x" * 500)))) == "SOURCE_TOO_LARGE"


# --- parser ------------------------------------------------------------------------------------------------

def test_parser_skips_post_without_valid_date_and_ignores_scripts():
    html = ('<script>var a="<article class=post data-date=2026-01-01>";</script>'
            '<article class="post" data-date="not-a-date"><p class="post-text">bad</p></article>'
            '<article class="post" data-date="2026-03-04" data-reactions="-5"><p class="post-text">  spaced \n out </p></article>')
    posts, skipped = parse_profile(html, max_posts=10, max_text_chars=100)
    assert skipped == 1
    assert posts == [{"id": "post-01", "date": "2026-03-04", "location": None, "text": "spaced out", "reactions": 0, "comments": []}]


def test_parser_enforces_limits():
    html = "".join(f'<article class="post" data-date="2026-01-{n:02d}"><p class="post-text">{"x" * 50}</p></article>' for n in range(1, 6))
    posts, skipped = parse_profile(html, max_posts=3, max_text_chars=10)
    assert len(posts) == 3 and skipped == 2 and all(len(p["text"]) == 10 for p in posts)


# --- HTTP interface ----------------------------------------------------------------------------------------

def test_collect_endpoint_requires_token():
    app.dependency_overrides[get_settings] = lambda: Settings(shared_secret="s3", allowed_hosts="localhost")
    try:
        client = TestClient(app)
        body = {"url": "http://localhost/demo-source/ordinary"}
        assert client.post("/collect", json=body).status_code == 401
        assert client.post("/collect", json=body, headers={"X-Collector-Token": "wrong"}).status_code == 401
        assert client.get("/health").status_code == 200
    finally:
        app.dependency_overrides.clear()


def test_collect_endpoint_maps_errors_to_codes():
    app.dependency_overrides[get_settings] = lambda: Settings(shared_secret="", allowed_hosts="localhost")
    try:
        client = TestClient(app)
        blocked = client.post("/collect", json={"url": "http://example.com/profile"})
        assert blocked.status_code == 403 and blocked.json()["code"] == "SOURCE_NOT_ALLOWED"
        assert client.post("/collect", json={"url": "nonsense-url", "extra": 1}).status_code == 422
    finally:
        app.dependency_overrides.clear()
