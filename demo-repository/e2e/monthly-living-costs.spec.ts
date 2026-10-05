import { expect, test, type Page } from '@playwright/test';

test.beforeAll(async ({ request }) => {
  await expect.poll(async () => {
    try { return (await request.get('/actuator/health')).status(); }
    catch { return 0; }
  }, { timeout: 30_000 }).toBe(200);
});

async function register(page: Page) {
  await page.goto('/register');
  await page.getByLabel('Name').fill('Riley Review');
  await page.getByLabel('Email').fill(`monthly-${Date.now()}-${Math.random().toString(36).slice(2)}@example.test`);
  await page.getByLabel('Password').fill('password-123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/account-dashboard/);
}

async function financialInputs(page: Page, housingSituation = 'RENTING') {
  const current = await (await page.request.get('/api/me/financial-inputs')).json();
  const csrf = await (await page.request.get('/api/csrf')).json();
  const response = await page.request.put('/api/me/financial-inputs', {
    headers: { [csrf.headerName]: csrf.token }, data: { expectedRevision: current.revision,
      housingSituation, expenseMode: 'AUTOMATIC' },
  });
  expect(response.ok()).toBe(true);
  await page.reload();
}

async function saveAddress(page: Page) {
  await page.getByRole('navigation', { name: 'Dashboard sections' }).getByRole('link', { name: 'Address & costs' }).click();
  await page.getByLabel('Postal code', { exact: true }).fill('1051');
  await expect(page.getByLabel('District determined by postal code')).toHaveValue('Budapest District V');
  await page.getByLabel('Street', { exact: true }).fill('Minta utca');
  await page.getByLabel('Building', { exact: true }).fill('12');
  await page.getByRole('button', { name: 'Save address', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Review prepared sample' })).toBeVisible();
  await expect(page.getByTestId('monthly-living-cost-table')).toBeVisible();
}

test('automatic budget removes amount inputs, saves size and sharing, and forecasts from location history', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await register(page); await financialInputs(page); await saveAddress(page);
  await expect(page.getByTestId('monthly-living-cost-table').getByRole('row', { name: /Rent benchmark/ })).toContainText(/350.*000/);
  const before = await (await page.request.get('/api/me/monthly-living-costs')).json();
  expect(before.eligible).toBe(false); expect(before.profile.apartmentSize).toBe(50);
  expect(before.monthlyRent).toBe(350000); expect(before.groceries).toHaveLength(12);
  await page.getByLabel('People sharing rent', { exact: true }).fill('2');
  await expect(page.getByLabel('Other spending (HUF / month)', { exact: true })).toHaveCount(0);
  await expect(page.getByLabel('Net income', { exact: true })).toHaveCount(0);
  await page.getByText('Monthly grocery basket · 12 products').click();
  await expect(page.getByLabel('Milk (litres / month)', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Save size and sharing', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Save size and sharing', exact: true }).click();
  await expect(page.getByText('Housing details saved. Your automatic estimate is recalculating.')).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('People sharing rent', { exact: true })).toHaveValue('2');
  await expect(page.getByLabel('Other spending (HUF / month)', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Review prepared sample' }).click();
  await expect.poll(async () => (await (await page.request.get('/api/me/address')).json()).verificationStatus, { timeout: 40_000 }).toBe('VERIFIED_DEMO');
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).status, { timeout: 15_000 }).toBe('READY');
  let monthly = await (await page.request.get('/api/me/monthly-living-costs')).json();
  expect(monthly.eligible).toBe(true); expect(monthly.monthlyRent).toBe(175000); expect(monthly.monthlyOther).toBeGreaterThan(0);
  let estimate = await (await page.request.get('/api/me/affordability')).json();
  expect(estimate.formulaVersion).toBe('affordability-v4'); expect(estimate.policyVersion).toBe('rules-v4');
  expect(estimate.localCostContext.datasetVersion).toBe(monthly.datasetVersion);
  const usd = (value: number) => Math.round(value / 360 * 100) / 100;
  expect(estimate.breakdown.effectiveExpenses).toBeCloseTo(Math.max(usd(monthly.suppliedExpenseFloorHuf), usd(monthly.monthlyRent) + usd(monthly.monthlyGroceries) + usd(monthly.monthlyOther)), 2);
  await page.getByLabel('Apartment size (m²)', { exact: true }).fill('40');
  await expect(page.getByRole('button', { name: 'Save size and sharing', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Save size and sharing', exact: true }).click();
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).localCostContext?.monthlyRent, { timeout: 15_000 }).toBe(140000);
  expect((await (await page.request.get('/api/me/address')).json()).verificationStatus).toBe('VERIFIED_DEMO');
  await page.getByRole('navigation', { name: 'Dashboard sections' }).getByRole('link', { name: 'Activity' }).click();
  await page.getByRole('button', { name: 'Run location analysis' }).click();
  await expect.poll(async () => (await (await page.request.get('/api/me/monthly-living-costs')).json()).activityForecast.historySufficient).toBe(true);
  monthly = await (await page.request.get('/api/me/monthly-living-costs')).json();
  expect(monthly.activityForecast.method).toBe('MEAN_OF_BASELINE_AND_HISTORY');
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).localCostContext?.activityForecast?.reportId).toBe(monthly.activityForecast.reportId);
  await page.getByRole('navigation', { name: 'Dashboard sections' }).getByRole('link', { name: 'Address & costs' }).click();
  await page.getByText('How activity spending is forecast').click();
  await expect(page.getByText(/Sufficient history: average/)).toBeVisible();
  await page.getByText('Sources and calculation dates').click();
  await expect(page.getByRole('link', { name: 'District rental statistics' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Cheese: Tesco/ })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(errors).toEqual([]);
});

test('postcode changes clear previous prices, reject special codes, and owner budgets exclude rent', async ({ page }) => {
  await register(page); await financialInputs(page, 'OWNER'); await saveAddress(page);
  await expect(page.getByText('Excluded: you are not renting')).toBeVisible();
  const monthly = await (await page.request.get('/api/me/monthly-living-costs')).json();
  expect(monthly.monthlyTotal).toBeCloseTo(monthly.monthlyGroceries + monthly.monthlyOther,2);
  await page.getByLabel('Postal code', { exact: true }).fill('105');
  await expect(page.getByTestId('monthly-living-cost-table')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Save address', exact: true })).toBeDisabled();
  await page.getByLabel('Postal code', { exact: true }).fill('1007');
  await expect(page.getByText('This code is not a supported residential Budapest postcode.')).toBeVisible();
  await page.getByLabel('Postal code', { exact: true }).fill('1239');
  await expect(page.getByLabel('District determined by postal code')).toHaveValue('Budapest District XXIII');
  await expect(page.getByTestId('monthly-living-cost-table')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save size and sharing', exact: true })).toBeDisabled();
});

test('missing reference data is displayed as unavailable', async ({ page }) => {
  await page.route('**/api/me/monthly-living-costs', route => route.request().method() === 'GET'
    ? route.fulfill({ json: { available: false, unavailableReason: 'NO_DATASET', eligible: false, currency: 'HUF', hufPerUsd: 360 } })
    : route.continue());
  await register(page);
  await page.getByRole('navigation', { name: 'Dashboard sections' }).getByRole('link', { name: 'Address & costs' }).click();
  await expect(page.getByText('Monthly price references are unavailable. Import the researched dataset to calculate costs.')).toBeVisible();
  await expect(page.getByTestId('monthly-living-cost-table')).toHaveCount(0);
});
