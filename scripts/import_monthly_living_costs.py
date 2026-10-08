#!/usr/bin/env python3
"""Versioned Budapest postal codes and monthly rent/food benchmarks (HUF)."""
from __future__ import annotations

import argparse
import copy
import csv
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP, localcontext
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

import import_budapest_location_prices as location

POSTAL_URL = 'https://httpmegosztas.posta.hu/PartnerExtra/OUT/ZipCodes.xml'
# id, UI label, exact published product, normalized unit, package quantity, monthly quantity, product ID, loose
PRODUCTS = [
    ('milk','Milk','Tesco UHT félzsíros tej 2,8% 1 l','LITRE','1','8','210621123',False),
    ('bread','Bread','Szeletelt félbarna kenyér 500 g','KG','.5','6','207803495',False),
    ('eggs','Eggs','Tesco "A" osztályú közepes méretű mélyalmos, friss tojás M 10 db','ITEM','10','30','220181340',False),
    ('rice','Rice','Tesco előgőzölt, hosszú szemű rizs 1 kg','KG','1','2','100501436',False),
    ('apples','Apples','Jonagold alma lédig','KG','1','4','2004006222303',True),
    ('chicken','Chicken breast','Friss csirkemell filé','KG','1','4','203176258',True),
    ('potatoes','Potatoes','Tesco burgonya főznivaló 2 kg','KG','2','4','2004020330168',False),
    ('pasta','Pasta','Tesco spagetti 4 tojásos száraztészta 500 g','KG','.5','2','2005100501330',False),
    ('tomatoes','Tomatoes','Fürtös paradicsom','KG','1','2','203274480',True),
    ('onions','Onions','Vöröshagyma lédig','KG','1','1','206481922',True),
    ('cheese','Cheese','Tesco zsíros, félkemény trappista sajt 700 g','KG','.7','1','2004010616075',False),
    ('oil','Sunflower oil','Golden Imperial 100% finomított napraforgó étolaj 1 l','LITRE','1','1','111272111',False),
]
DEFAULTS = {'apartmentSize':'50','rentSharers':1,'otherSpending':'0',
            'groceryQuantities':{p[0]:p[5] for p in PRODUCTS}}
CENT = Decimal('.01')


def parse_postcodes(raw: bytes) -> list[dict]:
    if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():
        raise location.PricingError('Unexpected XML declarations')
    root = ET.fromstring(raw)
    if root.tag != 'zipCodes':
        raise location.PricingError('Postcode XML structure changed')
    rows = []
    for el in root:
        if el.findtext('city') != 'Budapest':
            continue
        code = el.findtext('Code', '')
        if not re.fullmatch(r'1(?:0[1-9]|1[0-9]|2[0-3])[1-9]', code):
            continue  # institutional/special codes cannot identify a residential district
        number = int(code[1:3])
        rows.append({'postalCode':code,'districtId':'budapest-'+location.ROMANS[number-1].lower()})
    if len({r['postalCode'] for r in rows}) != len(rows) or len({r['districtId'] for r in rows}) != 23:
        raise location.PricingError('Postcodes must be unique and cover all 23 districts')
    return sorted(rows,key=lambda r:r['postalCode'])


def parse_product(html: str, product: tuple) -> Decimal:
    soup = location.soup_for(html)
    heading = soup.find('h1')
    if not heading or location.normalize(heading.get_text(' ')) != product[2]:
        raise location.PricingError(f'Product name changed: {product[0]}')
    texts=[]
    for node in heading.next_elements:
        if getattr(node,'name',None) in ('h1','h2'):
            break
        if isinstance(node,str):
            texts.append(node)
    text=location.normalize(' '.join(texts))
    if re.search(r'Clubcard|akció|ajánlat|korábbi ár|Szuper ár|nem elérhető',text,re.I):
        raise location.PricingError(f'Available regular price not verified: {product[0]}')
    pattern=r'([\d. ]+)\s*Ft/kg' if product[7] else r'([\d. ]+)\s*Ft(?!\s*/[a-z])'
    match=re.search(pattern,text)
    if not match:
        raise location.PricingError(f'Price/unit field changed: {product[0]}')
    if not product[7]:
        # Verify the published package size; never substitute the rounded shelf unit price.
        body=location.normalize(soup.get_text(' '))
        amount=Decimal(product[4])
        if product[3]=='ITEM':
            size_pattern=rf'{int(amount)}\s*(?:db|x\s*Tojás)'
        elif product[3]=='LITRE':
            size_pattern=rf'{int(amount)}\s*l\b'
        else:
            grams=int(amount*1000)
            kg=str(amount).replace('.',r'[.,]')
            size_pattern=rf'(?:{grams}\s*g\b|0?{kg}\s*kg\b)'
        if not re.search(size_pattern,body,re.I):
            raise location.PricingError(f'Package size changed: {product[0]}')
    return location.hungarian_number(match[1])


