import { useEffect, useState } from 'react';
import { meApi } from '../../api/rocket';
import type { MonthlyCostDraft, MonthlyCostProfile, MonthlyLivingCosts as CostResult } from '../../api/types';
import { useApi, errorMessage } from '../../hooks/useApi';
import { formatHuf } from '../../utils/format';
import { Loading, Notice } from '../app/Ui';

const draftFrom = (profile: MonthlyCostProfile): MonthlyCostDraft => ({ apartmentSize: String(profile.apartmentSize), rentSharers: profile.rentSharers });
const unit = { KG: 'kg', LITRE: 'litres', ITEM: 'eggs' };
const date = (value: string) => new Date(value).toLocaleDateString('en-GB');

export function MonthlyLivingCosts({ postalCode, resolved, savedAddressMatches, refreshKey, onUpdated }: {
  postalCode: string; resolved: boolean; savedAddressMatches: boolean; refreshKey: string; onUpdated: () => void;
}) {
  const loaded = useApi(() => meApi.monthlyLivingCosts(), [refreshKey]);
  const [draft, setDraft] = useState<MonthlyCostDraft | null>(null);
  const [preview, setPreview] = useState<CostResult | null>(null);
  const [calculating, setCalculating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const current = loaded.status === 'ready' ? loaded.data : null;

  useEffect(() => {
    if (current?.profile) setDraft(draftFrom(current.profile));
  }, [current?.profile?.revision, current?.profile?.datasetVersion, refreshKey]);

  useEffect(() => {
    let cancelled = false;
    setPreview(null); setError(''); setCalculating(false);
    if (!draft || !resolved || !/^\d{4}$/.test(postalCode)) return;
    setCalculating(true);
    const timer = setTimeout(() => {
      meApi.previewMonthlyLivingCosts({ ...draft, postalCode })
        .then(result => { if (!cancelled) setPreview(result); })
        .catch(cause => { if (!cancelled) setError(errorMessage(cause)); })
        .finally(() => { if (!cancelled) setCalculating(false); });
    }, 250);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [draft, postalCode, resolved, current?.profile?.datasetVersion]);

  const save = async (refreshDataset = false) => {
    if (!draft || !current?.profile) return;
    setSaving(true); setError(''); setNotice('');
    try {
      const result = await meApi.saveMonthlyLivingCosts({ ...draft, expectedRevision: current.profile.revision, refreshDataset });
      setPreview(result); setDraft(result.profile ? draftFrom(result.profile) : draft);
      setNotice(refreshDataset ? 'Latest researched prices adopted. Your affordability estimate is recalculating.' : 'Housing details saved. Your automatic estimate is recalculating.');
      loaded.reload(); onUpdated();
    } catch (cause) { setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause)))); }
    finally { setSaving(false); }
  };

  const numberField = (label: string, value: string, change: (value: string) => void, min = 0, max = 1000, step = '0.01') =>
    <label className="block text-sm font-medium text-gray-700">{label}
      <input type="number" min={min} max={max} step={step} value={value} onChange={event => change(event.target.value)}
        className="mt-1 w-full rounded-lg min-h-11 border border-gray-400 bg-white px-3 py-2 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent" />
    </label>;

  const costs = preview?.available && preview.district?.postalCode === postalCode && resolved ? preview : null;
  const applied = savedAddressMatches && current?.eligible;
  const products = current?.groceries ?? [];
  return <aside className="min-w-0 border-t border-gray-200 pt-5 xl:border-l xl:border-t-0 xl:pl-6 xl:pt-0" aria-label="Monthly living costs">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 className="font-semibold text-gray-800">Monthly living costs</h3>
      <span className="rounded bg-primary-50 px-2 py-1 text-xs font-medium text-primary-900">Estimated · HUF / person</span>
    </div>
    <p className="mt-2 text-sm text-gray-600">Automatic rent, per-person groceries and an activity spending forecast from researched prices.</p>
    {loaded.status === 'loading' && <Loading label="Loading monthly cost references…" />}
    {loaded.status === 'error' && <Notice tone="error">{errorMessage(loaded.error)}</Notice>}
    {current?.unavailableReason === 'NO_DATASET' && <p className="mt-4 text-sm text-warning-700">Monthly price references are unavailable. Import the researched dataset to calculate costs.</p>}
    {draft && current?.profile && <>
      <div className="mt-5 grid gap-3 sm:grid-cols-2">
        {numberField('Apartment size (m²)', draft.apartmentSize, apartmentSize => setDraft({ ...draft, apartmentSize }), 0.01)}
        {numberField('People sharing rent', String(draft.rentSharers), value => setDraft({ ...draft, rentSharers: Number(value) }), 1, 20, '1')}
      </div>

      <details className="mt-4 border-y border-gray-200 py-3">
        <summary className="cursor-pointer text-sm font-medium text-primary-800">Monthly grocery basket · {products.length} products</summary>
        <p className="mt-2 text-xs text-gray-500">Prices are researched; per-person quantities are demo assumptions and are not divided between rent payers.</p>
        <dl className="mt-3 grid gap-2 sm:grid-cols-2">{products.map(product => <div key={product.id} className="flex justify-between gap-2 text-sm">
          <dt>{product.label}</dt><dd className="text-gray-600">{current.profile?.groceryQuantities[product.id]} {unit[product.unit]}</dd>
        </div>)}</dl>
      </details>
      <div className="mt-5" aria-live="polite" aria-busy={calculating}>
        {calculating ? <p className="text-sm text-gray-500">Calculating monthly estimate…</p> : costs ? <>
          <table className="w-full text-sm" data-testid="monthly-living-cost-table">
            <caption className="mb-2 text-left text-xs font-medium text-gray-500">{costs.district?.displayName} · {Number(costs.coff).toFixed(6)}× district coefficient</caption>
            <tbody>
              <tr className="border-b border-gray-100"><th scope="row" className="py-3 text-left font-normal text-gray-600">Rent benchmark{!costs.rentApplies && <span className="block text-xs">Excluded: you are not renting</span>}</th><td className="py-3 text-right font-medium">{formatHuf(costs.monthlyRent ?? 0)}</td></tr>
              <tr className="border-b border-gray-100"><th scope="row" className="py-3 text-left font-normal text-gray-600">Groceries</th><td className="py-3 text-right font-medium">{formatHuf(costs.monthlyGroceries ?? 0)}</td></tr>
              <tr className="border-b border-gray-100"><th scope="row" className="py-3 text-left font-normal text-gray-600">Activity spending forecast</th><td className="py-3 text-right font-medium">{formatHuf(costs.monthlyOther ?? 0)}</td></tr>
              <tr><th scope="row" className="py-4 text-left font-semibold">Monthly total</th><td className="py-4 text-right font-semibold text-primary-900">{formatHuf(costs.monthlyTotal ?? 0)}</td></tr>
            </tbody>
          </table>
          <details><summary>Budget calculation</summary><p className="text-xs text-gray-500">Rent: {formatHuf(costs.rentPerM2 ?? 0)} / m² × {draft.apartmentSize} m² ÷ {draft.rentSharers}. Food: unit prices × quantities × district coefficient. Activities use the forecast below.</p></details>
        </> : <p className="text-sm text-gray-500">{resolved ? 'A monthly estimate is unavailable for this postcode.' : 'Enter a supported Budapest postal code to see monthly costs.'}</p>}
      </div>
      {costs?.activityForecast?.available && <details className="mt-3 border-t border-gray-200 pt-3">
        <summary className="cursor-pointer text-sm font-medium text-primary-800">How activity spending is forecast</summary>
        <p className="mt-2 text-xs text-gray-600">Next 30 days. {costs.activityForecast.historySufficient ? 'Sufficient history: average of the monthly benchmark and projected visit spending.' : 'History is insufficient: monthly benchmark used.'} At least seven observed days and three priced gym/coffee visits are needed. Categories without visits retain their benchmark.</p>
        <dl className="mt-3 space-y-2 text-xs">{costs.activityForecast.items.map(item => <div key={item.category} className="flex flex-wrap justify-between gap-2">
          <dt>{item.category === 'CAFE' ? 'Café' : item.category === 'LIBRARY' ? 'Library' : item.category === 'GYM' ? 'Gym' : 'Starbucks'}</dt>
          <dd>{formatHuf(item.baselineMonthly)} benchmark{item.historyMonthly != null && <> + {formatHuf(item.historyMonthly)} projected, divided by 2</>} → {formatHuf(item.monthlyAmount)}</dd>
        </div>)}</dl>
        <p className="mt-3 text-xs text-gray-500">Demo assumptions: 4 gym entries, 8 café drinks and 4 Starbucks drinks per month, plus one annual library membership divided by 12. Synthetic visits estimate spending; they do not prove purchases. Grocery visits are excluded because the food basket already covers groceries. This benchmark budget excludes utilities and transport.</p>
      </details>}
      <div className="mt-4 flex flex-wrap gap-2">
        <button type="button" disabled={saving || calculating || !costs || !savedAddressMatches} onClick={() => save()} className="btn-secondary px-3 py-2 text-sm disabled:opacity-50">{saving ? 'Saving…' : 'Save size and sharing'}</button>
        <button type="button" disabled={saving || calculating || !resolved || !savedAddressMatches} onClick={() => save(true)} className="px-2 py-2 text-sm font-medium text-primary-800 underline underline-offset-4 disabled:opacity-50">Refresh cost estimates</button>
      </div>
      <p className="mt-2 text-xs text-gray-600">{!savedAddressMatches ? 'Save this address before saving housing details.' : applied ? 'Saved automatic costs apply to affordability; the supplied expense baseline remains a minimum.' : 'Verify your address to apply researched costs; until then, affordability uses the supplied expense baseline.'} Unsaved edits are preview only.</p>
      <details className="mt-4 border-t border-gray-200 pt-3">
        <summary className="cursor-pointer text-sm font-medium text-gray-700">Sources and calculation dates</summary>
        <div className="mt-3 space-y-2 break-words text-xs text-gray-600">
      {current.suppliedExpenseFloorHuf != null && <p className="mt-2 text-xs text-gray-500">Supplied demo expense baseline: {formatHuf(current.suppliedExpenseFloorHuf)} / month. Credit calculations retain the greater of this baseline and the verified budget.</p>}
      <p className="mt-2 text-xs text-gray-500">Fixed demo conversion for affordability: 1 USD = {current.hufPerUsd} HUF.</p>

          {current.housing && <p><a href={current.housing.sourceUrl} target="_blank" rel="noreferrer" className="text-primary-800 underline">District rental statistics</a> · observed {current.housing.observedAt} · retrieved {date(current.housing.retrievedAt)}</p>}
          {costs?.district && <p><a href={costs.district.sourceUrl} target="_blank" rel="noreferrer" className="text-primary-800 underline">Magyar Posta postcode mapping</a> · retrieved {date(costs.district.retrievedAt)}</p>}
          <ul className="space-y-2">{products.map(product => <li key={product.id}><a href={product.sourceUrl} target="_blank" rel="noreferrer" className="text-primary-800 underline">{product.label}: {product.description}</a> · {formatHuf(Number(product.packagePrice))} / {product.packageQuantity} {unit[product.unit]} · retrieved {date(product.retrievedAt)}</li>)}</ul>
          {costs?.activityForecast?.items.map(item => <p key={item.category}><a href={item.sourceUrl} target="_blank" rel="noreferrer" className="text-primary-800 underline">{item.category} benchmark</a> · retrieved {date(item.retrievedAt)}</p>)}
          <p>Dataset: {current.datasetVersion}. Grocery quantities: fixed demo defaults. Housing details: {current.profile.source === 'DEMO_DEFAULT' ? 'demo defaults' : 'user supplied'}.</p>
        </div>
      </details>
    </>}
    {error && <div className="mt-3"><Notice tone="error">{error}</Notice></div>}
    {notice && <div className="mt-3"><Notice>{notice}</Notice></div>}
  </aside>;
}
