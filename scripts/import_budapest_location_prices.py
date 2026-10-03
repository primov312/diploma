#!/usr/bin/env python3
"""Research, compare and import Budapest location benchmarks; never infer spending.

Preview/export use only the standard library. Fetch/import dependencies are in
requirements-location-pricing.txt. See docs/LOCATION_PRICING.md for source units.
"""
from __future__ import annotations

import argparse
import copy
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys
from datetime import date, datetime, timezone
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP, localcontext
from urllib.parse import urlparse
from urllib.request import Request, urlopen

ROMANS = 'I II III IV V VI VII VIII IX X XI XII XIII XIV XV XVI XVII XVIII XIX XX XXI XXII XXIII'.split()
UNITS = {'GROCERY': 'BASKET', 'LIBRARY': 'YEAR', 'GYM': 'ENTRY', 'CAFE': 'DRINK', 'STARBUCKS': 'DRINK'}
HOUSING_URL = 'https://negyzetmeterarak.hu/statisztika?tipus=lakas&ugylet=kiado'
SOURCE_URLS = {
    'LIBRARY': 'https://fszek.hu/Entities/87/Events/konyvtarhasznalat-2026',
    'GYM': 'https://life1.hu/wp-content/uploads/2026/04/L1-Corvin-kezi-arlista-HU.pdf',
    'CAFE': 'https://madalcafe.hu/kmenu/',
    'STARBUCKS': 'https://www.foodora.hu/restaurant/o4sx/starbucks-nyugati',
}
GROCERY_PRODUCTS = [
    ('milk', 'Tesco UHT félzsíros tej 2,8% 1 l', 'LITRE', '210621123'),
    ('bread', 'Szeletelt félbarna kenyér 500 g', 'PACK_500G', '207803495'),
    ('eggs', 'Tesco "A" osztályú közepes méretű mélyalmos, friss tojás M 10 db', 'PACK_10', '220181340'),
    ('rice', 'Tesco előgőzölt, hosszú szemű rizs 1 kg', 'KG', '100501436'),
    ('apples', 'Jonagold alma lédig', 'KG', '2004006222303'),
]
DESCRIPTIONS = {
    'GROCERY': 'Fixed basket: 1 L milk, 500 g half-brown bread, 10 medium eggs, 1 kg rice, 1 kg Jonagold apples',
    'LIBRARY': 'FSZEK category I/II branch: adult annual borrowing membership, excluding card administration fee',
    'GYM': 'Life1 Corvin regular single-entry ticket, excluding first-visit promotion and magnetic card',
    'CAFE': 'Madal cappuccino, 180 ml',
    'STARBUCKS': 'Starbucks Nyugati Caffè Latte starting price on foodora; size unspecified; delivery/service fees excluded',
}
METHOD = 'District mean apartment asking rent / unweighted mean of all 23 district means; benchmark × coff'


class PricingError(ValueError):
    pass


def normalize(text: str) -> str:
    return re.sub(r'\s+', ' ', text).strip()


def hungarian_number(text: str) -> Decimal:
    """Hungarian dots are thousands separators; comma is the decimal separator."""
    value = normalize(text)
    if not re.fullmatch(r'\d+(?:[. ]\d{3})*(?:,\d+)?', value):
        raise PricingError(f'Invalid Hungarian number: {text!r}')
    return Decimal(value.replace('.', '').replace(' ', '').replace(',', '.'))


def soup_for(html: str):
    try:
        from bs4 import BeautifulSoup
    except ImportError as exc:
        raise PricingError('Install scripts/requirements-location-pricing.txt to fetch sources') from exc
    soup = BeautifulSoup(html, 'html.parser')
    for node in soup(['script', 'style', 'noscript']):
        node.decompose()
    return soup


