import { expect, test, type Page } from '@playwright/test';
import { formatCurrency } from '../src/utils/format';

async function register(page: Page, name = 'Dashboard Tester') {
  await page.goto('/register');
  await page.getByLabel('Name').fill(name);
  await page.getByLabel('Email').fill(`dashboard-${Date.now()}-${Math.random().toString(36).slice(2)}@example.test`);
  await page.getByLabel('Password').fill('password-123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/account-dashboard$/);
}

const sectionLinks = [
  ['budget', 'Budget', 'Income and housing'],
  ['activity', 'Activity', 'Where you spend time'],
] as const;

test('dashboard tabs have direct URLs, legacy links and responsive navigation', async ({ page }) => {
  await register(page);
  const nav = page.getByRole('navigation', { name: 'Dashboard sections' });
  await expect(nav.getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByRole('heading', { name: 'What you can afford' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recent applications' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Income and housing' })).toHaveCount(0);

  for (const [id, label, heading] of sectionLinks) {
    await nav.getByRole('link', { name: label }).click();
    await expect(page).toHaveURL(`/account-dashboard?section=${id}`);
    await expect(nav.getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page');
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'What you can afford' })).toHaveCount(0);
  }

  await page.goBack();
  await expect(page).toHaveURL('/account-dashboard?section=budget');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Address and living costs' })).toBeVisible();

  // Links from the earlier seven-section dashboard still land on the matching tab.
  for (const [legacy, heading] of [['financial', 'Income and housing'], ['address', 'Address and living costs'], ['profile', 'What you can afford'], ['unknown', 'What you can afford']]) {
    await page.goto(`/account-dashboard?section=${legacy}`);
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
  }

  await page.setViewportSize({ width: 390, height: 844 });
  await nav.getByRole('link', { name: 'Activity' }).click();
  await expect(page.getByRole('heading', { name: 'Social activity' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('Facebook return opens activity and dismissing its message keeps the tab', async ({ page }) => {
  await register(page);
  for (const [outcome, text] of [['permission', 'access to posts was not allowed'], ['session', 'Your session ended during Facebook login']]) {
    await page.goto(`/account-dashboard?section=activity&social=${outcome}`);
    await expect(page.getByText(text, { exact: false })).toBeVisible();
  }
  await page.goto('/account-dashboard?section=activity&social=connected');
  await expect(page.getByRole('heading', { name: 'Social activity' })).toBeVisible();
  await expect(page.getByText('Facebook connected. You can analyze your posts now.')).toBeVisible();
  await page.getByRole('button', { name: 'Dismiss' }).click();
  await expect(page).toHaveURL('/account-dashboard?section=activity');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Social activity' })).toBeVisible();
});

test('saved address and documented housing changes are reflected when Overview reopens', async ({ page }) => {
  await register(page, 'Riley Review');   // the synthetic housing samples are made out to Riley Review
  const nav = page.getByRole('navigation', { name: 'Dashboard sections' });
  const before = await (await page.request.get('/api/me/affordability')).json();

  await nav.getByRole('link', { name: 'Budget' }).click();
  const income = page.locator('section', { has: page.getByRole('heading', { name: 'Income and housing' }) });
  await expect(income.getByText('Other', { exact: true })).toBeVisible();
  await expect(income.getByRole('button')).toHaveCount(0);
  const housing = page.getByRole('region', { name: 'Housing situation' });
  await expect(housing.getByRole('button', { name: 'Edit', exact: true })).toBeDisabled();

  await page.getByLabel('Postal code', { exact: true }).fill('1051');
  await expect(page.locator('#district-hint')).toHaveText('Budapest District V');
  await page.getByLabel('Street', { exact: true }).fill('Minta utca');
  await page.getByLabel('Building', { exact: true }).fill('12');
  await page.getByRole('button', { name: 'Save address', exact: true }).click();
  await expect(page.getByText('Address saved. Verify it below to use local prices.')).toBeVisible();
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).generation).toBeGreaterThan(before.generation);

  // The wrong document is refused and changes nothing; the matching sample changes the situation.
  await housing.getByRole('button', { name: 'Edit', exact: true }).click();
  await page.getByLabel('New housing situation', { exact: true }).click();
  await page.getByRole('option', { name: 'Owner', exact: true }).click();
  await housing.getByLabel('Choose document').setInputFiles('src/assets/sample-housing-renting.png');
  await housing.getByRole('button', { name: 'Check and save' }).click();
  await expect(housing.getByText('The document does not support this change.')).toBeVisible();
  await expect(housing.getByText(/a rental agreement, which does not support "Owner"/)).toBeVisible();
  expect((await (await page.request.get('/api/me/housing')).json()).housingSituation).toBe('OTHER');
  await housing.getByRole('button', { name: 'Use the sample document' }).click();
  await expect(housing.getByText('Housing situation changed to Owner. Your amount is being recalculated.')).toBeVisible();
  await expect(income.getByText('Owner', { exact: true })).toBeVisible();
  await expect(housing.getByText(/verified with a document on/)).toBeVisible();
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).financialRevision).toBeGreaterThan(before.financialRevision);

  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).status, { timeout: 15_000 }).toBe('READY');
  const estimate = await (await page.request.get('/api/me/affordability')).json();
  await nav.getByRole('link', { name: 'Overview' }).click();
  await expect(page.getByRole('heading', { name: 'What you can afford' })).toBeVisible();
  await expect(page.locator('#dashboard-content').getByText(estimate.baseAmount == null ? 'Unavailable' : formatCurrency(estimate.baseAmount), { exact: true }).first()).toBeVisible();
});
