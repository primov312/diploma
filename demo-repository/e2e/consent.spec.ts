// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
import { expect, test, type Page } from '@playwright/test';
import { mockCustomerApi } from './support/redesignFixture';

/** Consent: nothing is processed before the customer agrees, and every choice can be changed later. */
async function reviewTerms(page: Page) {
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByLabel('Number of payments').selectOption('6');
  await page.getByRole('button', { name: 'See my payments' }).click();
}

const consentCalls = (requests: { path: string; body: Record<string, unknown> }[]) =>
  requests.filter(r => r.path.startsWith('/api/me/consents/') || r.path === '/api/applications');

test('a decision needs the data processing consent, which is saved before the request', async ({ page }) => {
  const state = await mockCustomerApi(page, true, { consented: false });
  await page.goto('/apply?product=14');
  await reviewTerms(page);
  const submit = page.getByRole('button', { name: 'Get my decision' });
  await expect(submit).toBeDisabled();
  await expect(page.getByText('Tick the consent above to continue.')).toBeVisible();
  await page.getByText('Read what I agree to').click();
  await expect(page.getByText('Mock wording: Processing my data for credit decisions.')).toBeVisible();
  await page.getByLabel(/I agree: Processing my data/).check();
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect(page).toHaveURL(/applications\/1$/);
  expect(consentCalls(state.requests).map(r => [r.path, r.body.granted ?? null])).toEqual([
    ['/api/me/consents/DATA_PROCESSING', true],
    ['/api/applications', null],
  ]);
});

test('the AI estimate asks for its own consent', async ({ page }) => {
  const state = await mockCustomerApi(page, true, { consented: false });
  await page.goto('/apply?product=14');
  await reviewTerms(page);
  await page.getByLabel(/I agree: Processing my data/).check();
  await page.getByLabel(/Also use AI analysis/).check();
  const submit = page.getByRole('button', { name: 'Get my decision' });
  await expect(submit).toBeDisabled();
  await page.getByLabel(/I agree: AI-assisted analysis/).check();
  await submit.click();
  await expect(page).toHaveURL(/applications\/1$/);
  expect(consentCalls(state.requests).map(r => r.path)).toEqual(['/api/me/consents/DATA_PROCESSING', '/api/me/consents/AI_ANALYSIS', '/api/applications']);
});

test('the privacy page shows each consent and withdrawing digital footprint closes the Activity analyses', async ({ page }) => {
  const state = await mockCustomerApi(page);
  await page.goto('/consents');
  const footprint = page.getByRole('switch', { name: 'Digital footprint signals' });
  await expect(page.getByRole('switch')).toHaveCount(3);
  await expect(footprint).toBeChecked();
  await expect(page.getByText('Your stored social and location analysis results are deleted')).toBeVisible();
  await footprint.click(); // the switch follows the server answer, so it flips a moment after the click
  await expect(footprint).not.toBeChecked();
  expect(consentCalls(state.requests).at(-1)).toMatchObject({ path: '/api/me/consents/DIGITAL_FOOTPRINT', body: { granted: false } });

  await page.goto('/account-dashboard?section=activity');
  await expect(page.getByRole('heading', { name: 'Digital footprint consent' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Analyze visits' })).toHaveCount(0);
  await page.getByRole('button', { name: 'I agree', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Analyze visits' })).toBeVisible();
});

test('a public page can be analysed and the report shows where it was read from', async ({ page }) => {
  const state = await mockCustomerApi(page);
  await page.goto('/account-dashboard?section=activity');
  await page.getByRole('button', { name: 'Posts to analyze' }).click();
  await page.getByRole('option', { name: 'A public page I enter', exact: true }).click();
  await expect(page.getByLabel('Address of the public page')).toHaveValue('http://collector:8100/demo-source/ordinary');
  await page.getByRole('button', { name: 'Analyze posts' }).click();
  await expect(page.getByText('Public page', { exact: true })).toBeVisible();
  await expect(page.getByText(/Read from http:\/\/collector:8100\/demo-source\/ordinary/)).toBeVisible();
  expect(state.requests.find(r => r.path.endsWith('/social-runs'))?.body).toEqual({ scenarioId: 'public-page', sourceUrl: 'http://collector:8100/demo-source/ordinary' });
});
