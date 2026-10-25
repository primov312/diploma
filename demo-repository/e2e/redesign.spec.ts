import { expect, test, type Page } from '@playwright/test';
import { mockCustomerApi } from './support/redesignFixture';
import { formatCurrency } from '../src/utils/format';

async function choose(page:Page,label:string,option:string) {
  await page.getByLabel(label,{exact:true}).click();
  await page.getByRole('option',{name:option,exact:true}).click();
}
async function reviewTerms(page:Page) {
  await page.getByRole('button',{name:'Continue'}).click();
  await page.getByLabel('Number of payments').selectOption('6');
  await page.getByRole('button',{name:'See my payments'}).click();
}
const tabs = [
  ['budget','Budget','Income and housing'],
  ['activity','Activity','Where you spend time'],
] as const;

test('authentication, marketplace handoff, submission and saved decisions keep working',async ({page}) => {
  const state = await mockCustomerApi(page,false);
  await page.goto('/apply?product=14');
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole('link',{name:'Create an account'}).click();
  await page.getByLabel('Name').fill('Alex Customer');
  await page.getByLabel('Email').fill('customer@example.test');
  await page.getByLabel('Password').fill('password-123');
  await page.getByRole('button',{name:'Create account'}).click();
  await expect(page).toHaveURL(/\/apply\?product=14$/);
  await expect(page.getByText('Linen shirt')).toBeVisible();
  await expect(page.getByText(/Zara · 21\s240\s*Ft/)).toBeVisible();
  await reviewTerms(page);
  await page.getByRole('button',{name:'Get my decision'}).click();
  await expect(page).toHaveURL(/\/applications\/1$/);
  await expect(page.getByText('Approved',{exact:true})).toBeVisible();
  await page.getByText('Technical details',{exact:true}).click();
  await expect(page.getByText(/policy rules-v4/)).toBeVisible();
  await page.reload();
  await expect(page.getByText('Approved',{exact:true})).toBeVisible();
  await page.goto('/applications');
  await expect(page.getByRole('link',{name:/Linen shirt/})).toBeVisible();
  await page.goto('/history');
  await expect(page.getByRole('row')).toHaveCount(4);
  await choose(page,'Seller','Zara');
  await expect(page.getByRole('row')).toHaveCount(2);

  // One marketplace: search, departments, and a product search inside the form.
  await page.goto('/marketplace');
  await page.getByPlaceholder('Search products or sellers').fill('linen');
  await expect(page.getByRole('link',{name:'Apply for credit'})).toHaveCount(1);
  await page.getByPlaceholder('Search products or sellers').fill('nothing like this');
  await expect(page.getByText('No products match. Try another word or category.')).toBeVisible();
  await page.goto('/stores/threadly');
  await expect(page).toHaveURL(/\/marketplace$/);
  await page.goto('/apply');
  await page.getByLabel('Find a product').fill('travel');
  await page.getByRole('button',{name:/Travel bag/}).click();
  await expect(page.getByRole('button',{name:'Change'})).toBeVisible();
  await reviewTerms(page);
  await page.getByLabel(/Also use AI analysis/).check();
  await page.getByRole('button',{name:'Get my decision'}).click();
  await expect(page.getByText('Needs review',{exact:true})).toBeVisible();
  await expect(page.getByText('Your profile is not complete.').first()).toBeVisible();
  await page.getByText('Technical details',{exact:true}).click();
  await expect(page.getByText(/model logreg-v1/)).toBeVisible();
  expect(state.requests.filter(r => r.path==='/api/applications').map(r=>r.body)).toEqual([{partnerSlug:'threadly',productId:14,useAi:false,quoteId:1},{partnerSlug:'threadly',productId:15,useAi:true,quoteId:2}]);

  await page.goto('/apply?partner=markethub');
  await page.getByRole('button',{name:'Another amount'}).click();
  await page.getByRole('button',{name:'Continue'}).click();
  await expect(page.getByText('Enter an amount greater than zero.')).toBeVisible();
  await page.getByLabel('Amount (HUF)').fill('43200');
  await reviewTerms(page);
  await page.getByRole('button',{name:'Get my decision'}).click();
  await expect(page).toHaveURL(/applications\/3$/);
  expect(state.requests.filter(r=>r.path==='/api/applications').at(-1)?.body).toEqual({partnerSlug:'markethub',requestedAmount:120,useAi:false,quoteId:3});
  await page.getByRole('button',{name:'Sign out'}).click();
  await page.goto('/account-dashboard');
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('Email').fill('customer@example.test');
  await page.getByLabel('Password').fill('wrong-password');
  await page.getByRole('button',{name:'Sign in'}).click();
  await expect(page.getByRole('alert')).toHaveText('Invalid email or password.');
  await page.getByLabel('Password').fill('password-123');
  await page.getByRole('button',{name:'Sign in'}).click();
  await expect(page).toHaveURL(/\/account-dashboard$/);
});

