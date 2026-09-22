import { expect, test, type Page } from '@playwright/test';

/**
 * 8.2 — one browser scenario: register, see history, hand off from a store page,
 * submit an application, revisit the saved result, log out. Screenshots for the
 * diploma land in docs/screenshots/ when SCREENSHOTS=1.
 */
const shots = process.env.SCREENSHOTS === '1';
const shot = async (page: Page, name: string) => {
  if (shots) await page.screenshot({ path: `../docs/screenshots/${name}.png`, fullPage: true });
};

test('register → history → store handoff → decision → logout', async ({ page }) => {
  const email = `e2e-${Date.now()}@example.test`;

  // Register (the demo app registers, then signs in automatically).
  await page.goto('/register');
  await page.getByLabel('Name').fill('E2E Tester');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('password-123');
  await shot(page, '01-register');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/account-dashboard/);
  await expect(page.getByRole('heading', { name: /Hello, E2E Tester/ })).toBeVisible();
  await expect(page.getByText('Synthetic starter data for new accounts')).toBeVisible();
  await shot(page, '02-dashboard-new-account');

  // Purchase history (starter data, one purchase per store) with the partner filter.
  await page.goto('/history');
  await expect(page.getByRole('table')).toBeVisible();
  await expect(page.getByRole('row')).toHaveCount(1 + 3); // header + three starter purchases
  await page.getByLabel('Store').selectOption('threadly');
  await expect(page.getByRole('row')).toHaveCount(1 + 1);
  await shot(page, '03-history');

  // Store page → "Apply with Rocket Credit" carries only partner + product id.
  await page.goto('/stores/threadly');
  await expect(page.getByRole('heading', { name: /Threadly/ })).toBeVisible();
  await shot(page, '04-store-threadly');
  const card = page.locator('li', { hasText: 'Linen shirt' });
  await card.getByRole('link', { name: 'Apply with Rocket Credit' }).click();
  await expect(page).toHaveURL(/\/apply\?partner=threadly&product=\d+/);
  await expect(page.getByLabel('Amount (USD)')).toBeDisabled();
  await expect(page.getByLabel('Amount (USD)')).toHaveValue('59');
  await shot(page, '05-apply-prefilled');

  // Submit; the button is disabled while pending; we land on the saved decision.
  const submit = page.getByRole('button', { name: 'Submit request' });
  await submit.click();
  await expect(page).toHaveURL(/\/applications\/\d+/);
  await expect(page.getByText(/policy rules-v1/)).toBeVisible();
  await expect(page.getByText(/Approved|Not approved|Needs review/).first()).toBeVisible();
  await shot(page, '06-decision');
  const decisionUrl = page.url();

  // The decision is persisted: reload and list.
  await page.reload();
  await expect(page.getByText(/policy rules-v1/)).toBeVisible();
  await page.goto('/applications');
  await expect(page.getByRole('link', { name: /\$59\.00 at Threadly/ })).toBeVisible();

  // Direct request with AI from the form.
  await page.goto('/apply');
  await page.getByLabel('Store').selectOption('markethub');
  await page.getByLabel('Amount (USD)').fill('120');
  await page.getByLabel(/Use AI analysis/).check();
  await page.getByRole('button', { name: 'Submit request' }).click();
  await expect(page).toHaveURL(/\/applications\/\d+/);
  await expect(page.getByText(/model logreg-v1/)).toBeVisible();
  await shot(page, '07-decision-with-ai');

  // Logout closes the session: the private page redirects to login.
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto(decisionUrl);
  await expect(page).toHaveURL(/\/login/);
  await shot(page, '08-login-after-logout');
});

test('demo persona sees seeded three-partner history and a review outcome', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('riley@demo.rocket.local');
  await page.getByLabel('Password').fill('rocket-demo-123');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/account-dashboard/);
  await expect(page.getByRole('heading', { name: 'Purchase history by store' })).toBeVisible();
  await expect(page.getByText('completed purchases').first()).toBeVisible();

  await page.goto('/apply?partner=markethub&amount=250');
  await expect(page.getByLabel('Amount (USD)')).toHaveValue('250');
  await page.getByRole('button', { name: 'Submit request' }).click();
  await expect(page).toHaveURL(/\/applications\/\d+/);
  await expect(page.getByText('Needs review').first()).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'PROFILE_INCOMPLETE' })).toBeVisible();
  await shot(page, '09-riley-review');
});

test('wrong password gets a generic message', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('avery@demo.rocket.local');
  await page.getByLabel('Password').fill('not-the-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toHaveText('Invalid email or password.');
});
