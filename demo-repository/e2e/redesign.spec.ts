import { expect, test, type Page } from '@playwright/test';
import { mockCustomerApi } from './support/redesignFixture';
import { formatCurrency } from '../src/utils/format';

async function choose(page:Page,label:string,option:string) {
  await page.getByLabel(label,{exact:true}).click();
  await page.getByRole('option',{name:option,exact:true}).click();
}
const sections = [
  ['financial','Finances','Financial information'],
  ['address','Address & costs','Living address and local cost context'],
  ['activity','Activity','Activity analysis'],
  ['profile','Profile','Account profile'],
  ['purchases','Purchases','Purchase history by store'],
  ['applications','Applications','Credit applications'],
] as const;

test('authentication, store handoff, submission and saved decisions keep working',async ({page}) => {
  const state = await mockCustomerApi(page,false);
  await page.goto('/apply?partner=threadly&product=14');
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole('link',{name:'Create an account'}).click();
  await page.getByLabel('Name').fill('Alex Customer');
  await page.getByLabel('Email').fill('customer@example.test');
  await page.getByLabel('Password').fill('password-123');
  await page.getByRole('button',{name:'Create account'}).click();
  await expect(page).toHaveURL(/\/apply\?partner=threadly&product=14$/);
  await expect(page.getByLabel('Amount (HUF)')).toHaveValue('21240');
  await expect(page.getByLabel('Amount (HUF)')).toBeDisabled();
  await page.getByRole('button',{name:'Submit request'}).click();
  await expect(page).toHaveURL(/\/applications\/1$/);
  await expect(page.getByText('Approved',{exact:true})).toBeVisible();
  await page.getByText('Decision details',{exact:true}).click();
  await expect(page.getByText(/policy rules-v4/)).toBeVisible();
  await page.reload();
  await expect(page.getByText('Approved',{exact:true})).toBeVisible();
  await page.goto('/applications');
  await expect(page.getByRole('link',{name:/Linen shirt/})).toBeVisible();
  await page.goto('/history');
  await expect(page.getByRole('row')).toHaveCount(4);
  await choose(page,'Store','Zara');
  await expect(page.getByRole('row')).toHaveCount(2);
  await page.goto('/stores/threadly');
  await page.getByLabel('Search products').fill('linen');
  await expect(page.getByRole('link',{name:'Apply with Rocket Credit'})).toHaveCount(1);
  await page.getByRole('button',{name:'Accessories',exact:true}).click();
  await expect(page.getByText('No entries match these filters.')).toBeVisible();
  await page.getByLabel('Search products').fill('');
  await page.getByRole('link',{name:'Apply with Rocket Credit'}).click();
  await expect(page).toHaveURL(/product=15/);
  await page.getByLabel(/Use AI analysis/).check();
  await page.getByRole('button',{name:'Submit request'}).click();
  await expect(page.getByText('Needs review',{exact:true})).toBeVisible();
  await expect(page.getByText('Your profile is not complete.').first()).toBeVisible();
  await page.getByText('Decision details',{exact:true}).click();
  await expect(page.getByText(/model logreg-v1/)).toBeVisible();
  expect(state.requests.filter(r => r.path==='/api/applications').map(r=>r.body)).toEqual([{partnerSlug:'threadly',productId:14,useAi:false},{partnerSlug:'threadly',productId:15,useAi:true}]);
  await page.goto('/apply?partner=markethub');
  await page.getByRole('button',{name:'Submit request'}).click();
  await expect(page.getByText('Enter an amount greater than zero.')).toBeVisible();
  await page.getByLabel('Amount (HUF)').fill('43200');
  await page.getByRole('button',{name:'Submit request'}).click();
  await expect(page).toHaveURL(/applications\/3$/);
  expect(state.requests.filter(r=>r.path==='/api/applications').at(-1)?.body).toEqual({partnerSlug:'markethub',requestedAmount:120,useAi:false});
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

test('all dashboard sections, saves, enabled analyses and Facebook return',async ({page}) => {
  await mockCustomerApi(page);
  await page.goto('/account-dashboard');
  const nav=page.getByRole('navigation',{name:'Dashboard sections'});
  for(const [id,label,heading] of sections) {
    await nav.getByRole('link',{name:label,exact:true}).click();
    await expect(page).toHaveURL('/account-dashboard?section='+id);
    await expect(nav.getByRole('link',{name:label,exact:true})).toHaveAttribute('aria-current','page');
    await expect(page.getByRole('heading',{name:heading,exact:true})).toBeVisible();
    await expect(page.getByRole('heading',{name:'Estimated affordable amount'})).toHaveCount(0);
  }
  await page.goBack(); await expect(page).toHaveURL(/section=purchases/);
  await page.goForward(); await expect(page).toHaveURL(/section=applications/);
  await page.reload(); await expect(page.getByRole('heading',{name:'Credit applications'})).toBeVisible();
  await page.goto('/account-dashboard?section=unknown');
  await expect(page.getByRole('heading',{name:'Estimated affordable amount'})).toBeVisible();
  await nav.getByRole('link',{name:'Finances',exact:true}).click();
  await choose(page,'Housing situation','Owner');
  await page.getByRole('button',{name:'Save housing situation'}).click();
  await expect(page.getByText('Housing situation saved. Automatic costs are recalculating.')).toBeVisible();
  await nav.getByRole('link',{name:'Overview',exact:true}).click();
  await expect(page.locator('#dashboard-content').getByText(formatCurrency(1000),{exact:true}).first()).toBeVisible();
  await nav.getByRole('link',{name:'Address & costs',exact:true}).click();
  await page.getByLabel('Postal code',{exact:true}).fill('1007');
  await expect(page.getByText('This code is not a supported residential Budapest postcode.')).toBeVisible();
  await expect(page.getByRole('button',{name:'Save address',exact:true})).toBeDisabled();
  await page.getByLabel('Postal code',{exact:true}).fill('1051');
  await expect(page.getByLabel('District determined by postal code')).toHaveValue('Budapest District V');
  await page.getByLabel('Street',{exact:true}).fill('Updated street');
  await page.getByRole('button',{name:'Save address',exact:true}).click();
  await expect(page.getByText('Address saved. Verification is reset and the estimate is recalculating without district references.')).toBeVisible();
  await page.getByLabel('People sharing rent',{exact:true}).fill('2');
  await page.getByRole('button',{name:'Save size and sharing',exact:true}).click();
  await expect(page.getByText('Housing details saved. Your automatic estimate is recalculating.')).toBeVisible();
  await page.reload(); await expect(page.getByLabel('People sharing rent',{exact:true})).toHaveValue('2');
  await page.getByText('Sources and calculation dates',{exact:true}).click();
  await expect(page.getByRole('link',{name:'District rental statistics'})).toBeVisible();
  await page.getByRole('button',{name:'Review prepared sample'}).click();
  await expect(page.getByText(/VERIFIED DEMO/)).toBeVisible();
  await nav.getByRole('link',{name:'Overview',exact:true}).click();
  await expect(page.locator('#dashboard-content').getByText(formatCurrency(1100),{exact:true}).first()).toBeVisible();
  await nav.getByRole('link',{name:'Activity',exact:true}).click();
  await expect(page.getByRole('checkbox')).toHaveCount(0);
  await page.getByRole('button',{name:'Run location analysis'}).click();
  await expect(page.getByText('Café · Budapest V',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Run social analysis'}).click();
  await expect(page.getByText('Synthetic activity report ready.')).toBeVisible();
  await page.getByText('Evidence and confidence',{exact:true}).click();
  await expect(page.getByText('Synthetic example post.',{exact:false})).toBeVisible();
  await page.reload(); await expect(page.getByText('Synthetic activity report ready.')).toBeVisible();
  await page.goto('/account-dashboard?social=connected');
  await expect(page.getByText('Facebook account connected. You can now analyze it.',{exact:false})).toBeVisible();
  await page.getByRole('button',{name:'Dismiss'}).click();
  await expect(page).toHaveURL('/account-dashboard?section=activity');
  await expect(page.getByRole('heading',{name:'Activity analysis'})).toBeVisible();
});

test('full width responsive pages, complete navigation and expandable explanations',async ({page},testInfo) => {
  await mockCustomerApi(page);
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('/apply?partner=threadly&product=14');
  await page.getByRole('button',{name:'Submit request'}).click();
  await expect(page).toHaveURL(/applications\/1$/);
  const routes=['/','/how-it-works','/for-businesses','/stores','/stores/threadly','/stores/markethub','/stores/streambox','/login','/register','/account-dashboard',...sections.map(s=>'/account-dashboard?section='+s[0]),'/history','/apply','/applications','/applications/1','/catalog/credits.html'];
  for(const width of [390,768,1280,1920]) {
    await page.setViewportSize({width,height:900});
    for(const route of routes) {
      await page.goto(route);
      await expect(page.getByRole('heading',{level:1})).toBeVisible();
      await expect(page.getByRole('status').filter({hasText:/Loading/})).toHaveCount(0);
      await page.evaluate(()=>document.fonts.ready);
      expect(await page.evaluate(()=>getComputedStyle(document.body).fontFamily)).toContain('Source Sans 3');
      if(route.includes('section=address')) await expect(page.getByTestId('monthly-living-cost-table')).toBeVisible();
      expect(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth),route+' at '+width).toBe(true);
      await expect(page.locator('footer')).toHaveCount(0);
      await expect(page.getByRole('link',{name:'About Us'})).toHaveCount(0);
      const shell=page.locator('.page-shell').first();
      await expect(shell).toBeVisible();
      const box=await shell.boundingBox();expect(box!.width).toBe(width);
      if(['/', '/login','/stores/threadly','/account-dashboard','/account-dashboard?section=address','/applications/1'].includes(route)) await page.screenshot({path:testInfo.outputPath(route.replace(/[^a-z]/gi,'-')+'-'+width+'.png'),fullPage:true});
    }
    await page.goto('/account-dashboard?section=profile');
    if(width<1024) {
      await page.getByRole('combobox',{name:'Dashboard section'}).selectOption('financial');
      await expect(page.getByRole('heading',{name:'Financial information'})).toBeVisible();
    }
    if(width<1280) {
      const toggle=page.getByRole('button',{name:'Toggle navigation menu'});
      await toggle.focus();await page.keyboard.press('Enter');
      const mobile=page.getByRole('navigation',{name:'Mobile navigation'});
      for(const label of ['Stores','How it works','For businesses','Dashboard','History','Applications']) await expect(mobile.getByRole('link',{name:label,exact:true})).toBeVisible();
      await page.keyboard.press('Escape');await expect(toggle).toHaveAttribute('aria-expanded','false');
    }
  }
  await page.goto('/about-us');await expect(page).toHaveURL('/');
  await page.goto('/how-it-works');
  const summary=page.getByText('Where is my decision saved?',{exact:true});
  await summary.focus();await page.keyboard.press('Enter');
  await expect(page.getByText('Applications lists your requests.',{exact:false})).toBeVisible();
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
  for(const route of ['/','/login','/stores/threadly','/account-dashboard?section=address','/account-dashboard?section=activity','/apply','/history']) {
    await page.goto(route);
    await expect(page.getByRole('heading',{level:1})).toBeVisible();
    await expect(page.getByRole('status').filter({hasText:/Loading/})).toHaveCount(0);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth),route).toBe(true);
    await expect(page.getByRole('button',{name:'Toggle navigation menu'})).toBeVisible();
  }
  await page.screenshot({path:testInfo.outputPath('zoom-200.png'),fullPage:true});
});
});