test('dashboard tabs, saves, analyses with the district map and Facebook return',async ({page}) => {
  await mockCustomerApi(page);
  await page.goto('/account-dashboard');
  const nav=page.getByRole('navigation',{name:'Dashboard sections'});
  for(const [id,label,heading] of tabs) {
    await nav.getByRole('link',{name:label,exact:true}).click();
    await expect(page).toHaveURL('/account-dashboard?section='+id);
    await expect(nav.getByRole('link',{name:label,exact:true})).toHaveAttribute('aria-current','page');
    await expect(page.getByRole('heading',{name:heading,exact:true})).toBeVisible();
    await expect(page.getByRole('heading',{name:'What you can afford'})).toHaveCount(0);
  }
  await page.goBack(); await expect(page).toHaveURL(/section=budget/);
  await page.goto('/account-dashboard?section=unknown');
  await expect(page.getByRole('heading',{name:'What you can afford'})).toBeVisible();
  await nav.getByRole('link',{name:'Budget',exact:true}).click();
  const income=page.locator('section',{has:page.getByRole('heading',{name:'Income and housing'})});
  await expect(income.getByRole('button')).toHaveCount(0);
  await expect(page.getByLabel('New housing situation',{exact:true})).toHaveCount(0);
  await page.getByRole('region',{name:'Housing situation'}).getByRole('button',{name:'Edit',exact:true}).click();
  await choose(page,'New housing situation','Owner');
  await expect(page.getByText(/upload an ownership certificate/)).toBeVisible();
  await page.getByRole('button',{name:'Use the sample document'}).click();
  await expect(page.getByText('Housing situation changed to Owner. Your amount is being recalculated.')).toBeVisible();
  await expect(income.getByText('Owner',{exact:true})).toBeVisible();
  await nav.getByRole('link',{name:'Overview',exact:true}).click();
  await expect(page.locator('#dashboard-content').getByText(formatCurrency(1000),{exact:true}).first()).toBeVisible();
  await nav.getByRole('link',{name:'Budget',exact:true}).click();
  await page.getByLabel('Postal code',{exact:true}).fill('1007');
  await expect(page.getByText('This code is not a supported residential Budapest postcode.')).toBeVisible();
  await expect(page.getByRole('button',{name:'Save address',exact:true})).toBeDisabled();
  await page.getByLabel('Postal code',{exact:true}).fill('1051');
  await expect(page.locator('#district-hint')).toHaveText('Budapest District V');
  await page.getByLabel('Street',{exact:true}).fill('Updated street');
  await page.getByRole('button',{name:'Save address',exact:true}).click();
  await expect(page.getByText('Address saved. Verify it below to use local prices.')).toBeVisible();
  await page.getByLabel('People sharing rent',{exact:true}).fill('2');
  await page.getByRole('complementary',{name:'Monthly living costs'}).getByRole('button',{name:'Save',exact:true}).click();
  await expect(page.getByText('Saved. Your amount is being recalculated.')).toBeVisible();
  await page.reload(); await expect(page.getByLabel('People sharing rent',{exact:true})).toHaveValue('2');
  await page.getByText('Sources and calculation dates',{exact:true}).click();
  await expect(page.getByRole('link',{name:'District rental statistics'})).toBeVisible();
  await page.getByRole('button',{name:'Use the sample card'}).click();
  await expect(page.getByText(/VERIFIED DEMO/)).toBeVisible();
  await nav.getByRole('link',{name:'Overview',exact:true}).click();
  await expect(page.locator('#dashboard-content').getByText(formatCurrency(1100),{exact:true}).first()).toBeVisible();
  await nav.getByRole('link',{name:'Activity',exact:true}).click();
  await expect(page.getByRole('checkbox')).toHaveCount(0);
  await page.getByRole('button',{name:'Analyze visits'}).click();
  await expect(page.getByRole('button',{name:/District V: 1 visit · most visited · your address/})).toBeVisible();
  await expect(page.getByText('Matches your address').or(page.getByText('Not enough visits yet'))).toHaveCount(0);
  await page.getByText(/^All visits \(1\)$/).click();
  await expect(page.getByText('Café · Budapest V',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Analyze posts'}).click();
  await expect(page.getByText('Synthetic activity report ready.')).toBeVisible();
  await page.getByText('Evidence (1)',{exact:true}).click();
  await expect(page.getByText('Synthetic example post.',{exact:false})).toBeVisible();
  await page.reload(); await expect(page.getByText('Synthetic activity report ready.')).toBeVisible();
  await nav.getByRole('link',{name:'Overview',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Where you spend time'})).toBeVisible();
  await expect(page.locator('#dashboard-content [aria-label^="District V: 1 visit · most visited"]')).toHaveCount(1);
  await page.goto('/account-dashboard?social=connected');
  await expect(page.getByText('Facebook connected. You can analyze your posts now.',{exact:false})).toBeVisible();
  await page.getByRole('button',{name:'Dismiss'}).click();
  await expect(page.getByRole('heading',{name:'Social activity'})).toBeVisible();
});

test('full width responsive pages, compact navigation and expandable explanations',async ({page},testInfo) => {
  await mockCustomerApi(page);
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('/apply?product=14');
  await reviewTerms(page);
  await page.getByRole('button',{name:'Get my decision'}).click();
  await expect(page).toHaveURL(/applications\/1$/);
  const routes=['/','/how-it-works','/for-businesses','/marketplace','/login','/register','/account-dashboard',...tabs.map(s=>'/account-dashboard?section='+s[0]),'/history','/balance','/repayments','/apply','/applications','/applications/1','/catalog/credits.html'];
  for(const width of [390,768,1280,1920]) {
    await page.setViewportSize({width,height:900});
    for(const route of routes) {
      await page.goto(route);
      await expect(page.getByRole('heading',{level:1})).toBeVisible();
      await expect(page.getByRole('status').filter({hasText:/Loading/})).toHaveCount(0);
      await page.evaluate(()=>document.fonts.ready);
      expect(await page.evaluate(()=>getComputedStyle(document.body).fontFamily)).toContain('Source Sans 3');
      if(route.includes('section=budget')) await expect(page.getByTestId('monthly-living-cost-table')).toBeVisible();
      expect(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth),route+' at '+width).toBe(true);
      if(route!=='/catalog/credits.html') {
        await expect(page.getByRole('contentinfo')).toContainText('simulated money');
        const shell=page.locator('.page-shell').first();
        await expect(shell).toBeVisible();
        const box=await shell.boundingBox();expect(box!.width).toBe(width);
      }
      if(['/', '/login','/marketplace','/account-dashboard','/account-dashboard?section=budget','/applications/1'].includes(route)) await page.screenshot({path:testInfo.outputPath(route.replace(/[^a-z]/gi,'-')+'-'+width+'.png'),fullPage:true});
    }
    if(width<1024) {
      await page.goto('/marketplace');
      const toggle=page.getByRole('button',{name:'Toggle navigation menu'});
      await toggle.focus();await page.keyboard.press('Enter');
      const mobile=page.getByRole('navigation',{name:'Mobile navigation'});
      for(const label of ['Marketplace','Dashboard','Applications','Wallet']) await expect(mobile.getByRole('link',{name:label,exact:true})).toBeVisible();
      await page.keyboard.press('Escape');await expect(toggle).toHaveAttribute('aria-expanded','false');
    }
  }
  await page.goto('/repayments');
  const wallet=page.getByRole('navigation',{name:'Wallet'});
  await expect(wallet.getByRole('link',{name:'Repayments'})).toHaveAttribute('aria-current','page');
  await wallet.getByRole('link',{name:'Activity'}).click();
  await expect(page).toHaveURL(/\/history$/);
  await page.goto('/about-us');await expect(page).toHaveURL('/');
  await page.goto('/how-it-works');
  const summary=page.getByText('Why was I not approved?',{exact:true});
  await summary.focus();await page.keyboard.press('Enter');
  await expect(page.getByText('Every decision lists its reasons',{exact:false})).toBeVisible();
  await page.goto('/');await page.keyboard.press('Tab');
  await expect(page.getByRole('link',{name:'Skip to content'})).toBeFocused();
  await page.keyboard.press('Enter');await expect(page.locator('#main-content')).toBeFocused();
  expect(errors).toEqual([]);
});

test.describe('200 percent browser zoom equivalent', () => {
  // A 1280px viewport at 200% zoom has a 640px CSS viewport and 2x pixel scale.
  test.use({ viewport: { width:640, height:450 }, deviceScaleFactor:2 });
  test('preserves navigation, inputs and content without page overflow',async ({page},testInfo) => {
  await mockCustomerApi(page);
  for(const route of ['/','/login','/marketplace','/account-dashboard?section=budget','/account-dashboard?section=activity','/apply','/history']) {
    await page.goto(route);
    await expect(page.getByRole('heading',{level:1})).toBeVisible();
    await expect(page.getByRole('status').filter({hasText:/Loading/})).toHaveCount(0);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth),route).toBe(true);
    await expect(page.getByRole('button',{name:'Toggle navigation menu'})).toBeVisible();
  }
  await page.screenshot({path:testInfo.outputPath('zoom-200.png'),fullPage:true});
});
});
