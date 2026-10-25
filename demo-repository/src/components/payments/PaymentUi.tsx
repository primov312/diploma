import { Link, NavLink } from 'react-router-dom';
import { formatCurrency } from '../../utils/format';
import type { Schedule } from '../../api/payments';
export const money = (value: string | number) => formatCurrency(Number(value));
/** Payment confirmations use the same HUF display; the exact USD amount stays in the ledger. */
export const exactMoney = money;
export const PaymentStatus = ({ value }: { value: string }) => <span className={`inline-flex rounded border px-2 py-1 text-xs font-semibold ${value === 'OVERDUE' ? 'border-error-200 text-error-700' : value === 'PAID' || value === 'SETTLED_EARLY' ? 'border-success-200 text-success-700' : 'border-gray-200 text-gray-700'}`}>{value.toLowerCase().replaceAll('_', ' ')}</span>;
export const TopUpLink = () => <Link to="/balance#top-up" className="font-semibold underline">Add demo money</Link>;

/** Balance, Repayments and Activity are one "Wallet" area with tabs instead of three menu items. */
export const WalletTabs = () => <nav aria-label="Wallet" className="mb-6 flex gap-1 overflow-x-auto border-b border-gray-200">
  {[['/balance', 'Balance'], ['/repayments', 'Repayments'], ['/history', 'Activity']].map(([to, label]) =>
    <NavLink key={to} to={to} className={({ isActive }) => `-mb-px flex min-h-11 items-center border-b-2 px-4 text-sm font-semibold ${isActive ? 'border-primary text-primary' : 'border-transparent text-gray-600 hover:text-gray-900'}`}>{label}</NavLink>)}
</nav>;

export function ScheduleReview({ schedule }: { schedule: Schedule }) {
  return <div className="space-y-4">
    <dl className="grid gap-4 sm:grid-cols-3">
      <div><dt className="text-sm text-gray-500">Purchase</dt><dd className="text-lg font-semibold tabular-nums">{money(schedule.principal)}</dd></div>
      <div><dt className="text-sm text-gray-500">Interest ({Number(schedule.annualRate) * 100}% a year)</dt><dd className="text-lg font-semibold tabular-nums">{money(schedule.totalInterest)}</dd></div>
      <div><dt className="text-sm text-gray-500">You repay</dt><dd className="text-lg font-semibold tabular-nums">{money(schedule.totalRepayment)}</dd></div>
    </dl>
    <div role="region" aria-label="Quoted repayment schedule" tabIndex={0} className="max-h-72 overflow-auto">
      <table className="w-full text-left text-sm tabular-nums"><caption className="sr-only">{schedule.installmentCount} {schedule.termUnit.toLowerCase()}ly installments</caption>
        <thead><tr className="border-b text-gray-500"><th className="p-2">#</th><th className="p-2">Due</th><th className="p-2 text-right">Amount</th></tr></thead>
        <tbody>{schedule.installments.map(p => <tr key={p.sequence} className="border-b border-gray-100" title={`Principal ${money(p.principal)} · interest ${money(p.interest)}`}><td className="p-2">{p.sequence}</td><td className="p-2 whitespace-nowrap">{p.dueDate}</td><td className="p-2 text-right font-semibold">{money(p.total)}</td></tr>)}</tbody>
      </table>
    </div>
    <p className="text-sm text-gray-600">Payments are taken from your balance on each due date. You can pay early or settle the whole credit with future interest removed. No fees.</p>
  </div>;
}
