import { useEffect, useMemo, useState } from 'react';
import { meApi } from '../../api/rocket';
import type { AffordabilityEstimate, AffordabilityHistoryPoint } from '../../api/types';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency } from '../../utils/format';
import { Card, Loading, Notice, SyntheticTag } from '../app/Ui';
import LocalDropdown from '../common/LocalDropdown';

const monthName = (month: string) => {
  const [year, value] = month.split('-').map(Number);
  return new Intl.DateTimeFormat(undefined, { month: 'short', year: '2-digit', timeZone: 'UTC' }).format(new Date(Date.UTC(year, value - 1, 1)));
};

export function AffordabilityDashboardCard({ refreshKey }: { refreshKey: number }) {
  const estimate = useApi(() => meApi.affordability(), [refreshKey]);
  const [months, setMonths] = useState(12);
  const history = useApi(() => meApi.affordabilityHistory(months), [months, refreshKey]);
  const [partner, setPartner] = useState('BASE');

  useEffect(() => {
    if (estimate.status !== 'ready' || estimate.data.status !== 'UPDATING') return;
    const timer = window.setInterval(() => { estimate.reload(); history.reload(); }, 1800);
    return () => window.clearInterval(timer);
  }, [estimate.status, estimate.status === 'ready' ? estimate.data.status : null]);

  const points = history.status === 'ready' ? history.data : [];
  const current = estimate.status === 'ready' ? estimate.data : null;
  const selectedPartner = partner === 'BASE' ? null : current?.partners.find((item) => item.slug === partner);
  const displayedValue = current == null ? null : partner === 'BASE' ? current.baseAmount : selectedPartner?.possibleAmount ?? null;
  const valueFor = (point: AffordabilityHistoryPoint) => partner === 'BASE' ? point.amount : point.partnerAmounts[partner] ?? null;
  const max = Math.max(1, ...points.map((point) => valueFor(point) ?? 0));
  const coords = useMemo(() => points.map((point, index) => point.amount == null ? null : ({
    x: points.length === 1 ? 360 : 28 + index * (664 / (points.length - 1)),
    y: 206 - ((valueFor(point) ?? 0) / max) * 174,
    point,
  })), [points, max, partner]);
  // Start a fresh line after each gap in the monthly series.
  const chartSegments: { x: number; y: number }[][] = [];
  coords.forEach((point) => {
    if (!point) return;
    if (!chartSegments.length || coords[coords.indexOf(point) - 1] == null) chartSegments.push([]);
    chartSegments[chartSegments.length - 1].push({ x: point.x, y: point.y });
  });

  return <Card icon="chart" title="Estimated affordable amount" className="lg:col-span-3">
    {estimate.status === 'loading' && <Loading label="Loading estimate…" />}
    {estimate.status === 'error' && <Notice tone="error">{errorMessage(estimate.error)}</Notice>}
    {estimate.status === 'ready' && current && <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-3xl font-bold sm:text-4xl text-gray-900">{displayedValue == null ? 'Unavailable' : formatCurrency(displayedValue)}</p>
          <p className="mt-1 text-sm text-gray-600">{partner === 'BASE' ? 'Before partner caps' : `${partner} partner amount`} · up to 6 monthly payments</p>
        </div>
        <label className="text-sm font-medium text-gray-700">View
          <LocalDropdown aria-label="View" value={partner} onValueChange={setPartner} className="ml-2 min-w-48"
            options={[{ value: 'BASE', label: 'Base amount' }, ...current.partners.map((item) => ({ value: item.slug, label: item.slug }))]} />
        </label>
      </div>
      {current.status === 'UPDATING' && <Notice>Updating from revision {current.financialRevision}… {current.stale ? 'The amount below is the last saved estimate.' : ''}</Notice>}
      {current.status === 'ERROR' && <Notice tone="error">The latest calculation failed. {current.stale ? 'Showing the prior estimate.' : 'No estimate is available yet.'}</Notice>}
      {current.status === 'UNAVAILABLE' && <Notice tone="warning">No complete supplied financial profile or automatic budget is available. Verify your address and check that price references have been imported.</Notice>}
      <div className="mt-4"><SyntheticTag /></div>
      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <div className="rounded-md bg-success-50 p-4"><div className="text-xs uppercase tracking-wide text-success-700">Monthly payment capacity</div><div className="mt-1 text-xl font-semibold text-success-700">{current.monthlyPaymentCapacity == null ? '—' : formatCurrency(current.monthlyPaymentCapacity)}</div></div>
        <div className="rounded-md bg-gray-50 p-4"><div className="text-xs uppercase tracking-wide text-gray-500">Calculated</div><div className="mt-1 text-sm font-medium text-gray-800">{current.calculatedAt ? new Date(current.calculatedAt).toLocaleString() : 'Not calculated yet'}</div></div>
      </div>
      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <h3 className="font-semibold text-gray-800">Monthly history</h3>
        <div className="flex gap-2" aria-label="History range">
          {[6, 12].map((n) => <button key={n} type="button" onClick={() => setMonths(n)} aria-pressed={months === n}
            className={`min-h-11 rounded-md px-3 py-2 text-sm ${months === n ? 'bg-success-700 text-white' : 'bg-gray-100 text-gray-700'}`}>{n} months</button>)}
        </div>
      </div>
      {history.status === 'loading' ? <Loading label="Loading monthly history…" /> : history.status === 'error' ?
        <Notice tone="error">{errorMessage(history.error)}</Notice> : <div className="mt-2">
          <svg viewBox="0 0 720 250" role="img" aria-labelledby="affordability-chart-title affordability-chart-description" className="h-auto w-full overflow-visible">
            <title id="affordability-chart-title">Monthly affordable amount history</title>
            <desc id="affordability-chart-description">Green line shows the last successful estimate in each month. Missing months appear as gaps.</desc>
            {[0, 1, 2, 3].map((i) => <line key={i} x1="24" x2="696" y1={32 + i * 58} y2={32 + i * 58} stroke="#e5e7eb" strokeDasharray="4 5" />)}
            {chartSegments.map((segment, index) => <g key={index}>
              {segment.length > 1 && <polygon points={`${segment.map((p) => `${p.x},${p.y}`).join(' ')} ${segment[segment.length - 1].x},206 ${segment[0].x},206`} fill="#CFE5D7" opacity="0.8" />}
              {segment.length > 1 && <polyline points={segment.map((p) => `${p.x},${p.y}`).join(' ')} fill="none" stroke="#246544" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />}
            </g>)}
            {coords.filter((point): point is NonNullable<typeof point> => point !== null).map((point) => <circle key={point.point.month} cx={point.x} cy={point.y} r="5" tabIndex={0} role="img" aria-label={`${monthName(point.point.month)}: ${valueFor(point.point) == null ? 'no estimate' : formatCurrency(valueFor(point.point)!)}`} fill="white" stroke="#246544" strokeWidth="3"><title>{`${monthName(point.point.month)} · ${valueFor(point.point) == null ? 'No snapshot' : formatCurrency(valueFor(point.point)!)} · ${point.point.calculatedAt ? new Date(point.point.calculatedAt).toLocaleDateString() : ''}`}</title></circle>)}
            {points.map((point, index) => <text key={point.month} x={points.length === 1 ? 360 : 28 + index * (664 / (points.length - 1))} y="237" textAnchor="middle" className="fill-gray-500 text-[11px]">{monthName(point.month)}</text>)}
          </svg>
          <details className="mt-3"><summary>History values</summary><div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm"><caption className="sr-only">Accessible monthly estimate values</caption>
              <thead><tr className="border-b text-gray-500"><th className="py-2 pr-4">Month</th><th className="py-2 pr-4">{partner === 'BASE' ? 'Base estimate' : `${partner} estimate`}</th><th className="py-2">Snapshot</th></tr></thead>
              <tbody>{points.map((point) => <tr key={point.month} className="border-b border-gray-100"><th scope="row" className="py-2 pr-4 font-medium">{monthName(point.month)}</th><td className="py-2 pr-4">{valueFor(point) == null ? 'No snapshot' : formatCurrency(valueFor(point)!)}</td><td className="py-2 text-gray-500">{point.calculatedAt ? new Date(point.calculatedAt).toLocaleDateString() : '—'}</td></tr>)}</tbody>
            </table>
          </div></details>
        </div>}
      {current.breakdown && Object.keys(current.breakdown).length > 0 && <details className="mt-5 rounded-md border border-gray-200 p-4">
        <summary className="cursor-pointer font-semibold text-gray-800">How this estimate was calculated</summary>
        <p className="mt-3 text-xs text-gray-500">Formula {current.formulaVersion ?? 'Pending'} · Policy {current.policyVersion ?? '—'}</p>
        <dl className="mt-4 grid gap-x-8 gap-y-2 text-sm sm:grid-cols-2">{Object.entries(current.breakdown).map(([key, value]) => <div key={key} className="flex justify-between gap-4"><dt className="text-gray-600">{key.replace(/[A-Z]/g, (letter) => ` ${letter.toLowerCase()}`)}</dt><dd className="font-medium">{formatCurrency(value)}</dd></div>)}</dl>
        <p className="mt-3 text-xs text-gray-500">Demo formula: reserve 10%, payment capacity is limited to 50% of remaining disposable income and 30% of income for total debt service; six payments, zero interest and fees.</p>
      </details>}
    </>}
  </Card>;
}
