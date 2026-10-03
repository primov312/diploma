# Demo catalog and image sources

The three catalogs use the display names **Netflix**, **Amazon**, and **Zara** for this private diploma demonstration. The database keeps its original internal partner slugs (`streambox`, `markethub`, `threadly`) so existing applications and transaction records retain their IDs. Product names, descriptions, and USD prices are illustrative; the catalog is not an official feed from any of these companies.

`scripts/catalog_data.py` is the source for the 90 entries (30 per partner). After editing it, run `python3 scripts/build_catalog_migration.py` to regenerate the additive V7 Flyway migration. Do not edit V2 or change existing fixture IDs. The generated migration adds descriptions, categories, local image paths, and alt text; it updates visible partner names and inserts/updates products using `fixture_id`.

Images are locally cached photos from Wikimedia Commons, Pexels, and CC0 stock collections indexed by Openverse. `scripts/catalog_image_sources.json` pins one selected source per product so reruns reproduce the reviewed selection. The source list includes the photo page, download URL, author/credit reference, and license. No API key is needed.

Run `python3 scripts/fetch_catalog_images.py` from the repository root. It requires Python 3 and macOS `sips`, downloads each image with a 6 MB response limit, converts it to an 800-pixel JPEG, and saves it in `demo-repository/public/catalog/`. Completed files are reused; changing a source URL causes that image to be downloaded on the next run. Downloads replace existing files only after successful conversion, so a failed download preserves the previous photo and returns a nonzero exit code.

Use `--limit 3` for a small batch, `--replace threadly-wool-coat` to download that pinned image again, or `--force` to download all sources again. To choose a different photo, edit the corresponding entry in `catalog_image_sources.json`, then rerun the script. Review the new photo in the store before presentation.

`demo-repository/public/catalog/sources.json` records the selected photo page, author/credit reference, license, license link, catalog search phrase, and download date for every cached image. The generated `credits.html` presents those details in the app. Photos illustrate the demo entries; they are not official product photos or Netflix posters. Commons files retain their individual licenses and credits as described in the [Commons reuse guide](https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia). Pexels photos use the [Pexels License](https://www.pexels.com/license/); their photo pages identify the photographers, including entries whose local credit directs readers to that page.

The public assets are copied into the Vite build, so the catalog displays without network access during the demo. If an image is missing, the store page shows `fallback.svg` and the missing asset must be fixed before presentation.
