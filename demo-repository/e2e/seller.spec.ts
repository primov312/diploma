// AI-assisted (Claude), 2026; reviewed by the author.
import { expect, test } from '@playwright/test';
import { shot, signIn, signOut } from './support/roles';

/** Live stack: a seller adds a product, sees it in the marketplace, deactivates it and it disappears. */
test('seller adds a product, it appears in the marketplace, then is deactivated', async ({ page }) => {
  const name = `E2E scarf ${Date.now()}`;
  await signIn(page, 'zara@demo.rocket.local', /\/seller$/);
  await expect(page.getByRole('heading', { name: 'Financed orders, last 30 days' })).toBeVisible();
  await shot(page, '11-seller-overview');

  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Orders', exact: true }).click();
  await expect(page.getByRole('table', { name: 'Financed orders' })).toBeVisible();
  await expect(page.getByRole('cell', { name: /^C-[0-9A-F]{6}$/ }).first()).toBeVisible();
  await expect(page.getByText('@demo.rocket.local')).toHaveCount(0);
  await shot(page, '12-seller-orders');

  await page.goto('/seller/catalog');
  await page.getByRole('button', { name: 'Add product' }).click();
  // Inline validation mirrors the server: an empty form and a price above the cap are refused before sending.
  await page.getByRole('button', { name: 'Add product' }).last().click();
  await expect(page.getByText('Enter a name of 1 to 160 characters.')).toBeVisible();
  await page.getByLabel('Name').fill(name);
  await page.getByLabel('Price (USD)').fill('5000');
  await page.getByLabel('Description').fill('A light scarf added by the end-to-end test.');
  await page.getByLabel('Category').selectOption({ index: 1 });
  await page.getByLabel('Image').selectOption({ index: 1 });
  await expect(page.getByText(/cannot be above your financing limit/)).toBeVisible();
  await page.getByLabel('Price (USD)').fill('12.50');
  await shot(page, '13-seller-catalog-form');
  await page.getByRole('button', { name: 'Add product' }).last().click();
  await expect(page.getByRole('cell', { name: new RegExp(`^${name}`) })).toBeVisible();

  await page.goto('/marketplace');
  await page.getByPlaceholder('Search products or sellers').fill(name);
  await expect(page.locator('li', { hasText: name })).toBeVisible();

  await page.goto('/seller/catalog');
  // The first attempt carries a wrong CSRF header; the server answers 403 CSRF_INVALID, the client fetches a
  // fresh token and retries once, and the user only sees the result.
  const attempts: number[] = [];
  await page.route(/\/api\/seller\/products\/\d+\/deactivate$/, async (route) => {
    const first = attempts.length === 0;
    const response = await route.fetch(first ? { headers: { ...route.request().headers(), 'x-xsrf-token': 'wrong-token' } } : undefined);
    attempts.push(response.status());
    await route.fulfill({ response });
  });
  await page.getByRole('button', { name: `Deactivate ${name}` }).click();
  await expect(page.getByRole('button', { name: `Activate ${name}` })).toBeVisible();
  await expect.poll(() => attempts).toEqual([403, 200]);
  await page.unroute(/\/api\/seller\/products\/\d+\/deactivate$/);
  await page.goto('/marketplace');
  await page.getByPlaceholder('Search products or sellers').fill(name);
  await expect(page.getByText('No products match.')).toBeVisible();
  await signOut(page);
});
