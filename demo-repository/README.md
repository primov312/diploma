# Rocket Credit frontend

Customer-facing demo catalogs, account estimates and saved financing decisions.

## Development

From this directory:

```bash
npm install
npm run dev -- --host 127.0.0.1
```

## Verification

```bash
npm run build
./node_modules/.bin/tsc --noEmit -p tsconfig.json
BASE_URL=http://127.0.0.1:5173 ./node_modules/.bin/playwright test e2e/redesign.spec.ts --workers=1
```

The redesign browser suite uses stateful API mocks. It covers authentication, all seven dashboard selections and their URLs, financial/address/housing saves, sample verification, both enabled analyses, Facebook return messages, catalog filters and handoff, application submission and saved decisions. It also checks full width containers, navigation, keyboard access, expandable explanations and page overflow at 390, 768, 1280 and 1920px. The zoom check uses a 640px CSS viewport with 2x pixel scaling to represent a 1280px browser at 200% zoom.

The existing smoke, dashboard, location-pricing and monthly-living-cost suites require the running backend stack and imported datasets:

```bash
BASE_URL=http://localhost:8080 ./node_modules/.bin/playwright test e2e/smoke.spec.ts e2e/dashboard-sections.spec.ts e2e/location-pricing.spec.ts e2e/monthly-living-costs.spec.ts
```

## Design

Confirmed audience and design principles are recorded in [the project design context](../.impeccable.md). The shared theme uses navy navigation, light surfaces, Source Sans 3, outline icons and visible short labels. The font is bundled locally with its open font license. Methodology and technical metadata remain available in expandable details.

The shared footer and About Us page are removed; old `/about-us` links redirect home. Location and social demo analyses remain enabled.