def fetch_snapshot(housing_input: Path, grocery_input: Path | None = None) -> dict:
    housing_snapshot=location.load_snapshot(housing_input)
    result=location.calculate(housing_snapshot)
    now=datetime.now(timezone.utc).isoformat()
    postal={'sourceUrl':POSTAL_URL,'retrievedAt':now,'mappings':parse_postcodes(location.download(POSTAL_URL))}
    if grocery_input:
        saved=load(grocery_input)
        calculate(saved)
        groceries=copy.deepcopy(saved['groceries'])
        print('Explicit saved grocery prices retained; grocery retrieval dates were not refreshed.',file=sys.stderr)
    else:
        groceries=[]
        for p in PRODUCTS:
            url=f'https://bevasarlas.tesco.hu/shop/hu-HU/products/{p[6]}'
            price=parse_product(location.download(url).decode('utf-8'),p)
            groceries.append(product_record(p,price,now))
    snapshot={'schemaVersion':1,'city':'Budapest','countryCode':'HU','currency':'HUF',
              'housingDatasetVersion':result['datasetVersion'],'housing':housing_snapshot['housing'],
              'postal':postal,'groceries':groceries,'defaults':copy.deepcopy(DEFAULTS)}
    calculate(snapshot)
    return snapshot


def product_record(p: tuple, price: Decimal, retrieved_at: str) -> dict:
    unit_price=price/Decimal(p[4])
    return {'id':p[0],'label':p[1],'description':p[2],'unit':p[3],
            'packageQuantity':p[4],'packagePrice':str(price),'loose':p[7],
            'unitPrice':str(unit_price.quantize(Decimal('.000001'),rounding=ROUND_HALF_UP)),
            'sourceUrl':f'https://bevasarlas.tesco.hu/shop/hu-HU/products/{p[6]}',
            'retrievedAt':retrieved_at,'observedAt':None,'channel':'retailer_online_regular_price'}


