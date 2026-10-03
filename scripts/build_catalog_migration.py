"""Generate the additive V7 catalog migration from catalog_data.py.

Run from the repository root: python3 scripts/build_catalog_migration.py
The generated migration is committed; this script is never needed at app startup.
"""

from pathlib import Path

from catalog_data import PRODUCTS


ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "rocket-credit-backend/src/main/resources/db/migration/V7__expanded_demo_catalog.sql"
PARTNERS = {"streambox": "Netflix", "markethub": "Amazon", "threadly": "Zara"}


def literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def main() -> None:
    lines = [
        "-- Academic demo catalog. Keep existing partner/product IDs and internal slugs stable.",
        "-- All entries and prices are illustrative; stock photos are not official listings.",
        "ALTER TABLE products ADD COLUMN description VARCHAR(500);",
        "ALTER TABLE products ADD COLUMN category VARCHAR(50);",
        "ALTER TABLE products ADD COLUMN image_path VARCHAR(180);",
        "ALTER TABLE products ADD COLUMN image_alt VARCHAR(250);",
        "",
    ]
    for slug, name in PARTNERS.items():
        lines.append(f"UPDATE partners SET display_name = {literal(name)} WHERE slug = {literal(slug)};")
    lines.append("")
    # These are generated fixture labels, not user-entered purchase descriptions.
    for slug, old_name in [("streambox", "StreamBox"), ("markethub", "MarketHub"), ("threadly", "Threadly")]:
        lines.append(
            "UPDATE transactions SET description = " + literal(f"Demo purchase at {PARTNERS[slug]}")
            + " WHERE fixture_id LIKE " + literal(f"%-{slug}-%")
            + " AND description = " + literal(f"Demo purchase at {old_name}") + ";"
        )
    lines.extend(["", "INSERT INTO products (partner_id, fixture_id, name, price, description, category, image_path, image_alt)",
                  "SELECT p.id, c.fixture_id, c.name, c.price, c.description, c.category, c.image_path, c.image_alt", "FROM (VALUES"])
    rows = []
    for fixture_id, name, price, category, description, query in PRODUCTS:
        slug = fixture_id.split("-", 1)[0]
        assert slug in PARTNERS
        values = [slug, fixture_id, name, description, category, f"/catalog/{fixture_id}.jpg", f"Illustrative photo: {query}"]
        rows.append("  (" + ", ".join([literal(values[0]), literal(values[1]), literal(values[2]), price,
                                    *[literal(v) for v in values[3:]]]) + ")")
    lines.append(",\n".join(rows))
    lines.extend([
        ") AS c(partner_slug, fixture_id, name, price, description, category, image_path, image_alt)",
        "JOIN partners p ON p.slug = c.partner_slug",
        "ON CONFLICT (fixture_id) DO UPDATE SET",
        "  name = EXCLUDED.name, price = EXCLUDED.price, partner_id = EXCLUDED.partner_id,",
        "  description = EXCLUDED.description, category = EXCLUDED.category,",
        "  image_path = EXCLUDED.image_path, image_alt = EXCLUDED.image_alt;",
        "",
        "ALTER TABLE products ALTER COLUMN description SET NOT NULL;",
        "ALTER TABLE products ALTER COLUMN category SET NOT NULL;",
        "ALTER TABLE products ALTER COLUMN image_path SET NOT NULL;",
        "ALTER TABLE products ALTER COLUMN image_alt SET NOT NULL;",
        "",
    ])
    TARGET.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {TARGET} with {len(PRODUCTS)} products")


if __name__ == "__main__":
    main()
