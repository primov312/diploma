import copy
from decimal import Decimal, ROUND_HALF_UP
import os
from pathlib import Path
import sys
import unittest
import uuid

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import import_budapest_location_prices as location
import import_monthly_living_costs as monthly

ROOT=Path(__file__).resolve().parents[2]
SNAPSHOT=ROOT/'scripts/data/budapest-monthly-living-costs-2026-10-03.json'


class MonthlyTests(unittest.TestCase):
    def setUp(self): self.snapshot=monthly.load(SNAPSHOT)

    def test_default_rows_exact_normalization_and_postal_coverage(self):
        result=monthly.calculate(self.snapshot)
        self.assertEqual(len(result['rows']),23)
        self.assertEqual(len(self.snapshot['postal']['mappings']),161)
        self.assertEqual(len(self.snapshot['groceries']),12)
        district_v=next(r for r in result['rows'] if r['districtId']=='budapest-v')
        self.assertEqual(district_v['monthlyRent'],Decimal('350000.00'))
        base=sum(Decimal(g['packagePrice'])/Decimal(g['packageQuantity'])*Decimal(monthly.DEFAULTS['groceryQuantities'][g['id']]) for g in self.snapshot['groceries'])
        self.assertEqual(result['groceryBaseline'].quantize(Decimal('.01')),base.quantize(Decimal('.01')))
        mean=sum(Decimal(d['rentPerM2']) for d in self.snapshot['housing']['districts'])/23
        self.assertEqual(district_v['monthlyGroceries'],(base*7000/mean).quantize(Decimal('.01'),rounding=ROUND_HALF_UP))
        mappings={m['postalCode']:m['districtId'] for m in self.snapshot['postal']['mappings']}
        self.assertEqual([mappings[k] for k in ['1021','1051','1239']],['budapest-ii','budapest-v','budapest-xxiii'])
        self.assertNotIn('1007',mappings)

    def test_unknown_codes_duplicates_missing_products_and_package_tampering_fail(self):
        variants=[]
        duplicate=copy.deepcopy(self.snapshot);duplicate['postal']['mappings'].append(duplicate['postal']['mappings'][0]);variants.append(duplicate)
        bad=copy.deepcopy(self.snapshot);bad['postal']['mappings'][0]['postalCode']='1007';variants.append(bad)
        missing=copy.deepcopy(self.snapshot);missing['groceries'].pop();variants.append(missing)
        package=copy.deepcopy(self.snapshot);package['groceries'][6]['packageQuantity']='1';variants.append(package)
        for variant in variants:
            with self.assertRaises(location.PricingError): monthly.calculate(variant)

    def test_loose_products_use_kg_and_package_parser_rejects_promotions_or_changed_units(self):
        for p in monthly.PRODUCTS:
            size= ' '.join([p[2], '10 x Tojás'])
            html=f'<h1>{p[2]}</h1><p>123 Ft</p><p>822 Ft/kg</p><h2>Product</h2><p>{size}</p>'
            expected=Decimal(822 if p[7] else 123)
            self.assertEqual(monthly.parse_product(html,p),expected)
            for message in ['Clubcard ár','Ez a termék jelenleg nem elérhető']:
                with self.assertRaises(location.PricingError): monthly.parse_product(html.replace('123 Ft',message+' 123 Ft'),p)
        potato=monthly.PRODUCTS[6]
        with self.assertRaises(location.PricingError): monthly.parse_product('<h1>Changed potatoes</h1><p>716 Ft</p>',potato)

    def test_xml_parser_rejects_changed_shape_and_special_codes(self):
        xml='<zipCodes>'+''.join(f'<zipCode><Code>1{n:02d}1</Code><city>Budapest</city></zipCode>' for n in range(1,24))+'<zipCode><Code>1007</Code><city>Budapest</city></zipCode></zipCodes>'
        rows=monthly.parse_postcodes(xml.encode())
        self.assertEqual(len(rows),23)
        with self.assertRaises(location.PricingError): monthly.parse_postcodes(xml.replace('zipCodes','changed').encode())

    def test_versions_preserve_identical_retrievals_but_change_with_prices(self):
        before=monthly.calculate(self.snapshot)['datasetVersion']
        self.snapshot['groceries'].reverse();self.snapshot['postal']['mappings'].reverse()
        self.snapshot['postal']['retrievedAt']='2026-10-04T12:00:00Z'
        for g in self.snapshot['groceries']:g['retrievedAt']='2026-10-04T12:00:00Z'
        self.assertEqual(before,monthly.calculate(self.snapshot)['datasetVersion'])
        g=self.snapshot['groceries'][0];g['packagePrice']='800';g['unitPrice']=str((Decimal(800)/Decimal(g['packageQuantity'])).quantize(Decimal('.000001'),rounding=ROUND_HALF_UP))
        self.assertNotEqual(before,monthly.calculate(self.snapshot)['datasetVersion'])