def parse_housing(html: str) -> dict:
    soup = soup_for(html)
    text = normalize(soup.get_text(' '))
    if not re.search(r'Kiadó\s*-\s*Lakás', text):
        raise PricingError('Housing source is not the apartment rental table')
    observed = re.search(r'Utoljára\s*frissítve:\s*(\d{4})\.(\d{2})\.(\d{2})\.', text)
    if not observed:
        raise PricingError('Housing observation date missing')
    observed_at = date(*map(int, observed.groups())).isoformat()
    candidates = [t for t in soup.find_all('table') if re.search(r'Budapest\s+I\.\s+kerület', t.get_text(' '))]
    if len(candidates) != 1:
        raise PricingError('Expected exactly one Budapest district table')
    table = candidates[0]
    header_rows = table.select('thead tr')
    if not header_rows:
        raise PricingError('Housing column headers missing')
    headers = [normalize(h.get_text(' ')).replace(' ↓', '') for h in header_rows[-1].find_all('th')]
    try:
        mean_index, count_index = headers.index('Átlag'), headers.index('Ingatlan')
    except ValueError as exc:
        raise PricingError('Housing mean/listing-count columns changed') from exc
    rows = []
    for row in table.select('tbody tr'):
        cells = row.find_all('td', recursive=False)
        if len(cells) != len(headers):
            raise PricingError('Housing district row has unexpected columns')
        label = normalize(cells[0].get_text(' '))
        district = re.fullmatch(r'Budapest ([IVX]+)\. kerület', label)
        if not district or district[1] not in ROMANS:
            raise PricingError(f'Unexpected district: {label}')
        number = ROMANS.index(district[1]) + 1
        def cell_value(index):
            # Exclude change percentages, which are a separate <small> element.
            cell = copy.copy(cells[index])
            for small in cell.find_all('small'):
                small.decompose()
            return hungarian_number(cell.get_text(' ', strip=True))
        count = cell_value(count_index)
        if count != count.to_integral_value():
            raise PricingError('Listing count must be an integer')
        rows.append({'districtId': f'budapest-{district[1].lower()}', 'districtNumber': number,
                     'displayName': f'Budapest District {district[1]}', 'rentPerM2': str(cell_value(mean_index)),
                     'listingCount': int(count), 'observedAt': observed_at, 'sourceUrl': HOUSING_URL})
    return {'observedAt': observed_at, 'unit': 'HUF_PER_M2_MONTH', 'sourceUrl': HOUSING_URL,
            'districts': sorted(rows, key=lambda d: d['districtNumber'])}


def parse_library(html: str) -> Decimal:
    text = normalize(soup_for(html).get_text(' '))
    match = re.search(r'Könyvtár I\. és Könyvtár II\. besorolású tagkönyvtárakba\s+([\d. ]+)\s*Ft\s*/\s*12 hónap', text)
    if not match:
        raise PricingError('FSZEK adult category I/II annual membership missing')
    return hungarian_number(match[1])


def parse_gym(pdf: bytes) -> Decimal:
    try:
        from pypdf import PdfReader
    except ImportError as exc:
        raise PricingError('Install scripts/requirements-location-pricing.txt for PDF extraction') from exc
    reader = PdfReader(io.BytesIO(pdf))
    return parse_gym_text(reader.pages[0].extract_text())


def parse_gym_text(text: str) -> Decimal:
    match = re.search(r'^\s*Belépőjegy\s*\(1 alkalom\)\s*\.+\s*([\d. ]+)\s*Ft\s*$', text, re.MULTILINE)
    if not match:
        raise PricingError('Life1 regular single-entry ticket missing')
    return hungarian_number(match[1])


def parse_cafe(html: str) -> Decimal:
    text = normalize(soup_for(html).get_text(' '))
    match = re.search(r'CAPPUCCINO\s*/[^/]*\b180\s*ml\s*/\s*([\d. ]+),-', text)
    if not match:
        raise PricingError('Madal 180 ml cappuccino price missing')
    return hungarian_number(match[1])


def parse_starbucks(html: str) -> Decimal:
    soup = soup_for(html)
    if 'Starbucks | Nyugati' not in normalize(soup.get_text(' ')):
        raise PricingError('Expected Starbucks Nyugati menu')
    headings = [h for h in soup.find_all(['h2', 'h3']) if normalize(h.get_text(' ')) == 'Caffè Latte']
    if len(headings) != 1:
        raise PricingError('Starbucks standalone Caffè Latte product missing or duplicated')
    # Read only this product, stopping at the next heading to exclude combos/other drinks.
    texts = []
    for node in headings[0].next_elements:
        if getattr(node, 'name', None) in ('h2', 'h3'):
            break
        if isinstance(node, str):
            texts.append(node)
    match = re.search(r'([\d. ]+)\s*Ft-tól', normalize(' '.join(texts)))
    if not match:
        raise PricingError('Starbucks starting-price field missing')
    return hungarian_number(match[1])


