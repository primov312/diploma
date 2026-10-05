import type { LocationReportPricing, LocationVisitPricing } from '../../api/types';

const huf = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'HUF', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const units: Record<LocationVisitPricing['unit'], string> = { BASKET: 'basket', YEAR: 'year', ENTRY: 'entry', DRINK: 'drink' };
const displayDate = (date?: string) => date ? date.slice(0, 10) : 'Not published';
const safeSource = (url: unknown) => typeof url === 'string' && /^https:\/\//.test(url) ? url : undefined;

function visitPricing(value: unknown): LocationVisitPricing | null {
  if (!value || typeof value !== 'object') return null;
  const price = value as LocationVisitPricing;
  return price.currency === 'HUF' && price.unit in units && Number.isFinite(price.estimatedPrice)
    && Number.isFinite(price.baselinePrice) && Number.isFinite(price.coff) ? price : null;
}

export function LocationPricingSummary({ value }: { value: unknown }) {
  if (!value || typeof value !== 'object') return <p className="text-xs text-gray-500">Run location analysis again to attach benchmark prices to this saved report.</p>;
  const pricing = value as LocationReportPricing;
  return <div className="rounded-lg border border-primary-100 bg-primary-50/50 p-3 text-sm">
    <h4 className="font-semibold text-primary-900">Estimated benchmark prices</h4>
    <p className="mt-1 text-xs text-gray-600">Synthetic visits with researched HUF references. Each benchmark is scaled by the district housing coefficient.</p>
    {!pricing.available ? <p className="mt-2 text-xs text-gray-600">Price references are not available for this report. Import a dataset and run analysis again.</p> : <details className="mt-2 text-xs text-gray-600">
      <summary className="cursor-pointer font-medium text-primary-900">Housing reference · {displayDate(pricing.housingObservedAt)}</summary>
      <dl className="mt-2 space-y-1 break-words">
        <div><dt className="inline font-medium">Dataset: </dt><dd className="inline">{pricing.datasetVersion}</dd></div>
        <div><dt className="inline font-medium">Average district rent: </dt><dd className="inline">{typeof pricing.districtAverage === 'number' ? huf.format(pricing.districtAverage) : '—'} / m² / month</dd></div>
        <div><dt className="inline font-medium">Housing retrieved: </dt><dd className="inline">{displayDate(pricing.housingRetrievedAt)}</dd></div>
        <div><dt className="inline font-medium">Method: </dt><dd className="inline">{pricing.method}</dd></div>
      </dl>
      {safeSource(pricing.sourceUrl) && <a href={safeSource(pricing.sourceUrl)} target="_blank" rel="noreferrer" className="mt-2 inline-block underline">District rental statistics</a>}
    </details>}
  </div>;
}

export function LocationVisitBenchmark({ value, status }: { value: unknown; status: unknown }) {
  const price = visitPricing(value);
  if (!price) {
    return <p className="mt-1 text-xs text-gray-500">{status === 'UNSUPPORTED_PLACE' ? 'No benchmark for this place type' : 'Price unavailable'}</p>;
  }
  return <div className="mt-1 text-xs" data-testid="location-visit-price">
    <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
      <strong className="text-primary-900">≈ {huf.format(price.estimatedPrice)} / {units[price.unit]}</strong>
      <span className="text-gray-500">District coefficient {price.coff.toFixed(2)}×</span>
    </div>
    {price.unit === 'YEAR' && <p className="mt-1 text-gray-500">Annual membership benchmark</p>}
    <details className="mt-1 text-gray-500">
      <summary className="cursor-pointer">Benchmark source and calculation</summary>
      <p className="mt-1">{price.description}</p>
      <p className="mt-1">{huf.format(price.baselinePrice)} × {price.coff.toFixed(6)} ≈ {huf.format(price.estimatedPrice)} / {units[price.unit]}</p>
      <p className="mt-1">Retrieved {displayDate(price.retrievedAt)}{price.observedAt ? ` · Published for ${displayDate(price.observedAt)}` : ''}</p>
      {safeSource(price.sourceUrl) && <a href={safeSource(price.sourceUrl)} target="_blank" rel="noreferrer" className="mt-1 inline-block underline">Published benchmark</a>}
      {Array.isArray(price.evidence?.components) && <ul className="mt-2 space-y-1">{price.evidence.components.map((component) => <li key={component.id}>
        {component.description} · {huf.format(Number(component.price))}{' '}
        {safeSource(component.sourceUrl) && <a href={safeSource(component.sourceUrl)} target="_blank" rel="noreferrer" className="underline">Source</a>}
      </li>)}</ul>}
    </details>
  </div>;
}
