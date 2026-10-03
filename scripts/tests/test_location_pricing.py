"""Price/source invariants plus optional PostgreSQL transaction tests.

Run: python -m unittest discover -s scripts/tests -v
Set LOCATION_PRICING_TEST_DATABASE_URL for PostgreSQL tests (creates a temporary schema).
"""
import copy
from decimal import Decimal
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import import_budapest_location_prices as pricing

ROOT = Path(__file__).resolve().parents[2]
SNAPSHOT = ROOT / 'scripts/data/budapest-location-prices-2026-09-30.json'


class PricingTests(unittest.TestCase):
    def setUp(self):
        self.snapshot = pricing.load_snapshot(SNAPSHOT)

    def test_complete_dataset_normalization_and_known_examples(self):
        result = pricing.calculate(self.snapshot)
        self.assertEqual(len(result['districts']), 23)
        self.assertEqual(len(result['prices']), 115)
        self.assertEqual(result['districtAverage'], Decimal('5341.304348'))
        mean_coff = sum(d['coff'] for d in result['districts']) / Decimal(23)
        self.assertLess(abs(mean_coff - 1), Decimal('.000001'))
        expected = {2: '1.166382', 5: '1.310541', 23: '.790069'}
        for d in result['districts']:
            if d['districtNumber'] in expected:
                self.assertEqual(d['coff'], Decimal(expected[d['districtNumber']]))
        cafe_v = next(p for p in result['prices'] if p['districtId']=='budapest-v' and p['category']=='CAFE')
        self.assertEqual(cafe_v['estimatedPrice'], Decimal('1952.71'))

    def test_prices_use_unrounded_ratio_and_half_up(self):
        # Select a cents amount where storing six decimals changes final cent rounding.
        for d in self.snapshot['housing']['districts']:
            d['rentPerM2'] = '200.00'
        self.snapshot['housing']['districts'][0]['rentPerM2'] = '100.00'
        cafe = next(b for b in self.snapshot['baselines'] if b['category']=='CAFE')
        ratio = Decimal(100) / (Decimal(4500) / 23)
        rounded = ratio.quantize(Decimal('.000001'))
        candidate = next(Decimal(n)/100 for n in range(1,200000) if
                         (Decimal(n)/100*ratio).quantize(Decimal('.01'), rounding=pricing.ROUND_HALF_UP) !=
                         (Decimal(n)/100*rounded).quantize(Decimal('.01'), rounding=pricing.ROUND_HALF_UP))
        cafe['price'] = str(candidate)
        result = pricing.calculate(self.snapshot)
        actual = next(p['estimatedPrice'] for p in result['prices'] if p['districtId']=='budapest-i' and p['category']=='CAFE')
        self.assertEqual(actual, (candidate*ratio).quantize(Decimal('.01'), rounding=pricing.ROUND_HALF_UP))
        self.assertNotEqual(actual, (candidate*rounded).quantize(Decimal('.01'), rounding=pricing.ROUND_HALF_UP))

    def test_missing_duplicate_district_and_mixed_date_fail(self):
        variants = []
        missing = copy.deepcopy(self.snapshot); missing['housing']['districts'].pop(); variants.append(missing)
        duplicate = copy.deepcopy(self.snapshot); duplicate['housing']['districts'][-1] = duplicate['housing']['districts'][0]; variants.append(duplicate)
        mixed = copy.deepcopy(self.snapshot); mixed['housing']['districts'][0]['observedAt']='2026-08-30'; variants.append(mixed)
        for snapshot in variants:
            with self.subTest(snapshot=snapshot['housing']['districts'][-1]['districtId']):
                with self.assertRaises(pricing.PricingError): pricing.calculate(snapshot)

    def test_invalid_prices_currency_and_units_fail(self):
        for value in ['0', '-1', 'NaN', 'Infinity', '1.001', '10000000000']:
            self.snapshot['housing']['districts'][0]['rentPerM2'] = value
            with self.subTest(value=value), self.assertRaises(pricing.PricingError): pricing.calculate(self.snapshot)
        self.snapshot = pricing.load_snapshot(SNAPSHOT)
        self.snapshot['baselines'][1]['unit'] = 'DRINK'
        with self.assertRaises(pricing.PricingError): pricing.calculate(self.snapshot)
        self.snapshot = pricing.load_snapshot(SNAPSHOT); self.snapshot['currency']='USD'
        with self.assertRaises(pricing.PricingError): pricing.calculate(self.snapshot)

    def test_grocery_evidence_sum_and_fixed_quantities(self):
        grocery = next(b for b in self.snapshot['baselines'] if b['category']=='GROCERY')
        self.assertEqual(grocery['price'], '2720')
        grocery['evidence']['components'][0]['quantity']='2'
        with self.assertRaises(pricing.PricingError): pricing.calculate(self.snapshot)
        grocery['evidence']['components'][0]['quantity']='1'; grocery['price']='1'
        with self.assertRaises(pricing.PricingError): pricing.calculate(self.snapshot)

    def test_refetch_and_row_order_do_not_create_new_version(self):
        original = pricing.calculate(self.snapshot)['datasetVersion']
        self.snapshot['housing']['districts'].reverse()
        self.snapshot['baselines'].reverse()
        self.snapshot['housing']['retrievedAt']='2026-10-04T12:00:00Z'
        for baseline in self.snapshot['baselines']: baseline['retrievedAt']='2026-10-04T12:00:00Z'
        self.assertEqual(original, pricing.calculate(self.snapshot)['datasetVersion'])
        self.snapshot['housing']['districts'][0]['rentPerM2']='5000'
        self.assertNotEqual(original, pricing.calculate(self.snapshot)['datasetVersion'])

    def test_export_contains_all_rows_and_units(self):
        with tempfile.TemporaryDirectory() as directory:
            pricing.export_snapshot(self.snapshot, Path(directory))
            self.assertEqual(len((Path(directory)/'district-comparison.csv').read_text().splitlines()),24)
            lines=(Path(directory)/'venue-prices.csv').read_text().splitlines()
            self.assertEqual(len(lines),116)
            self.assertTrue(any(',LIBRARY,' in line and line.endswith(',HUF,YEAR') for line in lines))

    def test_hungarian_formatting(self):
        for text, expected in [('6.230','6230'),('1\u00a0540','1540'),('1.234,50','1234.50')]:
            self.assertEqual(pricing.hungarian_number(text),Decimal(expected))
        for text in ['6.23','-1','1.234.5','abc']:
            with self.assertRaises(pricing.PricingError): pricing.hungarian_number(text)

    def test_housing_parser_uses_mean_not_median_or_percentage(self):
        rows=''.join(f'<tr><td>Budapest {roman}. kerület</td><td>4.000</td><td>6.230<small>(+1,2%)</small></td><td>1.000</td><td>10.000</td><td>850<small>(-2%)</small></td></tr>' for roman in pricing.ROMANS)
        html=f'<h1>Kiadó - Lakás</h1><p>Utoljára frissítve: 2026.09.30.</p><table><thead><tr><th>Kiadó - Lakás</th></tr><tr><th>Kerület</th><th>Medián</th><th>Átlag</th><th>Min</th><th>Max</th><th>Ingatlan</th></tr></thead><tbody>{rows}</tbody></table>'
        housing=pricing.parse_housing(html)
        self.assertEqual(len(housing['districts']),23)
        self.assertEqual(housing['districts'][0]['rentPerM2'],'6230')
        self.assertEqual(housing['districts'][0]['listingCount'],850)
        with self.assertRaises(pricing.PricingError): pricing.parse_housing(html.replace('Átlag','Mean changed'))
        with self.assertRaises(pricing.PricingError): pricing.parse_housing('<html>Please wait</html>')

    def test_venue_parsers_exclude_other_products_and_promotions(self):
        self.assertEqual(pricing.parse_library('<p>a Központi Könyvtárba 8.400 Ft / 12 hónap a Könyvtár I. és Könyvtár II. besorolású tagkönyvtárakba 5.800 Ft / 12 hónap 4.500 Ft / 6 hónap</p>'),Decimal(5800))
        self.assertEqual(pricing.parse_cafe('<p>CAPPUCCINO /espresso & milk cream, 180ml / 1490,- LATTE /300 ml/ 1690,-</p>'),Decimal(1490))
        self.assertEqual(pricing.parse_gym_text('First napijegy .... 3.999 Ft\nBelépőjegy (1 alkalom) .... 5.999 Ft\nMágneskártya ... 1.499 Ft'),Decimal(5999))
        self.assertEqual(pricing.parse_starbucks('<h1>Starbucks | Nyugati</h1><h3>Latte combo</h3><p>2 730 Ft-tól</p><h3>Caffè Latte</h3><p>1 540 Ft-tól</p><h3>Americano</h3><p>1 440 Ft-tól</p>'),Decimal(1540))
        with self.assertRaises(pricing.PricingError): pricing.parse_gym_text('First napijegy ... 3.999 Ft')
        with self.assertRaises(pricing.PricingError): pricing.parse_cafe('<p>CAPPUCCINO /300 ml/ 1490,-</p>')
        with self.assertRaises(pricing.PricingError): pricing.parse_starbucks('<h1>Starbucks | Nyugati</h1><h3>Caffè Latte combo</h3><p>2 730 Ft-tól</p>')

    def test_grocery_parser_uses_whole_pack_and_apples_per_kg(self):
        self.assertEqual(pricing.parse_tesco('<h1>Jonagold alma lédig</h1><p>144 Ft</p><p>509 Ft/kg</p><h2>Termék megnevezés</h2>','Jonagold alma lédig','KG'),Decimal(509))
        name=pricing.GROCERY_PRODUCTS[2][1]
        self.assertEqual(pricing.parse_tesco(f'<h1>{name}</h1><p>768 Ft</p><p>77 Ft/db</p><h2>Termék megnevezés</h2>',name,'PACK_10'),Decimal(768))
        for prefix in ['Clubcarddal 599 Ft','Ez a termék jelenleg nem elérhető']:
            with self.assertRaises(pricing.PricingError): pricing.parse_tesco(f'<h1>{name}</h1><p>{prefix}</p><p>768 Ft</p><h2>Termék megnevezés</h2>',name,'PACK_10')

    def test_failed_fetch_preserves_saved_output(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory)/'snapshot.json'; target.write_text('existing snapshot')
            with patch.object(sys,'argv',['script','fetch','--output',str(target)]), patch.object(pricing,'download',side_effect=pricing.PricingError('Blocked source')):
                self.assertEqual(pricing.main(),1)
            self.assertEqual(target.read_text(),'existing snapshot')


