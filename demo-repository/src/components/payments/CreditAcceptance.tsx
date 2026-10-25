import { useNavigate, Link } from 'react-router-dom';
import type { Application } from '../../api/types';
import { paymentsApi } from '../../api/payments';
import { usePaymentAction } from '../../hooks/useFinancial';
import { Card, Notice } from '../app/Ui';
import { ScheduleReview } from './PaymentUi';
import { usdToHuf } from '../../utils/format';

/** Approved requests become credit only when the customer accepts the quoted schedule here. */
export default function CreditAcceptance({ application: a }: { application: Application }) {
  const action = usePaymentAction(); const navigate = useNavigate();
  if (!a.quote || (a.decisionStatus !== 'APPROVED' && !a.loanId)) return null;
  const retry = `/apply?partner=${a.partnerSlug}&${a.productId ? `product=${a.productId}` : `amount=${usdToHuf(a.requestedAmount)}`}`;
  const accept = async () => { const result = await action.run(`accept:${a.id}`, key => paymentsApi.accept(a.id, key)); if (result?.loanId) navigate(`/repayments?loanId=${result.loanId}`); };
  const open = a.decisionStatus === 'APPROVED' && !a.loanId;
  return <Card title={a.loanId ? 'Your credit' : 'Repayment plan'} icon="clock" className="mb-6">
    {a.loanId
      ? <Link to={`/repayments?loanId=${a.loanId}`} className="btn-primary">View repayments</Link>
      : <details open={open || undefined} className="border-0 pt-0"><summary>{open ? 'Your payments' : 'Quoted payments'}</summary><ScheduleReview schedule={a.quote.schedule} /></details>}
    {!a.loanId && a.canAccept && <div className="mt-5 flex flex-wrap items-center gap-3">
      <button className="btn-primary disabled:opacity-50" disabled={action.pending} onClick={accept}>{action.pending ? 'Accepting…' : 'Accept and pay seller'}</button>
      <span className="text-sm text-gray-600">{a.partnerName} is paid with credit; your balance does not change.</span>
    </div>}
    {!a.loanId && !a.canAccept && a.decisionStatus === 'APPROVED' && <div className="mt-5"><Notice tone="warning">{a.acceptanceUnavailableReason ?? 'This offer has expired.'} <Link className="underline" to={retry}>Apply again</Link></Notice></div>}
    {action.error && <div className="mt-3"><Notice tone="error">{action.error}</Notice></div>}
  </Card>;
}