def parse_tesco(html: str, expected_name: str, unit: str) -> Decimal:
    soup = soup_for(html)
    heading = soup.find('h1')
    if not heading or normalize(heading.get_text(' ')) != expected_name:
        raise PricingError(f'Tesco product name changed: expected {expected_name}')
    texts = []
    for node in heading.next_elements:
        if getattr(node, 'name', None) in ('h1', 'h2'):
            break
        if isinstance(node, str):
            texts.append(node)
    text = normalize(' '.join(texts))
    if re.search(r'Clubcard|akció|ajánlat|korábbi ár|Szuper ár|nem elérhető', text, re.IGNORECASE):
        raise PricingError(f'Tesco {expected_name}: regular available price cannot be verified')
    # Loose apples must use the per-kg price, not the illustrative one-apple price.
    pattern = r'([\d. ]+)\s*Ft/kg' if expected_name == 'Jonagold alma lédig' else r'([\d. ]+)\s*Ft(?!\s*/[a-z])'
    match = re.search(pattern, text)
    if not match:
        raise PricingError(f'Tesco {expected_name}: required price/unit missing ({unit})')
    return hungarian_number(match[1])


def download(url: str) -> bytes:
    request = Request(url, headers={'User-Agent': 'RocketCreditDiploma/1.0 (public price references)',
                                   'Accept': 'text/html,application/pdf'})
    try:
        with urlopen(request, timeout=30) as response:
            body = response.read(5_000_001)
        if len(body) > 5_000_000:
            raise PricingError('Source exceeds 5 MB limit')
        return body
    except Exception as exc:
        raise PricingError(f'Cannot fetch {url}: {type(exc).__name__}; use an explicitly saved snapshot') from exc


def fetch_snapshot(grocery_input: Path | None = None) -> dict:
    now = datetime.now(timezone.utc).isoformat()
    housing = parse_housing(download(HOUSING_URL).decode('utf-8'))
    housing['retrievedAt'] = now
    baselines = []
    parsers = {'LIBRARY': parse_library, 'GYM': parse_gym, 'CAFE': parse_cafe, 'STARBUCKS': parse_starbucks}
    for category, parser in parsers.items():
        raw = download(SOURCE_URLS[category])
        price = parser(raw if category == 'GYM' else raw.decode('utf-8'))
        baselines.append({'category': category, 'description': DESCRIPTIONS[category], 'unit': UNITS[category],
                          'price': str(price), 'currency': 'HUF', 'sourceUrl': SOURCE_URLS[category],
                          'observedAt': '2026-01-01' if category == 'LIBRARY' else None, 'retrievedAt': now,
                          'evidence': {'channel': 'delivery_menu_starting_price' if category == 'STARBUCKS' else 'published_tariff',
                                       'size': 'unspecified' if category == 'STARBUCKS' else None}})
    if grocery_input:
        saved = load_snapshot(grocery_input)
        validate_snapshot(saved)
        grocery = copy.deepcopy(next(b for b in saved['baselines'] if b['category'] == 'GROCERY'))
        print(f'Using explicit saved grocery benchmark retrieved {grocery["retrievedAt"]}; not refreshed', file=sys.stderr)
    else:
        components = []
        for key, name, unit, product_id in GROCERY_PRODUCTS:
            url = f'https://bevasarlas.tesco.hu/shop/hu-HU/products/{product_id}'
            price = parse_tesco(download(url).decode('utf-8'), name, unit)
            components.append({'id': key, 'description': name, 'unit': unit, 'quantity': '1',
                               'price': str(price), 'sourceUrl': url, 'retrievedAt': now})
        grocery = {'category': 'GROCERY', 'description': DESCRIPTIONS['GROCERY'], 'unit': 'BASKET',
                   'price': str(sum(Decimal(c['price']) for c in components)), 'currency': 'HUF',
                   'sourceUrl': components[0]['sourceUrl'], 'observedAt': None, 'retrievedAt': now,
                   'evidence': {'channel': 'retailer_online_regular_price', 'components': components}}
    snapshot = {'schemaVersion': 1, 'city': 'Budapest', 'countryCode': 'HU', 'currency': 'HUF',
                'method': METHOD, 'housing': housing, 'baselines': [grocery, *baselines]}
    validate_snapshot(snapshot)
    return snapshot