@unittest.skipUnless(os.environ.get('LOCATION_PRICING_TEST_DATABASE_URL'),'Set LOCATION_PRICING_TEST_DATABASE_URL for PostgreSQL tests')
class PostgreSQLTests(unittest.TestCase):
    def setUp(self):
        import psycopg
        from psycopg import sql
        self.connection=psycopg.connect(os.environ['LOCATION_PRICING_TEST_DATABASE_URL'],autocommit=True)
        self.schema='location_price_test_'+uuid.uuid4().hex
        self.connection.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(self.schema)))
        self.connection.execute(sql.SQL('SET search_path TO {}').format(sql.Identifier(self.schema)))
        self.connection.execute((ROOT/'rocket-credit-backend/src/main/resources/db/migration/V10__budapest_location_pricing.sql').read_text())
        self.snapshot=pricing.load_snapshot(SNAPSHOT)

    def tearDown(self):
        from psycopg import sql
        self.connection.execute(sql.SQL('DROP SCHEMA {} CASCADE').format(sql.Identifier(self.schema)))
        self.connection.close()

    def test_idempotence_publication_and_new_version(self):
        version=pricing.import_snapshot(self.snapshot,self.connection)
        self.assertEqual(version,pricing.import_snapshot(self.snapshot,self.connection))
        for table,expected in [('location_price_datasets',1),('location_district_prices',23),('location_venue_baselines',5),('location_venue_prices',115)]:
            self.assertEqual(self.connection.execute(f'SELECT count(*) FROM {table}').fetchone()[0],expected)
        self.snapshot['housing']['districts'][0]['rentPerM2']='6471'
        newer=pricing.import_snapshot(self.snapshot,self.connection)
        self.assertNotEqual(version,newer)
        self.assertEqual(self.connection.execute('SELECT dataset_version FROM location_price_datasets WHERE active').fetchall(),[(newer,)])
        self.assertEqual(self.connection.execute('SELECT count(*) FROM location_venue_prices WHERE dataset_version=%s',(version,)).fetchone()[0],115)

    def test_failure_after_inserts_rolls_back_and_preserves_active_dataset(self):
        import psycopg
        version=pricing.import_snapshot(self.snapshot,self.connection)
        self.snapshot['housing']['districts'][0]['rentPerM2']='6471'
        expected=pricing.calculate(self.snapshot)['datasetVersion']
        # Force a real database error after district/baseline inserts but before publication.
        self.connection.execute("CREATE FUNCTION reject_test_price() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simulated insert failure'; END; $$")
        self.connection.execute('CREATE TRIGGER reject_test_price BEFORE INSERT ON location_venue_prices FOR EACH ROW EXECUTE FUNCTION reject_test_price()')
        with self.assertRaises(psycopg.Error): pricing.import_snapshot(self.snapshot,self.connection)
        self.assertEqual(self.connection.execute('SELECT dataset_version FROM location_price_datasets WHERE active').fetchall(),[(version,)])
        self.assertEqual(self.connection.execute('SELECT count(*) FROM location_price_datasets WHERE dataset_version=%s',(expected,)).fetchone()[0],0)
        self.assertEqual(self.connection.execute('SELECT count(*) FROM location_district_prices WHERE dataset_version=%s',(expected,)).fetchone()[0],0)

    def test_database_rejects_incomplete_publication(self):
        import psycopg
        with self.assertRaises(psycopg.Error):
            self.connection.execute("INSERT INTO location_price_datasets (dataset_version,housing_observed_at,housing_retrieved_at,district_average,content_checksum,source_url,active) VALUES ('incomplete','2026-09-30',now(),1,repeat('a',64),'https://example.test',TRUE)")


if __name__ == '__main__':
    unittest.main()