def calculate(snapshot: dict) -> dict:
    try:
        if (snapshot['schemaVersion'],snapshot['city'],snapshot['countryCode'],snapshot['currency'])!=(1,'Budapest','HU','HUF'):
            raise location.PricingError('Expected Budapest monthly snapshot schema 1 in HUF')
        if snapshot['defaults']!=DEFAULTS:
            raise location.PricingError('Default profile changed; create a new schema for changed defaults')
        housing=snapshot['housing']
        location.timestamp(housing['retrievedAt'])
        location.source_url(housing['sourceUrl'])
        observed=datetime.fromisoformat(housing['observedAt']).date()
        if observed>datetime.fromisoformat(housing['retrievedAt']).date():
            raise location.PricingError('Housing observation is after retrieval')
        if housing['unit']!='HUF_PER_M2_MONTH' or not re.fullmatch(r'budapest-rent-\d{4}-\d{2}-\d{2}-[a-f0-9]{12}',snapshot['housingDatasetVersion']):
            raise location.PricingError('Invalid housing version/unit')
        districts=housing['districts']
        if len(districts)!=23 or {d['districtNumber'] for d in districts}!=set(range(1,24)):
            raise location.PricingError('Housing must cover all 23 districts')
        for d in districts:
            if type(d['districtNumber']) is not int or d['districtId']!='budapest-'+location.ROMANS[d['districtNumber']-1].lower() or d['observedAt']!=housing['observedAt']:
                raise location.PricingError('District ID/date mismatch')
            location.positive(d['rentPerM2'],'rent/m²')
        postal=snapshot['postal']
        if postal['sourceUrl']!=POSTAL_URL:
            raise location.PricingError('Unsupported postcode source')
        location.timestamp(postal['retrievedAt'])
        mappings=postal['mappings']
        codes=[m['postalCode'] for m in mappings]
        if len(codes)!=len(set(codes)) or {m['districtId'] for m in mappings}!={d['districtId'] for d in districts}:
            raise location.PricingError('Postcode coverage/duplicates invalid')
        for m in mappings:
            code=m['postalCode']
            if not re.fullmatch(r'1(?:0[1-9]|1[0-9]|2[0-3])[1-9]',code) or m['districtId']!='budapest-'+location.ROMANS[int(code[1:3])-1].lower():
                raise location.PricingError('Invalid residential postcode mapping')
        groceries=snapshot['groceries']
        if len(groceries)!=12 or {g['id'] for g in groceries}!={p[0] for p in PRODUCTS}:
            raise location.PricingError('Exactly 12 named grocery products required')
        with localcontext() as ctx:
            ctx.prec=40
            grocery_base=Decimal(0)
            for g in groceries:
                p=next(p for p in PRODUCTS if p[0]==g['id'])
                if any(g[k]!=v for k,v in [('label',p[1]),('description',p[2]),('unit',p[3]),('packageQuantity',p[4]),('loose',p[7]),('channel','retailer_online_regular_price')]):
                    raise location.PricingError('Grocery product/package/unit changed')
                if g['sourceUrl']!=f'https://bevasarlas.tesco.hu/shop/hu-HU/products/{p[6]}' or g['observedAt'] is not None:
                    raise location.PricingError('Grocery source/date mismatch')
                location.timestamp(g['retrievedAt'])
                price=location.positive(g['packagePrice'],'package price')
                unit_price=price/Decimal(g['packageQuantity'])
                if Decimal(g['unitPrice'])!=unit_price.quantize(Decimal('.000001'),rounding=ROUND_HALF_UP):
                    raise location.PricingError('Normalized unit price mismatch')
                grocery_base+=unit_price*Decimal(DEFAULTS['groceryQuantities'][g['id']])
            mean=sum(Decimal(d['rentPerM2']) for d in districts)/23
            rows=[]
            for d in sorted(districts,key=lambda d:d['districtNumber']):
                rent=(Decimal(d['rentPerM2'])*50).quantize(CENT,rounding=ROUND_HALF_UP)
                grocery=(grocery_base*Decimal(d['rentPerM2'])/mean).quantize(CENT,rounding=ROUND_HALF_UP)
                if rent+grocery>=Decimal('10000000000'):
                    raise location.PricingError('Monthly total overflow')
                rows.append({'districtId':d['districtId'],'monthlyRent':rent,'monthlyGroceries':grocery,'monthlyOther':Decimal(0),'monthlyTotal':rent+grocery,'currency':'HUF'})
        canonical=copy.deepcopy(snapshot)
        canonical['groceries'].sort(key=lambda g:g['id'])
        canonical['housing']['districts'].sort(key=lambda d:d['districtNumber'])
        canonical['postal']['mappings'].sort(key=lambda m:m['postalCode'])
        def strip(value):
            if isinstance(value,dict): return {k:strip(v) for k,v in value.items() if k!='retrievedAt'}
            if isinstance(value,list): return [strip(v) for v in value]
            return value
        checksum=hashlib.sha256(json.dumps(strip(canonical),sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
        return {'datasetVersion':f'budapest-monthly-{housing["observedAt"]}-{checksum[:12]}','checksum':checksum,'rows':rows,'groceryBaseline':grocery_base}
    except (KeyError,ValueError,TypeError,IndexError,ArithmeticError) as exc:
        if isinstance(exc,location.PricingError): raise
        raise location.PricingError(f'Malformed monthly snapshot: {exc}') from exc


def import_snapshot(snapshot: dict, connection) -> str:
    result=calculate(snapshot)
    version=result['datasetVersion']
    with connection.transaction(),connection.cursor() as cur:
        cur.execute("SELECT pg_advisory_xact_lock(hashtext('rocket-monthly-living-costs'))")
        # Prevent a saved snapshot from attaching different housing values to a valid version.
        cur.execute('SELECT district_id,rent_per_m2 FROM location_district_prices WHERE dataset_version=%s',(snapshot['housingDatasetVersion'],))
        actual=dict(cur.fetchall())
        expected={d['districtId']:Decimal(d['rentPerM2']) for d in snapshot['housing']['districts']}
        cur.execute('SELECT housing_observed_at FROM location_price_datasets WHERE dataset_version=%s',(snapshot['housingDatasetVersion'],))
        observed=cur.fetchone()
        if actual!=expected or not observed or str(observed[0])!=snapshot['housing']['observedAt']:
            raise location.PricingError('Import the matching housing dataset first')
        cur.execute('SELECT content_checksum FROM monthly_living_cost_datasets WHERE dataset_version=%s',(version,))
        existing=cur.fetchone()
        if existing and existing[0]!=result['checksum']:
            raise location.PricingError('Version checksum collision')
        if not existing:
            cur.execute('INSERT INTO monthly_living_cost_datasets(dataset_version,housing_dataset_version,content_checksum,snapshot) VALUES(%s,%s,%s,%s::jsonb)',
                        (version,snapshot['housingDatasetVersion'],result['checksum'],json.dumps(snapshot,ensure_ascii=False)))
            cur.executemany('INSERT INTO postal_code_districts(dataset_version,postal_code,district_id,source_url,retrieved_at) VALUES(%s,%s,%s,%s,%s)',
                            [(version,m['postalCode'],m['districtId'],POSTAL_URL,snapshot['postal']['retrievedAt']) for m in snapshot['postal']['mappings']])
            cur.executemany('INSERT INTO monthly_living_costs(dataset_version,district_id,monthly_rent,monthly_groceries,monthly_other,monthly_total) VALUES(%s,%s,%s,%s,%s,%s)',
                            [(version,r['districtId'],r['monthlyRent'],r['monthlyGroceries'],r['monthlyOther'],r['monthlyTotal']) for r in result['rows']])
        cur.execute('SELECT count(*) FROM monthly_living_costs WHERE dataset_version=%s',(version,))
        if cur.fetchone()[0]!=23: raise location.PricingError('Incomplete monthly district rows')
        cur.execute('SELECT count(*) FROM postal_code_districts WHERE dataset_version=%s',(version,))
        if cur.fetchone()[0]!=len(snapshot['postal']['mappings']): raise location.PricingError('Incomplete postcode mappings')
        cur.execute('UPDATE monthly_living_cost_datasets SET active=FALSE WHERE active AND dataset_version<>%s',(version,))
        cur.execute('UPDATE monthly_living_cost_datasets SET active=TRUE WHERE dataset_version=%s',(version,))
    return version


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding='utf-8'))


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='command',required=True)
    fetch=sub.add_parser('fetch')
    fetch.add_argument('--housing-input',type=Path,required=True)
    fetch.add_argument('--grocery-input',type=Path)
    fetch.add_argument('--output',type=Path,required=True)
    for name in ('preview','import','export'):
        child=sub.add_parser(name)
        child.add_argument('--input',type=Path,required=True)
        if name=='export': child.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    try:
        if args.command=='fetch':
            snapshot=fetch_snapshot(args.housing_input,args.grocery_input)
            args.output.parent.mkdir(parents=True,exist_ok=True)
            temporary=args.output.with_suffix('.json.tmp')
            temporary.write_text(json.dumps(snapshot,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
            temporary.replace(args.output)
            print(f'Saved {args.output}')
            return 0
        snapshot=load(args.input)
        result=calculate(snapshot)
        if args.command=='preview':
            print(f'{result["datasetVersion"]}\nMonthly city food baseline: {result["groceryBaseline"]:.2f} HUF; demo quantities, per person')
            for row in result['rows']: print(f'{row["districtId"]:16} rent {row["monthlyRent"]:10.2f} groceries {row["monthlyGroceries"]:9.2f} total {row["monthlyTotal"]:10.2f} HUF')
        elif args.command=='export':
            args.output.mkdir(parents=True,exist_ok=True)
            with (args.output/'monthly-living-costs.csv').open('w',newline='',encoding='utf-8') as handle:
                writer=csv.DictWriter(handle,fieldnames=list(result['rows'][0]))
                writer.writeheader();writer.writerows(result['rows'])
            with (args.output/'postal-code-districts.csv').open('w',newline='',encoding='utf-8') as handle:
                writer=csv.DictWriter(handle,fieldnames=['postalCode','districtId'])
                writer.writeheader();writer.writerows(snapshot['postal']['mappings'])
        else:
            import psycopg
            dsn=os.environ.get('LOCATION_PRICING_DATABASE_URL')
            if not dsn: raise location.PricingError('Set LOCATION_PRICING_DATABASE_URL')
            try:
                with psycopg.connect(dsn,autocommit=True) as connection: version=import_snapshot(snapshot,connection)
            except psycopg.Error as exc:
                raise location.PricingError(f'Database import failed ({type(exc).__name__}); previous dataset preserved') from exc
            print(f'Imported and activated {version}: 23 monthly rows')
        return 0
    except (location.PricingError,OSError,ImportError,ET.ParseError) as exc:
        print(str(exc),file=sys.stderr)
        return 1


if __name__=='__main__':
    raise SystemExit(main())
