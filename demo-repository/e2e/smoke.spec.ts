import { expect, test, type Page } from '@playwright/test';
import { agreeIfAsked, agreeToFootprint } from './support/consent';

/**
 * 8.2 — one browser scenario: register, see history, hand off from the marketplace,
 * submit an application, revisit the saved result, log out. Screenshots for the
 * diploma land in docs/screenshots/ when SCREENSHOTS=1.
 */
const shots = process.env.SCREENSHOTS === '1';
const shot = async (page: Page, name: string) => {
  if (shots) await page.screenshot({ path: `../docs/screenshots/${name}.png`, fullPage: true });
};

async function reviewTerms(page: Page) {
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByLabel('Number of payments').selectOption('6');
  await page.getByRole('button', { name: 'See my payments' }).click();
}

test('register → history → marketplace handoff → decision → logout', async ({ page }) => {
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
  await expect(page.getByRole('heading', { name: 'What you can afford' })).toBeVisible();
  await shot(page, '02-dashboard-new-account');

  // Purchase history (starter data, one purchase per seller) with the seller filter.
  await page.goto('/history');
  await expect(page.getByRole('table')).toBeVisible();
  await expect(page.getByRole('row')).toHaveCount(1 + 3); // header + three starter purchases
  await page.getByLabel('Seller').click();
  await page.getByRole('option', { name: 'Zara', exact: true }).click();
  await expect(page.getByRole('row')).toHaveCount(1 + 1);
  await shot(page, '03-history');

  // One marketplace for every seller; "Apply for credit" carries only the product id.
  await page.goto('/marketplace');
  await page.getByPlaceholder('Search products or sellers').fill('Linen shirt');
  await shot(page, '04-marketplace');
  const card = page.locator('li', { hasText: 'Linen shirt' });
  await expect(card.getByText('Zara · Tops')).toBeVisible();
  await card.getByRole('link', { name: 'Apply for credit' }).click();
  await expect(page).toHaveURL(/\/apply\?product=\d+$/);
  await expect(page.getByText(/Zara · 21\s240\s*Ft/)).toBeVisible();
  await shot(page, '05-apply-prefilled');

  // Submit; we land on the saved decision.
  await reviewTerms(page);
  await agreeIfAsked(page);
  await page.getByRole('button', { name: 'Get my decision' }).click();
  await expect(page).toHaveURL(/\/applications\/\d+/);
  await expect(page.getByText(/Approved|Not approved|Needs review/).first()).toBeVisible();
  await page.getByText('Technical details', { exact: true }).click();
  await expect(page.getByText(/policy rules-/)).toBeVisible();
  await shot(page, '06-decision');
  const decisionUrl = page.url();

  // The decision is persisted: reload and list.
  await page.reload();
  await page.getByText('Technical details', { exact: true }).click();
  await expect(page.getByText(/policy rules-/)).toBeVisible();
  await page.goto('/applications');
  await expect(page.getByRole('link', { name: /Linen shirt · 21\s240\s*Ft/ })).toBeVisible();

  // Direct request for another amount, with AI.
  await page.goto('/apply');
  await page.getByRole('button', { name: 'Another amount' }).click();
  await page.getByLabel('Seller').click();
  await page.getByRole('option', { name: /Amazon/ }).click();
  await page.getByLabel('Amount (HUF)').fill('43200');
  await reviewTerms(page);
  await page.getByLabel(/Also use AI analysis/).check();
  await agreeIfAsked(page);
  await page.getByRole('button', { name: 'Get my decision' }).click();
  await expect(page).toHaveURL(/\/applications\/\d+/);
  await expect(page.getByText('Rules + AI')).toBeVisible();
  await page.getByText('Technical details', { exact: true }).click();
  await expect(page.getByText(/model logreg-v1/)).toBeVisible();
  await shot(page, '07-decision-with-ai');

  // Logout closes the session: the private page redirects to login.
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto(decisionUrl);
  await expect(page).toHaveURL(/\/login/);
  await shot(page, '08-login-after-logout');
});

test('demo persona sees seeded history, the district map and a review outcome', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: /Riley/ }).click(); // demo account quick-fill
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/account-dashboard/);
  await expect(page.getByRole('heading', { name: 'Your history' })).toBeVisible();
  await expect(page.getByText('purchases').first()).toBeVisible();

  // Activity: the Budapest map shades districts by visits and outlines the most visited one.
  await page.getByRole('navigation', { name: 'Dashboard sections' }).getByRole('link', { name: 'Activity' }).click();
  await agreeToFootprint(page);
  await page.getByRole('button', { name: 'Analyze visits' }).click();
  await expect(page.getByRole('heading', { name: 'Most visited districts' })).toBeVisible();
  await expect(page.getByRole('button', { name: /District XIII: 6 visits · most visited/ })).toBeVisible();
  await page.getByRole('button', { name: /District VIII: 3 visits/ }).click();
  await expect(page.getByText('Visits in district VIII (3)')).toBeVisible();
  await shot(page, '09-district-map');

  await page.goto('/apply?partner=markethub&amount=18000');
  await expect(page.getByLabel('Amount (HUF)')).toHaveValue('18000');
  await reviewTerms(page);
  await agreeIfAsked(page);
  await page.getByRole('button', { name: 'Get my decision' }).click();
  await expect(page).toHaveURL(/\/applications\/\d+/);
  await expect(page.getByText('Needs review').first()).toBeVisible();
  await expect(page.getByRole('listitem').filter({ hasText: 'Your profile is not complete.' }).first()).toBeVisible();
  await shot(page, '10-riley-review');
});

test('wrong password gets a generic message', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('avery@demo.rocket.local');
  await page.getByLabel('Password').fill('not-the-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toHaveText('Invalid email or password.');
});
