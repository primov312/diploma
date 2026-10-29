// AI-assisted (Claude), 2026; reviewed by the author.
import { expect, test } from '@playwright/test';
import { shot, signIn, signOut } from './support/roles';

/** Live stack: each kind of user lands on its own start page, sees its own navigation and cannot open the others. */
const nav = (page: import('@playwright/test').Page) => page.getByRole('navigation', { name: 'Main navigation' });

test('seller lands on /seller and cannot open administrator or customer pages', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: 'Netflix seller' }).click();     // demo quick-fill
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(/\/seller$/);
  await expect(page.getByRole('heading', { name: 'Netflix overview' })).toBeVisible();
  for (const name of ['Overview', 'Orders', 'Catalogue']) await expect(nav(page).getByRole('link', { name, exact: true })).toBeVisible();
  for (const name of ['Wallet', 'Privacy', 'Applications', 'Users']) await expect(nav(page).getByRole('link', { name, exact: true })).toHaveCount(0);

  await page.goto('/admin');
  await expect(page).toHaveURL(/\/seller$/);
  await expect(page.getByText('That page is for administrator accounts.')).toBeVisible();
  await page.goto('/balance');
  await expect(page).toHaveURL(/\/seller$/);
  await expect(page.getByText('That page is for customer accounts.')).toBeVisible();

  // The server refuses too, whatever the screens do.
  const wallet = await page.request.get('/api/wallet');
  expect(wallet.status()).toBe(403);
  expect((await wallet.json()).error).toBe('FORBIDDEN_ROLE');

  // The marketplace is visible, but buying and credit are for customers.
  await page.goto('/marketplace');
  await expect(page.getByText(/Customers can apply for credit or pay here/).first()).toBeVisible();
  await expect(page.getByRole('link', { name: 'Apply for credit' })).toHaveCount(0);
  await signOut(page);
});

test('administrator lands on /admin with the administrator navigation', async ({ page }) => {
  await signIn(page, 'admin@demo.rocket.local', /\/admin$/);
  await expect(page.getByRole('heading', { name: 'Platform overview' })).toBeVisible();
  for (const name of ['Overview', 'Users', 'Reviews', 'Partners', 'Audit log']) await expect(nav(page).getByRole('link', { name, exact: true })).toBeVisible();
  await expect(nav(page).getByRole('link', { name: 'Wallet', exact: true })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Services' })).toBeVisible();
  await shot(page, '14-admin-overview');

  await nav(page).getByRole('link', { name: 'Users', exact: true }).click();
  await page.getByLabel('Search e-mail or name').fill('@demo.rocket.local');
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  await expect(page.getByRole('cell', { name: /^Netflix Seller \(demo\) netflix@demo\.rocket\.local/ })).toBeVisible();
  await shot(page, '15-admin-users');

  await page.goto('/seller');
  await expect(page).toHaveURL(/\/admin$/);
  await page.goto('/applications');
  await expect(page).toHaveURL(/\/admin$/);
  // The logo leads to the administrator's own start page.
  await page.getByRole('link', { name: /Rocket Credit/ }).first().click();
  await expect(page).toHaveURL(/\/admin$/);
  await signOut(page);
});

test('customer cannot open seller or administrator pages', async ({ page }) => {
  await signIn(page, 'avery@demo.rocket.local', /account-dashboard/);
  for (const name of ['Dashboard', 'Applications', 'Wallet', 'Privacy']) await expect(nav(page).getByRole('link', { name, exact: true })).toBeVisible();
  await expect(nav(page).getByRole('link', { name: 'Catalogue', exact: true })).toHaveCount(0);
  await page.goto('/seller/catalog');
  await expect(page).toHaveURL(/account-dashboard/);
  await expect(page.getByText('That page is for seller accounts.')).toBeVisible();
  await page.goto('/admin/users');
  await expect(page).toHaveURL(/account-dashboard/);
  expect((await page.request.get('/api/admin/overview')).status()).toBe(403);
  await signOut(page);
  // Signed out, a staff page asks for sign-in and comes back afterwards.
  await page.goto('/admin/audit');
  await expect(page).toHaveURL(/\/login/);
});
