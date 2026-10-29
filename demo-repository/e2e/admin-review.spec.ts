// AI-assisted (Claude), 2026; reviewed by the author.
import { expect, test, type Page } from '@playwright/test';
import { applyAsRiley, shot, signIn, signOut } from './support/roles';

/**
 * Live stack: Riley's request ends in REVIEW, an administrator decides it (human intervention), and Riley
 * sees the result. The approved path stops at the enabled "Accept" button so Riley's seeded history stays
 * unchanged for the other specs; the backend suite covers the acceptance itself.
 */
async function decide(page: Page, application: number, outcome: 'Approve' | 'Reject', note: string, screenshot?: string) {
  await signIn(page, 'admin@demo.rocket.local', /\/admin$/);
  await page.goto(`/admin/reviews/${application}`);
  await expect(page.getByRole('heading', { name: `Request #${application}` })).toBeVisible();
  await expect(page.getByText('Your profile is not complete.')).toBeVisible();
  await page.getByRole('radio', { name: outcome }).check();
  await page.getByLabel('Note to the customer').fill(note);
  if (screenshot) await shot(page, screenshot);
  await page.getByRole('button', { name: 'Review decision' }).click();
  await expect(page.getByText('This decision is final')).toBeVisible();
  await page.getByRole('button', { name: 'Confirm decision' }).click();
  await expect(page.getByText(`Note: ${note}`)).toBeVisible();
  await signOut(page);
}

test('REVIEW → administrator approves → Riley can accept', async ({ page }) => {
  await signIn(page, 'riley@demo.rocket.local', /account-dashboard/);
  const id = await applyAsRiley(page);
  await expect(page.getByText('An administrator will look at this request')).toBeVisible();
  await signOut(page);

  await decide(page, id, 'Approve', 'History with this store is good enough for this small amount.', '16-admin-review-detail');

  await signIn(page, 'admin@demo.rocket.local', /\/admin$/);
  await page.goto('/admin/audit');
  await expect(page.getByRole('cell', { name: 'ADMIN_REVIEW_DECIDED' }).first()).toBeVisible();
  await shot(page, '17-admin-audit-log');
  await signOut(page);

  await signIn(page, 'riley@demo.rocket.local', /account-dashboard/);
  await page.goto(`/applications/${id}`);
  await expect(page.getByRole('heading', { name: 'Human review: approved' })).toBeVisible();
  await expect(page.getByText('History with this store is good enough for this small amount.')).toBeVisible();
  await expect(page.getByText('Needs review').first()).toBeVisible();          // the automatic result is kept
  await expect(page.getByRole('button', { name: 'Accept and pay seller' })).toBeEnabled();
  await shot(page, '18-customer-decision-after-review');
  await page.goto('/applications');
  await expect(page.getByText('approved after review').first()).toBeVisible();
});

test('REVIEW → administrator rejects → Riley sees the note and no offer', async ({ page }) => {
  await signIn(page, 'riley@demo.rocket.local', /account-dashboard/);
  const id = await applyAsRiley(page);
  await signOut(page);

  await decide(page, id, 'Reject', 'A recent late payment; please try again after the next repayment.');

  await signIn(page, 'riley@demo.rocket.local', /account-dashboard/);
  await page.goto(`/applications/${id}`);
  await expect(page.getByRole('heading', { name: 'Human review: not approved' })).toBeVisible();
  await expect(page.getByText('A recent late payment; please try again after the next repayment.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Accept and pay seller' })).toHaveCount(0);
  const accept = await page.request.post(`/api/applications/${id}/accept`, {
    headers: { 'X-XSRF-TOKEN': (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? '', 'Idempotency-Key': `e2e-${Date.now()}` },
  });
  expect(accept.status()).toBe(409);
});
