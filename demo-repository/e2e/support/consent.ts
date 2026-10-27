// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
import { expect, type Page } from '@playwright/test';

/** On the apply review step: tick whichever consent boxes are shown. Already-agreed consents show no box. */
export async function agreeIfAsked(page: Page) {
  for (const name of [/I agree: Processing my data/, /I agree: AI-assisted analysis/]) {
    const box = page.getByLabel(name);
    if (await box.count()) await box.check();
  }
}

/** On the Activity tab: agree to digital footprint analysis when the consent card is shown. */
export async function agreeToFootprint(page: Page) {
  const agree = page.getByRole('button', { name: 'I agree', exact: true });
  const analyze = page.getByRole('button', { name: 'Analyze visits' });
  await expect(agree.or(analyze)).toBeVisible();
  if (await agree.isVisible()) await agree.click();
}
