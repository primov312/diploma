"""Download the curated, licensed stock photos for the demo catalog.

Run: python3 scripts/fetch_catalog_images.py
Files, source/license metadata, and a browser-readable credit page are stored in
demo-repository/public/catalog/. Re-running skips completed entries. Use --limit N
for a small trial or --force to re-download every pinned source.
Edit catalog_image_sources.json to select different images; changed URLs are fetched
on the next run. Python standard library and macOS sips are the only requirements.
"""

import argparse
from datetime import datetime, timezone
import html
import json
from pathlib import Path
import subprocess
import tempfile
import time
from urllib.parse import urlsplit
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from catalog_data import PRODUCTS


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "demo-repository/public/catalog"
MANIFEST = OUTPUT / "sources.json"
SOURCES = Path(__file__).with_name("catalog_image_sources.json")
USER_AGENT = "RocketCreditDiplomaDemo/1.0 (private academic demo; image source credits saved locally)"
MAX_BYTES = 6_000_000
ALLOWED_HOSTS = {"thumb.wikimedia.org", "upload.wikimedia.org", "live.staticflickr.com",
                 "cdn.stocksnap.io", "images.pexels.com"}


def request_bytes(url):
    req = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json,image/*"})
    for attempt in range(5):
        try:
            with urlopen(req, timeout=35) as response:
                data = response.read(MAX_BYTES + 1)
                if len(data) > MAX_BYTES:
                    raise ValueError(f"oversized response: {url}")
                return data
        except HTTPError as exc:
            if exc.code not in {429, 502, 503, 504} or attempt == 4:
                raise
            retry_after = exc.headers.get("Retry-After", "")
            delay = min(30, int(retry_after)) if retry_after.isdigit() else min(30, 2 ** (attempt + 2))
            print(f"Source rate-limited ({exc.code}); retrying in {delay}s", flush=True)
            time.sleep(delay)
    raise RuntimeError("unreachable")


def save_photo(photo, destination):
    url = urlsplit(photo["downloadUrl"])
    if url.scheme != "https" or url.hostname not in ALLOWED_HOSTS:
        raise ValueError("image source must be HTTPS on an approved photo host")
    data = request_bytes(photo["downloadUrl"])
    with tempfile.TemporaryDirectory(prefix="rocket-catalog-") as temp_dir:
        raw = Path(temp_dir) / "source-image"
        raw.write_bytes(data)
        result = subprocess.run(
            ["sips", "-s", "format", "jpeg", "-s", "formatOptions", "78",
             "--resampleWidth", "800", str(raw), "--out", str(destination)],
            capture_output=True, text=True, check=False,
        )
        if result.returncode != 0 or not destination.is_file() or destination.stat().st_size == 0:
            destination.unlink(missing_ok=True)
            raise ValueError(f"image conversion failed: {result.stderr.strip()}")


def write_credits(records):
    rows = []
    for fixture_id, item in sorted(records.items()):
        source = html.escape(item["source"], quote=True)
        license_url = html.escape(item["licenseUrl"], quote=True)
        rows.append(
            "<tr><td><img src='" + html.escape(fixture_id, quote=True) + ".jpg' alt='' width='96'></td>"
            "<td>" + html.escape(fixture_id) + "</td><td><a href='" + source + "'>"
            + html.escape(item["title"]) + "</a></td><td>" + html.escape(item["author"])
            + "</td><td><a href='" + license_url + "'>" + html.escape(item["license"])
            + "</a></td></tr>"
        )
    document = """<!doctype html><html lang='en'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
<title>Demo catalog image credits</title><style>body{font:16px system-ui;margin:2rem auto;max-width:1100px;padding:0 1rem;color:#192132}table{border-collapse:collapse;width:100%}td,th{padding:.6rem;border-bottom:1px solid #d4d9e2;text-align:left}img{height:64px;object-fit:cover}a{color:#184a9b}</style>
<h1>Demo catalog image credits</h1><p>Images are illustrative stock photos, not official brand listings or film posters. Photos were resized and converted to JPEG for this private academic demo. Each file keeps the license shown below.</p>
<table><thead><tr><th>Image</th><th>Catalog ID</th><th>Source image</th><th>Author</th><th>License</th></tr></thead><tbody>"""
    document += "".join(rows) + "</tbody></table></html>\n"
    (OUTPUT / "credits.html").write_text(document, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, help="process at most this many missing entries")
    parser.add_argument("--force", action="store_true", help="replace all selected images")
    parser.add_argument("--replace", nargs="+", metavar="FIXTURE_ID", help="download the pinned source again for only these fixture IDs")

    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    records = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {}
    sources = json.loads(SOURCES.read_text(encoding="utf-8"))
    fixture_ids = {item[0] for item in PRODUCTS}
    if sources.keys() != fixture_ids:
        parser.error("curated source list must cover exactly the catalog's fixture IDs")
    if args.replace and not set(args.replace) <= fixture_ids:
        parser.error("unknown fixture ID in --replace")
    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be positive")
    processed = 0
    attempted = 0
    failures = []
    for fixture_id, _name, _price, _category, _description, query in PRODUCTS:
        if args.replace and fixture_id not in args.replace:
            continue
        destination = OUTPUT / f"{fixture_id}.jpg"
        photo = dict(sources[fixture_id])
        existing = records.get(fixture_id, {})
        if (not (args.force or args.replace) and destination.is_file()
                and existing.get("downloadUrl") == photo["downloadUrl"]):
            records[fixture_id] = {**photo, "query": query, "downloadedAt": existing.get("downloadedAt", "")}
            continue
        if args.limit is not None and attempted >= args.limit:
            break
        attempted += 1
        temporary_destination = destination.with_name(destination.name + ".part.jpg")
        try:
            save_photo(photo, temporary_destination)
            temporary_destination.replace(destination)
            photo["query"] = query
            photo["downloadedAt"] = datetime.now(timezone.utc).isoformat()
            records[fixture_id] = photo
            MANIFEST.write_text(json.dumps(records, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            write_credits(records)
            processed += 1
            print(f"[{len(records)}/{len(PRODUCTS)}] {fixture_id}: {photo['title']}", flush=True)
        except Exception as exc:
            temporary_destination.unlink(missing_ok=True)
            failures.append((fixture_id, str(exc)))
            print(f"FAILED {fixture_id}: {exc}", flush=True)
        time.sleep(1.2)
    MANIFEST.write_text(json.dumps(records, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_credits(records)
    print(f"Stored {len(records)} images; {processed} downloaded; {len(failures)} failed this run", flush=True)
    for fixture_id, error in failures:
        print(f"  {fixture_id}: {error}", flush=True)
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
