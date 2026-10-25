import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { paymentsApi, type SettlementQuote } from '../../api/payments';
import { partnersApi } from '../../api/rocket';
import { useFinancial, usePaymentAction } from '../../hooks/useFinancial';
import { useApi } from '../../hooks/useApi';
import { Card, Empty, Loading, Notice, Page } from '../../components/app/Ui';
import LocalDropdown from '../../components/common/LocalDropdown';
import { money, PaymentStatus, TopUpLink, WalletTabs } from '../../components/payments/PaymentUi';

const STATUSES: [string, string][] = [['UNPAID', 'To pay'], ['OVERDUE', 'Overdue'], ['PAID', 'Paid'], ['ALL', 'All']];

function LoanDetails({ id }: { id: number }) {
  const loan = useFinancial(() => paymentsApi.loan(id), [id]);
  const action = usePaymentAction(); const [quote, setQuote] = useState<SettlementQuote>(), [message, setMessage] = useState('');
  if (loan.error) return <Notice tone="error">{loan.error.message}</Notice>;
  if (!loan.data) return <Loading />;
  const l = loan.data;
  const unpaid = l.installments.filter(i => !i.paidAt);
  return <Card title={`${l.productName ?? 'Credit'} · ${l.partnerName}`} icon="file"><div className="space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-gray-600">Credit #{id} · {l.installmentCount} {l.termUnit === 'WEEK' ? 'weekly' : 'monthly'} payments · {Number(l.annualRate) * 100}% a year · <Link className="underline" to={`/applications/${l.applicationId}`}>decision</Link></p><PaymentStatus value={l.status} /></div>
    <dl className="grid gap-4 text-sm sm:grid-cols-3"><div><dt className="text-gray-500">Borrowed</dt><dd className="text-lg font-semibold">{money(l.principal)}</dd></div><div><dt className="text-gray-500">Principal left</dt><dd className="text-lg font-semibold">{money(unpaid.reduce((sum, i) => sum + Number(i.principal), 0))}</dd></div><div><dt className="text-gray-500">Interest left</dt><dd className="text-lg font-semibold">{money(unpaid.reduce((sum, i) => sum + Number(i.interest), 0))}</dd></div></dl>
    {l.status === 'ACTIVE' && !quote && <button className="btn-secondary" disabled={action.pending} onClick={async () => { setMessage(''); const result = await action.run(`quote:${id}`, () => paymentsApi.settlementQuote(id)); if (result) setQuote(result); }}>Pay off early</button>}
    {quote && l.status === 'ACTIVE' && <div className="space-y-3 rounded-md border border-primary-200 bg-primary-50 p-4">
      <p className="text-lg font-semibold">Pay {money(quote.total)} today to close this credit</p>
      <p className="text-sm text-gray-700">You save {money(quote.waivedInterest)} of future interest. Offer valid until {new Date(quote.expiresAt).toLocaleTimeString()}.</p>
      <div className="flex flex-wrap gap-3"><button className="btn-primary disabled:opacity-50" disabled={action.pending} onClick={async () => { const result = await action.run(`settle:${id}:${quote.id}`, key => paymentsApi.settle(id, quote.id, key)); if (result) { setQuote(undefined); setMessage('Credit closed. Nothing more will be collected.'); } }}>{action.pending ? 'Paying…' : 'Confirm'}</button><button className="btn-secondary" disabled={action.pending} onClick={() => setQuote(undefined)}>Cancel</button></div>
    </div>}
    {action.error && <Notice tone="error">{action.error} <TopUpLink /></Notice>}{message && <Notice tone="info">{message}</Notice>}
  </div></Card>;
}