def positive(value, name: str) -> Decimal:
    try:
        if not isinstance(value, str):
            raise PricingError(f'{name} must be a decimal string')
        parsed = Decimal(value)
        if not parsed.is_finite() or parsed <= 0 or parsed >= Decimal('10000000000'):
            raise PricingError(f'{name} must be positive and below 10 billion')
        if parsed.as_tuple().exponent < -2:
            raise PricingError(f'{name} must have at most two decimal places')
        return parsed
    except InvalidOperation as exc:
        raise PricingError(f'Invalid {name}') from exc


def source_url(value):
    if not isinstance(value, str) or urlparse(value).scheme != 'https' or not urlparse(value).netloc:
        raise PricingError('Source URL must be an identifiable HTTPS URL')


def timestamp(value):
    if not isinstance(value, str):
        raise PricingError('retrievedAt timestamp missing')
    parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
    if parsed.tzinfo is None:
        raise PricingError('retrievedAt must include a timezone')


def validate_snapshot(snapshot: dict) -> None:
    try:
        if snapshot['schemaVersion'] != 1 or (snapshot['city'], snapshot['countryCode'], snapshot['currency']) != ('Budapest', 'HU', 'HUF'):
            raise PricingError('Expected schema 1 Budapest/HU/HUF snapshot')
        if snapshot['method'] != METHOD:
            raise PricingError('Unsupported coefficient method')
        housing = snapshot['housing']
        observed_at = date.fromisoformat(housing['observedAt'])
        timestamp(housing['retrievedAt'])
        if observed_at > datetime.fromisoformat(housing['retrievedAt'].replace('Z', '+00:00')).date():
            raise PricingError('Housing observation date is after retrieval')
        if housing['unit'] != 'HUF_PER_M2_MONTH':
            raise PricingError('Housing unit must be monthly HUF per square metre')
        source_url(housing['sourceUrl'])
        districts = housing['districts']
        numbers = [d['districtNumber'] for d in districts]
        if len(districts) != 23 or any(type(n) is not int for n in numbers) or set(numbers) != set(range(1, 24)):
            raise PricingError('Exactly one row for each of the 23 districts is required')
        for district in districts:
            if district['districtId'] != f'budapest-{ROMANS[district["districtNumber"] - 1].lower()}':
                raise PricingError('District identifier does not match its number')
            if not isinstance(district['displayName'], str) or not district['displayName'].strip():
                raise PricingError('District display name missing')
            if district['observedAt'] != housing['observedAt']:
                raise PricingError('All district observations must use the same date')
            if type(district['listingCount']) is not int or district['listingCount'] <= 0:
                raise PricingError('District listing count must be a positive integer')
            positive(district['rentPerM2'], 'rentPerM2')
            source_url(district['sourceUrl'])
        baselines = snapshot['baselines']
        if len(baselines) != 5 or {b['category'] for b in baselines} != set(UNITS):
            raise PricingError('Exactly the five supported venue baselines are required')
        for baseline in baselines:
            if baseline['currency'] != 'HUF' or baseline['unit'] != UNITS[baseline['category']]:
                raise PricingError('Benchmark currency/unit mismatch')
            price = positive(baseline['price'], 'baseline price')
            source_url(baseline['sourceUrl'])
            timestamp(baseline['retrievedAt'])
            if not isinstance(baseline['description'], str) or not baseline['description'].strip():
                raise PricingError('Benchmark description missing')
            if baseline['observedAt'] is not None:
                if date.fromisoformat(baseline['observedAt']) > datetime.fromisoformat(baseline['retrievedAt'].replace('Z', '+00:00')).date():
                    raise PricingError('Benchmark observation date is after retrieval')
            if not isinstance(baseline['evidence'], dict) or not baseline['evidence'].get('channel'):
                raise PricingError('Benchmark source evidence missing')
            if baseline['category'] == 'GROCERY':
                components = baseline['evidence']['components']
                if len(components) != 5 or {c['id'] for c in components} != {p[0] for p in GROCERY_PRODUCTS}:
                    raise PricingError('Grocery basket must contain the five fixed components')
                total = Decimal(0)
                for component in components:
                    expected = next(p for p in GROCERY_PRODUCTS if p[0] == component['id'])
                    if component['description'] != expected[1] or component['unit'] != expected[2] or component['quantity'] != '1':
                        raise PricingError('Grocery product, quantity or unit changed')
                    source_url(component['sourceUrl'])
                    timestamp(component['retrievedAt'])
                    total += positive(component['price'], 'grocery component price')
                if total != price:
                    raise PricingError('Grocery baseline must equal its component sum')
    except (KeyError, TypeError, IndexError, ValueError) as exc:
        if isinstance(exc, PricingError):
            raise
        raise PricingError(f'Malformed pricing snapshot: {exc}') from exc


