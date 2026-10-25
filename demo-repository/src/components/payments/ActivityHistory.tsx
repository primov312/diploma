import { useState } from 'react';
import { Link } from 'react-router-dom';
import { paymentsApi, type Activity } from '../../api/payments';
import { partnersApi } from '../../api/rocket';
import { useFinancial } from '../../hooks/useFinancial';
import { useApi } from '../../hooks/useApi';
import { Empty, Loading, Notice } from '../app/Ui';
import LocalDropdown from '../common/LocalDropdown';
import { money, PaymentStatus } from './PaymentUi';

const KINDS: Record<string, string> = { TOP_UP: 'Top-up', PURCHASE: 'Purchase', FINANCED_PURCHASE: 'Purchase on credit', REPAYMENT: 'Repayment', EARLY_SETTLEMENT: 'Early settlement' };
const kindLabel = (kind: string) => KINDS[kind] ?? kind.toLowerCase().replaceAll('_', ' ');

export function ActivityTable({ items }: { items: Activity[] }) {
  return <div className="overflow-x-auto" role="region" aria-label="Activity history table" tabIndex={0}>
    <table className="w-full text-left text-sm"><caption className="sr-only">Demo purchases and balance activity</caption>
      <thead><tr className="border-b text-xs uppercase tracking-wide text-gray-500"><th className="p-3">Date</th><th className="p-3">Activity</th><th className="p-3 text-right">Amount</th><th className="p-3">Status</th></tr></thead>
      <tbody className="divide-y divide-gray-100">{items.map(item => {
        const change = Number(item.balanceChange);
        return <tr key={item.id}>
          <td className="p-3 whitespace-nowrap text-gray-600">{item.date}</td>
          <td className="p-3">
            <div className="font-medium text-gray-800">{kindLabel(item.kind)}{item.partnerName ? ` · ${item.partnerName}` : ''}</div>
            <div className="text-gray-500">{item.description}{item.paidOnTime === false ? ' · paid late' : ''}</div>
            {item.loanId && <Link to={`/repayments?loanId=${item.loanId}`} className="mr-3 text-xs underline">Credit #{item.loanId}</Link>}
            {item.applicationId && <Link to={`/applications/${item.applicationId}`} className="text-xs underline">Application</Link>}
          </td>
          <td className={`p-3 text-right tabular-nums whitespace-nowrap font-medium ${change > 0 ? 'text-success-700' : ''}`}>{change === 0 ? money(item.amount) : `${change > 0 ? '+' : '−'}${money(Math.abs(change))}`}</td>
          <td className="p-3"><PaymentStatus value={item.status} /></td>
        </tr>;
      })}</tbody>
    </table>
  </div>;
}

export default function ActivityHistory({ compact = false }: { compact?: boolean }) {
  const [partner, setPartner] = useState(''), [kind, setKind] = useState(''), [from, setFrom] = useState(''), [to, setTo] = useState(''), [page, setPage] = useState(0);
  const partners = useApi(partnersApi.list, []);
  const list = useFinancial(() => paymentsApi.history({ partner, kind, from, to, page, size: compact ? 5 : 25 }), [partner, kind, from, to, page, compact]);
  const update = (set: (value: string) => void) => (value: string) => { set(value); setPage(0); };
  return <div className="space-y-4">
    {!compact && <div className="flex flex-wrap items-end gap-3">
      <label className="text-sm font-medium">Type<LocalDropdown id="history-kind" aria-label="Activity" value={kind} onValueChange={update(setKind)} placeholder="All activity" className="mt-1 w-48" options={[{ value: '', label: 'All activity' }, ...Object.entries(KINDS).map(([value, label]) => ({ value, label }))]} /></label>
      <label className="text-sm font-medium">Seller<LocalDropdown id="history-store" aria-label="Seller" value={partner} onValueChange={update(setPartner)} placeholder="All sellers" className="mt-1 w-48" options={[{ value: '', label: 'All sellers' }, ...(partners.data?.map(p => ({ value: p.slug, label: p.displayName })) ?? [])]} /></label>
      <details className="border-0 pt-0"><summary className="flex min-h-11 items-center text-sm">Dates</summary>
        <div className="flex flex-wrap gap-3">
          <label className="text-sm font-medium">From<input type="date" value={from} onChange={e => update(setFrom)(e.target.value)} className="form-input mt-1 w-full" /></label>
          <label className="text-sm font-medium">To<input type="date" min={from} value={to} onChange={e => update(setTo)(e.target.value)} className="form-input mt-1 w-full" /></label>
        </div>
      </details>
    </div>}
    {list.loading && <Loading />}{list.error && <Notice tone="error">{list.error.message} <button onClick={list.reload} className="underline">Retry</button></Notice>}
    {list.data && (list.data.items.length ? <ActivityTable items={list.data.items} /> : <Empty>No activity yet.</Empty>)}
    {compact ? <Link to="/history" className="inline-block text-sm font-semibold underline">All activity</Link> : list.data && list.data.total > 25 && <div className="flex items-center justify-between gap-3 text-sm"><button className="btn-secondary disabled:opacity-50" disabled={!page} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {Math.ceil(list.data.total / 25)}</span><button className="btn-secondary disabled:opacity-50" disabled={(page + 1) * 25 >= list.data.total} onClick={() => setPage(page + 1)}>Next</button></div>}
  </div>;
}
