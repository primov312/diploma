import { expect, test, type Page } from '@playwright/test';
import { formatCurrency } from '../src/utils/format';

async function register(page: Page) {
  await page.goto('/register');
  await page.getByLabel('Name').fill('Dashboard Tester');
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

test('saved financial and address changes are reflected when Overview reopens', async ({ page }) => {
  await register(page);
  const nav = page.getByRole('navigation', { name: 'Dashboard sections' });
  const before = await (await page.request.get('/api/me/affordability')).json();

  await nav.getByRole('link', { name: 'Budget' }).click();
  const housing = page.getByLabel('Housing situation', { exact: true });
  await housing.click();
  const choice = (await housing.textContent())?.includes('Renting') ? 'Owner' : 'Renting';
  await page.getByRole('option', { name: choice, exact: true }).click();
  await page.locator('section', { has: page.getByRole('heading', { name: 'Income and housing' }) }).getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Saved. Your amount is being recalculated.')).toBeVisible();
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).financialRevision).toBeGreaterThan(before.financialRevision);
  const afterFinancial = await (await page.request.get('/api/me/affordability')).json();

  await page.getByLabel('Postal code', { exact: true }).fill('1051');
  await expect(page.locator('#district-hint')).toHaveText('Budapest District V');
  await page.getByLabel('Street', { exact: true }).fill('Minta utca');
  await page.getByLabel('Building', { exact: true }).fill('12');
  await page.getByRole('button', { name: 'Save address', exact: true }).click();
  await expect(page.getByText('Address saved. Verify it below to use local prices.')).toBeVisible();
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).generation).toBeGreaterThan(afterFinancial.generation);

  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).status, { timeout: 15_000 }).toBe('READY');
  const estimate = await (await page.request.get('/api/me/affordability')).json();
  await nav.getByRole('link', { name: 'Overview' }).click();
  await expect(page.getByRole('heading', { name: 'What you can afford' })).toBeVisible();
  await expect(page.locator('#dashboard-content').getByText(estimate.baseAmount == null ? 'Unavailable' : formatCurrency(estimate.baseAmount), { exact: true }).first()).toBeVisible();
});