@unittest.skipUnless(os.environ.get('LOCATION_PRICING_TEST_DATABASE_URL'),'Set LOCATION_PRICING_TEST_DATABASE_URL')
class MonthlyPostgreSQLTests(unittest.TestCase):
    def setUp(self):
        import psycopg
        from psycopg import sql
        self.connection=psycopg.connect(os.environ['LOCATION_PRICING_TEST_DATABASE_URL'],autocommit=True)
        self.schema='monthly_test_'+uuid.uuid4().hex
        self.connection.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(self.schema)))
        self.connection.execute(sql.SQL('SET search_path TO {}').format(sql.Identifier(self.schema)))
        self.connection.execute('CREATE TABLE users(id BIGINT PRIMARY KEY); CREATE TABLE demo_districts(district_id VARCHAR(40) PRIMARY KEY,display_name TEXT,city TEXT,country_code TEXT,sort_order INT UNIQUE); CREATE TABLE affordability_jobs(id BIGINT); CREATE TABLE affordability_snapshots(id BIGINT)')
        migrations=ROOT/'rocket-credit-backend/src/main/resources/db/migration'
        self.connection.execute((migrations/'V10__budapest_location_pricing.sql').read_text())
        self.connection.execute((migrations/'V11__monthly_living_costs.sql').read_text())
        location.import_snapshot(location.load_snapshot(ROOT/'scripts/data/budapest-location-prices-2026-09-30.json'),self.connection)
        self.snapshot=monthly.load(SNAPSHOT)

    def tearDown(self):
        from psycopg import sql
        self.connection.execute(sql.SQL('DROP SCHEMA {} CASCADE').format(sql.Identifier(self.schema)))
        self.connection.close()

    def test_idempotence_and_failed_publication_keep_previous_active(self):
        import psycopg
        version=monthly.import_snapshot(self.snapshot,self.connection)
        self.assertEqual(version,monthly.import_snapshot(self.snapshot,self.connection))
        self.assertEqual(self.connection.execute('SELECT count(*) FROM monthly_living_costs').fetchone()[0],23)
        self.assertEqual(self.connection.execute('SELECT count(*) FROM postal_code_districts').fetchone()[0],161)
        self.snapshot['groceries'][0]['packagePrice']='320';self.snapshot['groceries'][0]['unitPrice']='320.000000'
        failed=monthly.calculate(self.snapshot)['datasetVersion']
        self.connection.execute("CREATE FUNCTION fail_monthly() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced test failure'; END $$")
        self.connection.execute('CREATE TRIGGER fail_monthly BEFORE INSERT ON monthly_living_costs FOR EACH ROW EXECUTE FUNCTION fail_monthly()')
        with self.assertRaises(psycopg.Error):monthly.import_snapshot(self.snapshot,self.connection)
        self.assertEqual(self.connection.execute('SELECT dataset_version FROM monthly_living_cost_datasets WHERE active').fetchall(),[(version,)])
        self.assertEqual(self.connection.execute('SELECT count(*) FROM monthly_living_cost_datasets WHERE dataset_version=%s',(failed,)).fetchone()[0],0)

    def test_housing_version_values_must_match_and_incomplete_activation_fails(self):
        import psycopg
        self.snapshot['housing']['districts'][0]['rentPerM2']='1000'
        with self.assertRaises(location.PricingError):monthly.import_snapshot(self.snapshot,self.connection)
        with self.assertRaises(psycopg.Error):
            self.connection.execute('INSERT INTO monthly_living_cost_datasets(dataset_version,housing_dataset_version,content_checksum,snapshot,active) VALUES(%s,%s,%s,%s::jsonb,TRUE)',
                                    ('incomplete',self.snapshot['housingDatasetVersion'],'a'*64,'{"groceries":[]}'))
