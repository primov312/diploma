import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { paymentsApi } from '../../api/payments';
import { useFinancial, usePaymentAction } from '../../hooks/useFinancial';
import { Card, Empty, Loading, Notice, Page } from '../../components/app/Ui';
import { money, WalletTabs } from '../../components/payments/PaymentUi';
import ActivityHistory from '../../components/payments/ActivityHistory';
import WalletCharts from '../../components/payments/WalletCharts';
import { formatHuf, hufToUsd } from '../../utils/format';

const QUICK_AMOUNTS = [10_000, 25_000, 50_000];

export default function BalancePage() {
  const wallet = useFinancial(paymentsApi.wallet);
  const [range, setRange] = useState('30d'), [amount, setAmount] = useState(''), [done, setDone] = useState(false);
  const chart = useFinancial(() => paymentsApi.chart(range), [range]);
  const action = usePaymentAction();
  const [params] = useSearchParams();
  const candidate = params.get('returnTo');
  const returnTo = candidate?.startsWith('/marketplace') && !candidate.includes('\\') ? candidate : null;
  const usd = hufToUsd(Number(amount));
  const w = wallet.data;
  const shortfall = w && Number(w.nextPayment ?? 0) > Number(w.balance) ? Number(w.nextPayment) - Number(w.balance) : 0;

  return <Page title="Wallet">
    <WalletTabs />
    {wallet.loading && <Loading />}
    {wallet.error && <Notice tone="error">{wallet.error.message} <button className="underline" onClick={wallet.reload}>Retry</button></Notice>}
    <div className="mb-6 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
      <Card title="Available balance" icon="wallet">{w && <>
        <p className="text-4xl font-semibold tabular-nums text-primary">{money(w.balance)}</p>
        <dl className="mt-6 grid gap-4 border-t border-gray-200 pt-5 sm:grid-cols-3">
          <div><dt className="text-sm text-gray-500">Next repayment</dt><dd className="mt-1 font-semibold">{w.nextPayment ? money(w.nextPayment) : 'None'}</dd>{w.nextDueDate && <dd className="text-sm text-gray-500">{w.nextDueDate}</dd>}</div>
          <div><dt className="text-sm text-gray-500">Overdue</dt><dd className={`mt-1 font-semibold ${Number(w.overdue) > 0 ? 'text-error-700' : ''}`}>{money(w.overdue)}</dd></div>
          <div><dt className="text-sm text-gray-500">Left to repay</dt><dd className="mt-1 font-semibold">{money(w.outstanding)}</dd></div>
        </dl>
        {shortfall > 0 && <div className="mt-4"><Notice tone="warning">Add {money(shortfall)} to cover your next repayment.</Notice></div>}
        <p className="mt-4 text-sm"><Link to="/repayments" className="font-semibold underline">See repayment schedule</Link></p>
      </>}</Card>
      <Card title="Add demo money" icon="wallet"><form id="top-up" className="space-y-4" onSubmit={async e => { e.preventDefault(); setDone(false); const result = await action.run(`topup:${usd}`, key => paymentsApi.topUp(usd.toFixed(2), key)); if (result) { setDone(true); setAmount(''); } }}>
        <div className="flex flex-wrap gap-2" role="group" aria-label="Quick amounts">{QUICK_AMOUNTS.map(value =>
          <button key={value} type="button" className={`min-h-11 rounded-full border px-4 text-sm font-medium ${Number(amount) === value ? 'border-primary bg-primary text-white' : 'border-gray-300 bg-white hover:border-primary'}`} onClick={() => { setAmount(String(value)); setDone(false); }}>{formatHuf(value)}</button>)}</div>
        <label className="block text-sm font-medium">Amount (HUF)<input type="number" inputMode="numeric" min="2" step="1" required value={amount} onChange={e => { setAmount(e.target.value); setDone(false); }} className="form-input mt-1 w-full" /></label>
        {action.error && <Notice tone="error">{action.error}</Notice>}
        {done && <Notice tone="info">Money added. {returnTo && <Link className="underline" to={returnTo}>Back to your purchase</Link>}</Notice>}
        <button className="btn-primary w-full disabled:opacity-50" disabled={action.pending || !Number.isFinite(usd) || usd <= 0}>{action.pending ? 'Adding…' : 'Add money'}</button>
      </form></Card>
    </div>
    <Card title="Trends" icon="chart" className="mb-6">
      <div className="mb-5 flex gap-2" role="group" aria-label="Chart range">{[['30d', '30 days'], ['90d', '90 days'], ['1y', '1 year']].map(([value, label]) =>
        <button key={value} aria-pressed={value === range} className={`min-h-11 rounded-full border px-4 text-sm font-medium ${value === range ? 'border-primary bg-primary text-white' : 'border-gray-300 bg-white'}`} onClick={() => setRange(value)}>{label}</button>)}</div>
      {chart.loading && <Loading />}{chart.error && <Notice tone="error">{chart.error.message}</Notice>}
      {chart.data && (chart.data.points.length ? <WalletCharts data={chart.data} /> : <Empty>Your chart starts after your first top-up.</Empty>)}
    </Card>
    <Card title="Recent activity" icon="clock"><ActivityHistory compact /></Card>
  </Page>;
}
