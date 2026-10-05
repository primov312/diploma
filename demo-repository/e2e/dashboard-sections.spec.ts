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
  ['financial', 'Finances', 'Financial information'],
  ['address', 'Address & costs', 'Living address and local cost context'],
  ['activity', 'Activity', 'Activity analysis'],
  ['profile', 'Profile', 'Account profile'],
  ['purchases', 'Purchases', 'Purchase history by store'],
  ['applications', 'Applications', 'Credit applications'],
] as const;

test('dashboard sections have direct URLs and responsive navigation', async ({ page }) => {
  await register(page);
  const nav = page.getByRole('navigation', { name: 'Dashboard sections' });
  await expect(nav.getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByRole('heading', { name: 'Estimated affordable amount' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Financial information' })).toHaveCount(0);

  for (const [id, label, heading] of sectionLinks) {
    await nav.getByRole('link', { name: label }).click();
    await expect(page).toHaveURL(`/account-dashboard?section=${id}`);
    await expect(nav.getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page');
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Estimated affordable amount' })).toHaveCount(0);
  }

  await page.goBack();
  await expect(page).toHaveURL('/account-dashboard?section=purchases');
  await expect(page.getByRole('heading', { name: 'Purchase history by store' })).toBeVisible();
  await page.goForward();
  await expect(page).toHaveURL('/account-dashboard?section=applications');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Credit applications' })).toBeVisible();

  await page.goto('/account-dashboard?section=unknown');
  await expect(page.getByRole('heading', { name: 'Estimated affordable amount' })).toBeVisible();
  await page.goto('/account-dashboard?section=address');
  await expect(page.getByRole('heading', { name: 'Living address and local cost context' })).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('combobox', { name: 'Dashboard section' })).toHaveValue('address');
  await page.getByRole('combobox', { name: 'Dashboard section' }).selectOption('profile');
  await expect(page).toHaveURL('/account-dashboard?section=profile');
  await expect(page.getByRole('heading', { name: 'Account profile' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('Facebook return opens activity and dismissing its message keeps the section', async ({ page }) => {
  await register(page);
  await page.goto('/account-dashboard?social=connected');
  await expect(page.getByRole('heading', { name: 'Activity analysis' })).toBeVisible();
  await expect(page.getByText('Facebook account connected. You can now analyze it.')).toBeVisible();
  await page.getByRole('button', { name: 'Dismiss' }).click();
  await expect(page).toHaveURL('/account-dashboard?section=activity');
  await expect(page.getByRole('heading', { name: 'Activity analysis' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Activity analysis' })).toBeVisible();
});

test('saved financial and address changes are reflected when Overview reopens', async ({ page }) => {
  await register(page);
  const nav = page.getByRole('navigation', { name: 'Dashboard sections' });
  const before = await (await page.request.get('/api/me/affordability')).json();

  await nav.getByRole('link', { name: 'Finances' }).click();
  const housing = page.getByLabel('Housing situation', { exact: true });
  await housing.click();
  const choice = (await housing.textContent())?.includes('Renting') ? 'Owner' : 'Renting';
  await page.getByRole('option', { name: choice, exact: true }).click();
  await page.getByRole('button', { name: 'Save housing situation' }).click();
  await expect(page.getByText('Housing situation saved. Automatic costs are recalculating.')).toBeVisible();
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).financialRevision).toBeGreaterThan(before.financialRevision);
  const afterFinancial = await (await page.request.get('/api/me/affordability')).json();

  await nav.getByRole('link', { name: 'Address & costs' }).click();
  await page.getByLabel('Postal code', { exact: true }).fill('1051');
  await expect(page.getByLabel('District determined by postal code')).toHaveValue('Budapest District V');
  await page.getByLabel('Street', { exact: true }).fill('Minta utca');
  await page.getByLabel('Building', { exact: true }).fill('12');
  await page.getByRole('button', { name: 'Save address', exact: true }).click();
  await expect(page.getByText('Address saved. Verification is reset and the estimate is recalculating without district references.')).toBeVisible();
  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).generation).toBeGreaterThan(afterFinancial.generation);

  await expect.poll(async () => (await (await page.request.get('/api/me/affordability')).json()).status, { timeout: 15_000 }).toBe('READY');
  const estimate = await (await page.request.get('/api/me/affordability')).json();
  await nav.getByRole('link', { name: 'Overview' }).click();
  await expect(page.getByRole('heading', { name: 'Estimated affordable amount' })).toBeVisible();
  await expect(page.locator('#dashboard-content').getByText(estimate.baseAmount == null ? 'Unavailable' : formatCurrency(estimate.baseAmount), { exact: true }).first()).toBeVisible();
});