export default function RepaymentsPage() {
  const [params, setParams] = useSearchParams(); const loanId = params.get('loanId') ?? '';
  const [partner, setPartner] = useState(''), [status, setStatus] = useState('UNPAID'), [page, setPage] = useState(0);
  const [confirm, setConfirm] = useState<number>(), [success, setSuccess] = useState('');
  const list = useFinancial(() => paymentsApi.repayments({ loanId, partner, status, page }), [loanId, partner, status, page]);
  const partners = useApi(partnersApi.list, []); const action = usePaymentAction();
  const update = (set: (v: string) => void) => (v: string) => { set(v); setPage(0); setConfirm(undefined); };
  const validLoan = Number.isSafeInteger(Number(loanId)) && Number(loanId) > 0;
  return <Page title="Wallet">
    <WalletTabs />
    <div className="space-y-6">
      {validLoan && <><LoanDetails key={loanId} id={Number(loanId)} /><button className="text-sm underline" onClick={() => { const next = new URLSearchParams(params); next.delete('loanId'); setParams(next); }}>Show all credits</button></>}
      <Card title={validLoan ? `Payments for credit #${loanId}` : 'Upcoming and past payments'} icon="clock">
        <div className="mb-5 flex flex-wrap items-center gap-2">
          <div className="flex flex-wrap gap-2" role="group" aria-label="Status">{STATUSES.map(([value, label]) =>
            <button key={value} type="button" aria-pressed={status === value} onClick={() => update(setStatus)(value)} className={`min-h-11 rounded-full border px-4 text-sm font-medium ${status === value ? 'border-primary bg-primary text-white' : 'border-gray-300 bg-white hover:border-primary'}`}>{label}</button>)}</div>
          <LocalDropdown id="repayment-store" aria-label="Seller" value={partner} onValueChange={update(setPartner)} className="w-44 sm:ml-auto" placeholder="All sellers" options={[{ value: '', label: 'All sellers' }, ...(partners.data?.map(p => ({ value: p.slug, label: p.displayName })) ?? [])]} />
        </div>
        {success && <div className="mb-4"><Notice tone="info">{success}</Notice></div>}{action.error && <div className="mb-4"><Notice tone="error">{action.error} <TopUpLink /></Notice></div>}
        {list.loading && <Loading />}{list.error && <Notice tone="error">{list.error.message} <button className="underline" onClick={list.reload}>Retry</button></Notice>}
        {list.data && (list.data.items.length === 0 ? <Empty>Nothing here. {status !== 'ALL' && <button className="underline" onClick={() => update(setStatus)('ALL')}>Show all payments</button>}</Empty> :
          <div role="region" aria-label="Repayment installments" tabIndex={0} className="overflow-x-auto"><table className="w-full text-left text-sm">
            <thead><tr className="text-xs uppercase text-gray-500"><th className="p-3">Due</th><th className="p-3">Credit</th><th className="p-3 text-right">Amount</th><th className="p-3">Status</th><th className="p-3"><span className="sr-only">Action</span></th></tr></thead>
            <tbody>{list.data.items.map(i => <tr key={i.id} className="border-t border-gray-100">
              <td className="p-3 whitespace-nowrap">{i.dueDate}</td>
              <td className="p-3"><Link className="font-medium underline-offset-2 hover:underline" to={`/repayments?loanId=${i.loanId}`}>{i.productName ?? `Credit #${i.loanId}`}</Link><div className="text-gray-500">{i.partnerName} · payment {i.sequence}</div></td>
              <td className="p-3 text-right font-semibold tabular-nums" title={`Principal ${money(i.principal)} · interest ${money(i.interest)}`}>{money(i.total)}</td>
              <td className="p-3"><PaymentStatus value={i.status} /></td>
              <td className="p-3 text-right">{i.canPay && (confirm === i.id
                ? <div className="flex flex-wrap justify-end gap-2"><button className="btn-primary" disabled={action.pending} onClick={async () => { const result = await action.run(`pay:${i.id}`, key => paymentsApi.pay(i.id, key)); if (result) { setConfirm(undefined); setSuccess(`Payment ${i.sequence} done.`); } }}>{action.pending ? 'Paying…' : `Pay ${money(i.total)}`}</button><button className="btn-secondary" disabled={action.pending} onClick={() => setConfirm(undefined)}>Cancel</button></div>
                : <button className="btn-secondary whitespace-nowrap" onClick={() => { setConfirm(i.id); setSuccess(''); }}>Pay now</button>)}</td>
            </tr>)}</tbody>
          </table></div>)}
        {list.data && list.data.total > 25 && <div className="mt-4 flex items-center justify-between text-sm"><button className="btn-secondary disabled:opacity-50" disabled={!page} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {Math.ceil(list.data.total / 25)}</span><button className="btn-secondary disabled:opacity-50" disabled={(page + 1) * 25 >= list.data.total} onClick={() => setPage(page + 1)}>Next</button></div>}
      </Card>
    </div>
  </Page>;
}
