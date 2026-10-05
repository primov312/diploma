import { expect, type Page } from '@playwright/test';
import type { Application, FinancialInputs, MonthlyLivingCosts } from '../../src/api/types';

export async function mockCustomerApi(page: Page, signedIn = true) {
  let authenticated = signedIn;
  let user = { id: 1, email: 'customer@example.test', displayName: 'Alex Customer', createdAt: '2026-01-01T12:00:00Z' };
  const partners = [
    { id: 1, slug: 'streambox', displayName: 'Netflix', amountCap: 400, currency: 'USD' },
    { id: 2, slug: 'markethub', displayName: 'Amazon', amountCap: 2000, currency: 'USD' },
    { id: 3, slug: 'threadly', displayName: 'Zara', amountCap: 1000, currency: 'USD' },
  ];
  const products = [
    { id: 14, fixtureId: 'shirt', name: 'Linen shirt', description: 'An illustrative linen shirt for the demo catalog.', category: 'Clothing', imagePath: '/catalog/threadly-linen-shirt.jpg', imageAlt: 'Illustrative linen shirt', price: 59, currency: 'USD' },
    { id: 15, fixtureId: 'bag', name: 'Travel bag', description: 'An illustrative travel accessory.', category: 'Accessories', imagePath: '/catalog/threadly-weekender.jpg', imageAlt: 'Illustrative travel bag', price: 80, currency: 'USD' },
  ];
  let inputs: FinancialInputs = { revision: 1, monthlyNetIncome: 2500, housingSituation: 'RENTING', expenseMode: 'AUTOMATIC', housingCost: null, groceriesCost: null, utilitiesCost: null, transportCost: null, otherLivingCosts: null, legacyLivingExpenses: 600, monthlyObligations: 100, source: 'FIXTURE', updatedAt: '2026-10-01T12:00:00Z' };
  let address = { available: true, revision: 1, countryCode: 'HU', city: 'Budapest', districtId: 'V', postalCode: '1051', street: 'Minta utca', building: '12', unit: null, verificationStatus: 'UNVERIFIED', verifiedAt: null };
  const district = { postalCode: '1051', districtId: 'V', displayName: 'Budapest District V', city: 'Budapest', countryCode: 'HU', sourceUrl: 'https://www.posta.hu/', retrievedAt: '2026-10-01T12:00:00Z', datasetVersion: 'demo-2026' };
  let monthly: MonthlyLivingCosts = { available: true, eligible: false, currency: 'HUF', hufPerUsd: 360, profile: { revision: 1, datasetVersion: 'demo-2026', apartmentSize: 50, rentSharers: 1, groceryQuantities: { milk: 4 }, otherSpending: 10000, source: 'DEMO_DEFAULT' }, groceries: [{ id: 'milk', label: 'Milk', description: 'Demo price reference', unit: 'LITRE', packageQuantity: '1', packagePrice: '400', unitPrice: '400', sourceUrl: 'https://www.tesco.hu/', retrievedAt: '2026-10-01T12:00:00Z', observedAt: null }], district, housing: { observedAt: '2026-09-30', retrievedAt: '2026-10-01T12:00:00Z', sourceUrl: 'https://www.ksh.hu/' }, datasetVersion: 'demo-2026', coff: 1.1, rentPerM2: 7000, monthlyRent: 350000, monthlyGroceries: 30000, monthlyOther: 10000, monthlyTotal: 390000, rentApplies: true, suppliedExpenseFloorHuf: 216000 };
  let baseAmount = 900;
  let generation = 1;
  const applications: Application[] = [];
  const reports: Record<string, Record<string, unknown>[]> = { LOCATION: [], SOCIAL: [] };
  const requests: {path:string;body:Record<string, unknown>}[] = [];
  // Restrict to API URLs: a broad **/api/** pattern also matches Vite source modules.
  await page.route(/\/api\//, async route => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith('/api/')) return route.continue();
    const path = url.pathname;
    const method = route.request().method();
    const body = method !== 'GET' && route.request().headers()['content-type']?.includes('application/json') ? route.request().postDataJSON() : {};
    if (method !== 'GET') requests.push({path,body});
    const ok = (json:unknown, status = 200) => route.fulfill({status,json});
    if (path === '/api/csrf') return route.fulfill({json:{token:'demo',headerName:'X-XSRF-TOKEN'},headers:{'Set-Cookie':'XSRF-TOKEN=demo; Path=/'}});
    if (path === '/api/me') return authenticated ? ok(user) : ok({error:'UNAUTHENTICATED'},401);
    if (path === '/api/auth/register') { user = {...user,...body}; return ok(user,201); }
    if (path === '/api/auth/login') {
      if (body.password === 'wrong-password') return ok({error:'INVALID_CREDENTIALS',message:'Invalid email or password.'},401);
      authenticated = true; return ok(user);
    }
    if (path === '/api/auth/logout') {authenticated = false; return route.fulfill({status:204});}
    if (path === '/api/partners') return ok(partners);
    if (path.startsWith('/api/partners/')) return ok({...partners.find(p => p.slug === path.split('/').pop()),products});
    if (path === '/api/me/profile') return ok({monthlyIncome:2500,monthlyExpenses:600,monthlyObligations:100,profileComplete:false,emailVerified:true,syntheticSource:'FIXTURE',accountAgeMonths:9});
    if (path === '/api/me/financial-inputs') {
      if (method === 'PUT') {
        expect(body.expectedRevision).toBe(inputs.revision);
        expect(body.expenseMode).toBe('AUTOMATIC');
        inputs = {...inputs,housingSituation:body.housingSituation,revision:inputs.revision+1};
        baseAmount += 100; generation++;
        return ok({inputs,generation,recalculationJobId:generation});
      }
      return ok(inputs);
    }
    if (path === '/api/me/affordability') return ok({status:'READY',baseAmount,monthlyPaymentCapacity:baseAmount/6,generation,financialRevision:inputs.revision,formulaVersion:'affordability-v4',policyVersion:'rules-v4',currency:'USD',termMonths:6,stale:false,calculatedAt:'2026-10-05T10:00:00Z',breakdown:{monthlyNetIncome:2500,effectiveExpenses:600,monthlyObligations:100},partners:partners.map(p => ({slug:p.slug,cap:p.amountCap,possibleAmount:Math.min(baseAmount,p.amountCap)})),reasons:[]});
    if (path === '/api/me/affordability/history') return ok(Array.from({length:12},(_,i) => ({month:'2026-'+String(i+1).padStart(2,'0'),calculatedAt:'2026-10-05T10:00:00Z',amount:i===2?null:baseAmount-200+i*10,partnerAmounts:{threadly:baseAmount-200+i*10},formulaVersion:'affordability-v4',policyVersion:'rules-v4',dataSource:'FIXTURE'})).slice(-Number(url.searchParams.get('months'))));
    if (path === '/api/address/postal-code') return url.searchParams.get('postalCode') === '1007' ? ok({error:'UNSUPPORTED_POSTCODE',message:'This code is not a supported residential Budapest postcode.'},400) : ok({...district,postalCode:url.searchParams.get('postalCode')});
    if (path === '/api/me/address') {
      if(method === 'PUT') {
        expect(body.expectedRevision).toBe(address.revision);
        address = {...address,...body,revision:address.revision+1,verificationStatus:'UNVERIFIED'};
        baseAmount+=100;generation++;
        return ok({address,generation,recalculationJobId:generation});
      }
      return ok(address);
    }
    if(path === '/api/me/address/verifications') {address.verificationStatus='VERIFIED_DEMO';monthly.eligible=true;return ok({status:'VERIFIED_DEMO',checks:['Sample matched'],recalculationJobId:++generation});}
    if(path.startsWith('/api/me/monthly-living-costs')) {
      if(method !== 'GET') {
        const rent = inputs.housingSituation === 'RENTING' ? Number(body.apartmentSize)*7000/Number(body.rentSharers) : 0;
        const next = {...monthly,monthlyRent:rent,monthlyTotal:rent+40000,rentApplies:inputs.housingSituation==='RENTING',profile:{...monthly.profile!,apartmentSize:Number(body.apartmentSize),rentSharers:Number(body.rentSharers)}};
        if(method === 'PUT') { monthly=next; monthly.profile!.revision++;return ok({...monthly,recalculationJobId:++generation});}
        return ok(next);
      }
      return ok(monthly);
    }
    if(path === '/api/me/social-connection') return ok({configured:false,connected:false,provider:null,displayName:null,connectedAt:null,expiresAt:null});
    if(path === '/api/me/demo-signals/reports') return ok(reports[url.searchParams.get('kind')!]);
    if(path.endsWith('/location-runs') || path.endsWith('/social-runs')) {
      const kind = path.endsWith('/location-runs') ? 'LOCATION':'SOCIAL';
      const report = kind === 'LOCATION' ? {scenarioId:body.scenarioId,status:'COMPLETE',dataSource:'SYNTHETIC',visitCount:3,distinctDistricts:1,mostVisitedDistrict:'Budapest V',visits:[{id:'cafe',place:'Café',district:'Budapest V',arrival:'2026-10-01T12:00:00Z'}],limitation:'Synthetic visits do not prove purchases.'} : {scenarioId:body.scenarioId,status:'COMPLETE',analysisMode:'FIXTURE',provider:'FIXTURE',modelVersion:null,dataSource:'SYNTHETIC',summary:'Synthetic activity report ready.',metrics:{postCount:12},findings:[{label:'Consistent activity',observation:'Activity is consistent in this scenario.',confidence:'medium',evidenceIds:['post-1']}],evidence:[{id:'post-1',excerpt:'Synthetic example post.'}],limitations:['Synthetic analysis is illustrative.']};
      reports[kind]=[report];
      return ok({jobId:1,state:'SUCCEEDED',report},202);
    }
    if(path === '/api/transactions') return ok(partners.filter(p => !url.searchParams.get('partner') || p.slug === url.searchParams.get('partner')).map(p => ({id:p.id,partnerSlug:p.slug,partnerName:p.displayName,amount:59,currency:'USD',occurredOn:'2026-10-01',status:'COMPLETED',paidOnTime:true,description:'Synthetic catalog purchase'})));
    if(path === '/api/applications') {
      if(method === 'POST') {
        const p=partners.find(p => p.slug===body.partnerSlug)!;
        const product=products.find(p => p.id===body.productId);
        const a: Application = {id:applications.length+1,partnerSlug:p.slug,partnerName:p.displayName,productId:product?.id??null,productName:product?.name??null,requestedAmount:product?.price??body.requestedAmount,currency:'USD',decisionStatus:body.useAi?'REVIEW':'APPROVED',score:.7,possibleAmount:baseAmount,reasons:body.useAi?['PROFILE_INCOMPLETE']:[],factors:{profile:{score:.7,weight:1,reasons:body.useAi?['PROFILE_INCOMPLETE']:[],details:{}}},aiRequested:body.useAi,aiStatus:body.useAi?'APPLIED':'NOT_REQUESTED',policyVersion:'rules-v4',modelVersion:body.useAi?'logreg-v1':null,preparationMode:'PARALLEL',observedAt:'2026-10-05T10:00:00Z',createdAt:'2026-10-05T10:00:00Z'};
        applications.push(a);return ok(a,201);
      }
      return ok(applications);
    }
    if(path.startsWith('/api/applications/')) return ok(applications.find(a => a.id===Number(path.split('/').pop())) ?? {error:'NOT_FOUND'},applications.some(a => a.id===Number(path.split('/').pop())) ? 200:404);
    throw new Error('Unexpected mocked endpoint: '+method+' '+path);
  });
  return {requests};
}
