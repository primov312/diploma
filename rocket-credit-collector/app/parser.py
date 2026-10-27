# AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
"""Turns a public profile page into the post shape the analysis service expects.

The page format is deliberately small and documented ("rocket-profile-v1"):

    <article class="post" data-date="2026-08-02" data-location="Budapest V" data-reactions="18">
      <p class="post-text">Afternoon walk near the river.</p>
      <ul><li class="comment">Nice view</li></ul>
    </article>

Anything that does not follow the format is ignored, never guessed. The parser uses only the standard library
and never executes scripts or follows links.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from html.parser import HTMLParser


@dataclass
class _Post:
    date: str
    location: str | None
    reactions: int
    text: list[str] = field(default_factory=list)
    comments: list[list[str]] = field(default_factory=list)


class _ProfileParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.posts: list[_Post] = []
        self.skipped = 0
        self._post: _Post | None = None
        self._in_text = False
        self._comment: list[str] | None = None
        self._skip_depth = 0  # script/style content is never read

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        a = {k: (v or "") for k, v in attrs}
        classes = a.get("class", "").split()
        if tag in ("script", "style"):
            self._skip_depth += 1
        elif tag == "article" and "post" in classes:
            self._post = self._new_post(a)
        elif self._post is not None and tag == "p" and "post-text" in classes:
            self._in_text = True
        elif self._post is not None and tag == "li" and "comment" in classes:
            self._comment = []

    def handle_endtag(self, tag: str) -> None:
        if tag in ("script", "style") and self._skip_depth:
            self._skip_depth -= 1
        elif tag == "p":
            self._in_text = False
        elif tag == "li" and self._comment is not None and self._post is not None:
            self._post.comments.append(self._comment)
            self._comment = None
        elif tag == "article" and self._post is not None:
            self.posts.append(self._post)
            self._post = None

    def handle_data(self, data: str) -> None:
        if self._skip_depth or self._post is None:
            return
        if self._comment is not None:
            self._comment.append(data)
        elif self._in_text:
            self._post.text.append(data)

    def _new_post(self, attrs: dict[str, str]) -> _Post:
        raw_date = attrs.get("data-date", "")
        try:
            iso = date.fromisoformat(raw_date[:10]).isoformat()
        except ValueError:
            iso = ""
            self.skipped += 1
        try:
            reactions = max(0, int(attrs.get("data-reactions", "0")))
        except ValueError:
            reactions = 0
        location = attrs.get("data-location", "").strip() or None
        return _Post(date=iso, location=location, reactions=reactions)


def parse_profile(html: str, *, max_posts: int, max_text_chars: int) -> tuple[list[dict], int]:
    """Return (posts, skipped). A post without a valid ISO date is skipped and counted."""
    parser = _ProfileParser()
    parser.feed(html)
    parser.close()
    posts: list[dict] = []
    skipped = parser.skipped
    for raw in parser.posts:
        if not raw.date:
            continue
        if len(posts) >= max_posts:
            skipped += 1
            continue
        number = len(posts) + 1
        comments = []
        for c in raw.comments[:25]:
            text = " ".join("".join(c).split())[:500]
            if text:
                comments.append({"id": f"comment-{number:02d}-{len(comments) + 1:02d}", "text": text})
        posts.append({
            "id": f"post-{number:02d}",
            "date": raw.date,
            "location": raw.location[:120] if raw.location else None,
            "text": " ".join("".join(raw.text).split())[:max_text_chars],
            "reactions": raw.reactions,
            "comments": comments,
        })
    return posts, skipped
