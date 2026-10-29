// AI-assisted (Claude), 2026; reviewed by the author.
import { expect, type Page } from '@playwright/test';

export const DEMO_PASSWORD = 'rocket-demo-123';

/** Screenshots for the thesis land in docs/screenshots/ when SCREENSHOTS=1. */
export const shot = async (page: Page, name: string) => {
  if (process.env.SCREENSHOTS !== '1') return;
  await page.evaluate(() => window.scrollTo(0, 0)); // the sticky header would otherwise be drawn mid-page
  await page.screenshot({ path: `../docs/screenshots/${name}.png`, fullPage: true });
};

export async function signIn(page: Page, email: string, landing: RegExp) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(DEMO_PASSWORD);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(landing);
}

export async function signOut(page: Page) {
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/$/);
}

/** Submits the apply form on the review step, the same way smoke.spec.ts does. */
export async function applyAsRiley(page: Page) {
  await page.goto('/apply?partner=markethub&amount=18000');
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByLabel('Number of payments').selectOption('6');
  await page.getByRole('button', { name: 'See my payments' }).click();
  for (const name of [/I agree: Processing my data/, /I agree: AI-assisted analysis/]) {
    const box = page.getByLabel(name);
    if (await box.count()) await box.check();
  }
  await page.getByRole('button', { name: 'Get my decision' }).click();
  await expect(page).toHaveURL(/\/applications\/\d+/);
  await expect(page.getByText('Needs review').first()).toBeVisible();
  return Number(page.url().match(/applications\/(\d+)/)![1]);
}
