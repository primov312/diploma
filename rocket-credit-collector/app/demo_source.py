# AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
"""A small built-in 'public profile' website so the whole collection flow can be shown and tested offline.

Profiles are synthetic. /robots.txt allows /demo-source/ and forbids /private/, which lets the thesis tests show
both an allowed and a refused read against the same host.
"""
from __future__ import annotations

from html import escape

ROBOTS_TXT = "User-agent: *\nAllow: /demo-source/\nDisallow: /private/\n"

PROFILES: dict[str, dict] = {
    "ordinary": {"name": "Demo profile: ordinary", "posts": [
        ("2026-08-02", "Budapest V", 18, "Afternoon walk near the river.", ["Nice view"]),
        ("2026-08-14", "Budapest XI", 26, "Coffee with friends.", ["Where is this?"]),
        ("2026-08-29", None, 11, "A quiet weekend at home.", []),
    ]},
    "repetitive": {"name": "Demo profile: repetitive", "posts": [
        (f"2026-08-{n:02d}", "Budapest V", 4, "Limited offer, message me now!", ["Great post"]) for n in (2, 3, 4, 5, 6, 7)
    ]},
    "sparse": {"name": "Demo profile: sparse", "posts": [("2026-08-10", None, 1, "Hello.", [])]},
    "empty": {"name": "Demo profile: no posts", "posts": []},
}


def render(slug: str) -> str | None:
    profile = PROFILES.get(slug)
    if profile is None:
        return None
    articles = []
    for date, location, reactions, text, comments in profile["posts"]:
        attrs = f'class="post" data-date="{escape(date)}" data-reactions="{reactions}"'
        if location:
            attrs += f' data-location="{escape(location)}"'
        items = "".join(f'<li class="comment">{escape(c)}</li>' for c in comments)
        articles.append(f"<article {attrs}><p class=\"post-text\">{escape(text)}</p><ul>{items}</ul></article>")
    return ("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            f"<title>{escape(profile['name'])}</title></head><body><h1>{escape(profile['name'])}</h1>"
            + "".join(articles) + "</body></html>")
