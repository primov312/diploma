import { expect, test, type Page } from '@playwright/test';

test.beforeAll(async ({ request }) => {
  await expect.poll(async () => {
    try { return (await request.get('/actuator/health')).status(); }
    catch { return 0; }
  }, { timeout: 30_000 }).toBe(200);
});

async function enableLocation(page: Page) {
  // The controlled checkbox adopts the saved server value after the permission request completes.
  await page.getByLabel('Allow location demo').click();
  await expect(page.getByLabel('Allow location demo')).toBeChecked();
}

async function register(page: Page) {
  await page.goto('/register');
  await page.getByLabel('Name').fill('Location Pricing Tester');
  await page.getByLabel('Email').fill(`location-${Date.now()}-${Math.random().toString(36).slice(2)}@example.test`);
  await page.getByLabel('Password').fill('password-123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/account-dashboard/);
}

test('live imported benchmarks cover five categories, persist, and leave finances unchanged', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await register(page);
  const before = await (await page.request.get('/api/me/financial-inputs')).json();
  await enableLocation(page);
  await expect(page.getByRole('button', { name: 'Run location analysis' })).toBeEnabled();
  const responsePromise = page.waitForResponse(response => response.url().endsWith('/api/me/demo-signals/location-runs') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Run location analysis' }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(202);
  const result = await response.json();
  expect(result.report.pricing.available).toBe(true);
  expect(result.report.pricing.housingObservedAt).toBe('2026-09-30');
  expect(result.report.visits.map((visit: { pricing: { category: string } }) => visit.pricing.category).sort())
    .toEqual(['CAFE', 'GROCERY', 'GYM', 'LIBRARY', 'STARBUCKS']);
  expect(result.report.visits.every((visit: { pricing: { currency: string } }) => visit.pricing.currency === 'HUF')).toBe(true);
  await expect(page.getByRole('heading', { name: 'Estimated benchmark prices' })).toBeVisible();
  await expect(page.getByTestId('location-visit-price')).toHaveCount(5);
  await expect(page.getByText('Annual membership benchmark')).toBeVisible();
  await page.getByText('Housing reference · 2026-09-30').click();
  await expect(page.getByRole('link', { name: 'District rental statistics' })).toBeVisible();
  await page.getByText('Benchmark source and calculation').first().click();
  await expect(page.getByRole('link', { name: 'Source', exact: true })).toHaveCount(5);
  const after = await (await page.request.get('/api/me/financial-inputs')).json();
  expect(after).toEqual(before);
  await page.reload();
  await expect(page.getByTestId('location-visit-price')).toHaveCount(5);
  const reports = await (await page.request.get('/api/me/demo-signals/reports?kind=LOCATION')).json();
  expect(reports[0]).toEqual(result.report);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('heading', { name: 'Estimated benchmark prices' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(errors).toEqual([]);
});

test('regular scenario keeps parks unpriced', async ({ page }) => {
  await register(page);
  await enableLocation(page);
  await page.getByRole('button', { name: 'Location scenario' }).click();
  await page.getByRole('option', { name: 'regular week', exact: true }).click();
  await page.getByRole('button', { name: 'Run location analysis' }).click();
  await expect(page.getByText('No benchmark for this place type')).toBeVisible();
  await expect(page.getByTestId('location-visit-price')).toHaveCount(3);
});

test('older saved reports and unavailable datasets remain readable', async ({ page }) => {
  const report: Record<string, unknown> = { scenarioId: 'sparse', status: 'COMPLETE', dataSource: 'SYNTHETIC', visitCount: 1,
    distinctDistricts: 1, mostVisitedDistrict: 'Budapest XI', visits: [{ id: 'old-visit', district: 'Budapest XI', place: 'Cafe', arrival: '2026-09-06T12:00:00Z' }] };
  await page.route('**/api/me/demo-signals/reports?kind=LOCATION', route => route.fulfill({ json: [report] }));
  await register(page);
  await expect(page.getByText('Run location analysis again to attach benchmark prices to this saved report.')).toBeVisible();
  await expect(page.getByText('Cafe · Budapest XI', { exact: true })).toBeVisible();
  report.pricing = { available: false, method: 'Test reference method' };
  await page.reload();
  await expect(page.getByText('Price references are not available for this report. Import a dataset and run analysis again.')).toBeVisible();
});