def calculate(snapshot: dict) -> dict:
    validate_snapshot(snapshot)
    content = copy.deepcopy(snapshot)
    content['housing']['districts'].sort(key=lambda d: d['districtNumber'])
    content['baselines'].sort(key=lambda b: b['category'])
    next(b for b in content['baselines'] if b['category'] == 'GROCERY')['evidence']['components'].sort(key=lambda c: c['id'])
    # Retrieval timestamps are evidence metadata, not price changes. Equivalent refetches reuse a version.
    def strip_retrieval(value):
        if isinstance(value, dict):
            return {k: strip_retrieval(v) for k, v in value.items() if k != 'retrievedAt'}
        if isinstance(value, list):
            return [strip_retrieval(v) for v in value]
        return value
    checksum = hashlib.sha256(json.dumps(strip_retrieval(content), sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()
    result = {'datasetVersion': f'budapest-rent-{snapshot["housing"]["observedAt"]}-{checksum[:12]}',
              'checksum': checksum, 'districts': [], 'prices': []}
    with localcontext() as context:
        context.prec = 40
        mean = sum(Decimal(d['rentPerM2']) for d in content['housing']['districts']) / Decimal(23)
        result['districtAverage'] = mean.quantize(Decimal('.000001'), rounding=ROUND_HALF_UP)
        for district in content['housing']['districts']:
            coff = Decimal(district['rentPerM2']) / mean
            result['districts'].append({**district, 'coff': coff.quantize(Decimal('.000001'), rounding=ROUND_HALF_UP)})
            for baseline in content['baselines']:
                estimated = (Decimal(baseline['price']) * coff).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
                if estimated <= 0 or estimated >= Decimal('10000000000'):
                    raise PricingError('Derived price is outside supported monetary range')
                result['prices'].append({'districtId': district['districtId'], 'category': baseline['category'],
                                         'estimatedPrice': estimated, 'baselinePrice': baseline['price'],
                                         'currency': 'HUF', 'unit': baseline['unit']})
    return result


def load_snapshot(path: Path) -> dict:
    return json.loads(path.read_text(encoding='utf-8'))


def import_snapshot(snapshot: dict, connection) -> str:
    """Own transaction: the active version changes only after complete successful inserts."""
    result = calculate(snapshot)
    version = result['datasetVersion']
    with connection.transaction():
        with connection.cursor() as cur:
            # Serialize publication/import across concurrent invocations, including first-ever import.
            cur.execute("SELECT pg_advisory_xact_lock(hashtext('rocket-location-pricing'))")
            cur.execute('SELECT content_checksum FROM location_price_datasets WHERE dataset_version=%s', (version,))
            existing = cur.fetchone()
            if existing and existing[0] != result['checksum']:
                raise PricingError('Dataset version checksum collision')
            if not existing:
                housing = snapshot['housing']
                cur.execute('''INSERT INTO location_price_datasets
                    (dataset_version, housing_observed_at, housing_retrieved_at, district_average, content_checksum, source_url)
                    VALUES (%s,%s,%s,%s,%s,%s)''',
                    (version, housing['observedAt'], housing['retrievedAt'], result['districtAverage'], result['checksum'], housing['sourceUrl']))
                cur.executemany('''INSERT INTO location_district_prices
                    (dataset_version,district_id,district_number,display_name,rent_per_m2,listing_count,coff,source_url)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s)''',
                    [(version,d['districtId'],d['districtNumber'],d['displayName'],Decimal(d['rentPerM2']),d['listingCount'],d['coff'],d['sourceUrl']) for d in result['districts']])
                cur.executemany('''INSERT INTO location_venue_baselines
                    (dataset_version,category,benchmark_description,unit,baseline_price,currency,source_url,observed_at,retrieved_at,evidence)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s::jsonb)''',
                    [(version,b['category'],b['description'],b['unit'],Decimal(b['price']),'HUF',b['sourceUrl'],b['observedAt'],b['retrievedAt'],json.dumps(b['evidence'],ensure_ascii=False)) for b in snapshot['baselines']])
                cur.executemany('''INSERT INTO location_venue_prices (dataset_version,district_id,category,estimated_price)
                    VALUES (%s,%s,%s,%s)''', [(version,p['districtId'],p['category'],p['estimatedPrice']) for p in result['prices']])
            for table, expected in [('location_district_prices',23), ('location_venue_baselines',5), ('location_venue_prices',115)]:
                cur.execute(f'SELECT count(*) FROM {table} WHERE dataset_version=%s', (version,))
                if cur.fetchone()[0] != expected:
                    raise PricingError(f'Incomplete dataset: {table} must contain {expected} rows')
            cur.execute('UPDATE location_price_datasets SET active=FALSE WHERE active AND dataset_version<>%s', (version,))
            cur.execute('UPDATE location_price_datasets SET active=TRUE WHERE dataset_version=%s', (version,))
    return version


def export_snapshot(snapshot: dict, directory: Path) -> None:
    result = calculate(snapshot)
    directory.mkdir(parents=True, exist_ok=True)
    with (directory/'district-comparison.csv').open('w', newline='', encoding='utf-8') as handle:
        fields = ['districtId','districtNumber','displayName','rentPerM2','listingCount','coff','observedAt','sourceUrl']
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(result['districts'])
    with (directory/'venue-prices.csv').open('w', newline='', encoding='utf-8') as handle:
        writer = csv.DictWriter(handle, fieldnames=['districtId','category','estimatedPrice','baselinePrice','currency','unit'])
        writer.writeheader()
        writer.writerows(result['prices'])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    fetch = sub.add_parser('fetch', help='Fetch published prices; blocked sources fail without overwriting the output')
    fetch.add_argument('--output', type=Path, required=True)
    fetch.add_argument('--grocery-input', type=Path, help='Explicitly reuse a saved grocery benchmark; its retrieval date is preserved')
    for name in ('preview','import','export'):
        command = sub.add_parser(name)
        command.add_argument('--input', type=Path, required=True)
        if name == 'export':
            command.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == 'fetch':
            snapshot = fetch_snapshot(args.grocery_input)
            args.output.parent.mkdir(parents=True, exist_ok=True)
            temporary = args.output.with_suffix(args.output.suffix+'.tmp')
            temporary.write_text(json.dumps(snapshot,ensure_ascii=False,indent=2)+'\n', encoding='utf-8')
            temporary.replace(args.output)
            print(f'Saved verified snapshot: {args.output}')
            return 0
        snapshot = load_snapshot(args.input)
        result = calculate(snapshot)
        if args.command == 'preview':
            print(f'{result["datasetVersion"]}\nUnweighted district mean: {result["districtAverage"]} HUF/m²/month')
            print('District  Rent/m²     coff      Grocery     Library         Gym        Café   Starbucks (HUF)')
            for district in result['districts']:
                prices = {p['category']: p['estimatedPrice'] for p in result['prices'] if p['districtId']==district['districtId']}
                values = ' '.join(f'{prices[c]:11.2f}' for c in UNITS)
                print(f'{ROMANS[district["districtNumber"]-1]:>8} {district["rentPerM2"]:>8} {district["coff"]:>9} {values}')
            print('Units: grocery/basket; library/year; gym/entry; café and Starbucks/drink. Estimates are not spending totals.')
        elif args.command == 'export':
            export_snapshot(snapshot, args.output)
            print(f'Exported 23 districts and 115 prices to {args.output}')
        else:
            dsn = os.environ.get('LOCATION_PRICING_DATABASE_URL')
            if not dsn:
                raise PricingError('Set LOCATION_PRICING_DATABASE_URL to a PostgreSQL connection URL')
            try:
                import psycopg
            except ImportError as exc:
                raise PricingError('Install scripts/requirements-location-pricing.txt to import into PostgreSQL') from exc
            try:
                with psycopg.connect(dsn, autocommit=True, connect_timeout=10) as connection:
                    version = import_snapshot(snapshot, connection)
            except PricingError:
                raise
            except Exception as exc:
                # Never echo connection strings or credentials from driver error messages.
                raise PricingError(f'PostgreSQL import failed ({type(exc).__name__}); previous dataset preserved') from exc
            print(f'Active dataset: {version}; 23 districts, 5 baselines, 115 estimated prices')
        return 0
    except (PricingError, OSError, ValueError) as exc:
        print(f'Error: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
