// AI-assisted (Claude), 2026; reviewed by the author.
import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { adminApi, type ReviewDetail } from '../../api/staff';
import { Card, LinkButton, Loading, Notice, Page, StatusBadge } from '../../components/app/Ui';
import { Money, Pill, dateTime, termText } from '../../components/app/Staff';
import { Icon } from '../../components/common/Icon';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency, formatUsd } from '../../utils/format';
import { FACTOR_LABEL, isBudgetNote, reasonText } from '../../utils/reasons';

type Outcome = 'APPROVED' | 'REJECTED';

/**
 * Decision form with a confirmation step. The limits mirror the server: an approval is above 0 and at most
 * the smaller of the possible and the requested amount; the note has 10 to 500 characters.
 */
const DecisionForm = ({ review, onDecided }: { review: ReviewDetail; onDecided: (r: ReviewDetail) => void }) => {
  const max = Math.min(review.possibleAmount, review.requestedAmount);
  const [outcome, setOutcome] = useState<Outcome>('APPROVED');
  const [amount, setAmount] = useState(max.toFixed(2));
  const [note, setNote] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const value = Number(amount);
  const amountError = outcome === 'APPROVED' && (!/^\d+(\.\d{1,2})?$/.test(amount.trim()) || !(value > 0) || value > max)
    ? `Enter an amount above 0 and at most ${formatUsd(max)}.` : null;
  const noteError = note.trim().length < 10 || note.trim().length > 500 ? 'Write 10 to 500 characters explaining the decision.' : null;

  const review_ = (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!amountError && !noteError) setConfirming(true);
  };
  const send = async () => {
    setSending(true);
    setError(null);
    try {
      onDecided(await adminApi.decide(review.applicationId, { outcome, note: note.trim(), ...(outcome === 'APPROVED' ? { approvedAmount: value } : {}) }));
    } catch (e) {
      setConfirming(false);
      setError(e instanceof ApiError && e.code === 'ALREADY_REVIEWED' ? 'Someone has already decided this request. Reload the page to see the decision.'
        : e instanceof Error ? e.message : 'The decision was not saved.');
    } finally {
      setSending(false);
    }
  };

  return <Card title="Your decision" icon="check">
    <form onSubmit={review_} noValidate className="space-y-4">
      <fieldset disabled={confirming || sending}>
        <legend className="text-sm font-medium text-gray-700">Outcome</legend>
        <div className="mt-2 flex flex-wrap gap-4">
          {(['APPROVED', 'REJECTED'] as const).map((o) => <label key={o} className="flex min-h-11 items-center gap-2 text-sm">
            <input type="radio" name="outcome" value={o} checked={outcome === o} onChange={() => setOutcome(o)} />{o === 'APPROVED' ? 'Approve' : 'Reject'}
          </label>)}
        </div>
        {outcome === 'APPROVED' && <div className="mt-4">
          <label htmlFor="approvedAmount" className="block text-sm font-medium text-gray-700">Approved amount (USD)</label>
          <input id="approvedAmount" inputMode="decimal" className="form-input mt-1 w-full max-w-xs" value={amount} onChange={(e) => setAmount(e.target.value)}
            aria-invalid={Boolean(amountError)} aria-describedby="approvedAmount-help" />
          <p id="approvedAmount-help" className={amountError ? 'mt-1 text-xs text-error-700' : 'mt-1 text-xs text-gray-500'}>
            {amountError ?? `At most ${formatUsd(max)} (${formatCurrency(max)}): the smaller of the possible and the requested amount.`}</p>
        </div>}
        <div className="mt-4">
          <label htmlFor="note" className="block text-sm font-medium text-gray-700">Note to the customer</label>
          <textarea id="note" rows={3} maxLength={500} className="form-input mt-1 w-full" value={note} onChange={(e) => setNote(e.target.value)}
            aria-invalid={Boolean(error && noteError)} aria-describedby="note-help" />
          <p id="note-help" className="mt-1 text-xs text-gray-500">{note.trim().length} / 500 characters, at least 10. The customer sees this note.</p>
        </div>
      </fieldset>
      {!confirming && <button type="submit" className="btn-primary" disabled={Boolean(amountError || noteError)}>Review decision</button>}
      {confirming && <div className="rounded-md border border-warning-100 bg-warning-50 p-4 text-sm" role="alertdialog" aria-labelledby="confirm-title">
        <p id="confirm-title" className="font-semibold text-gray-800">
          {outcome === 'APPROVED' ? `Approve ${formatCurrency(value)} (${formatUsd(value)}) for request #${review.applicationId}?` : `Reject request #${review.applicationId}?`}
        </p>
        <p className="mt-1 text-gray-700">This decision is final and cannot be changed. It is recorded in the audit log.</p>
        <div className="mt-3 flex flex-wrap gap-3">
          <button type="button" className="btn-primary" onClick={send} disabled={sending} autoFocus>{sending ? 'Saving…' : 'Confirm decision'}</button>
          <button type="button" className="btn-secondary" onClick={() => setConfirming(false)} disabled={sending}>Go back</button>
        </div>
      </div>}
      {error && <Notice tone="error">{error}</Notice>}
    </form>
  </Card>;
};

const AdminReviewDetailPage = () => {
  const { applicationId } = useParams();
  const loaded = useApi(() => adminApi.review(applicationId!), [applicationId]);
  const [decided, setDecided] = useState<ReviewDetail | null>(null);
  const back = <LinkButton to="/admin/reviews" variant="secondary">All reviews</LinkButton>;

  if (loaded.status === 'loading') return <Page title="Review"><Loading /></Page>;
  if (loaded.status === 'error') return <Page title="Review" actions={back}>
    <Notice tone="error">{loaded.error instanceof ApiError && loaded.error.status === 404 ? 'There is no review for this request.' : errorMessage(loaded.error)}</Notice></Page>;
  const r = decided ?? loaded.data;
  const reasons = r.reasons.filter((code) => !isBudgetNote(code));

  return <Page title={`Request #${r.applicationId}`} subtitle={`${r.productName ?? 'Custom amount'} · ${r.partnerName} · customer #${r.customerId} · ${dateTime(r.createdAt)}`} actions={back}>
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        <Card title="Automatic result" icon="file">
          <div className="flex flex-wrap items-center gap-3"><StatusBadge status="REVIEW" /><span className="text-sm text-gray-600">score {r.score.toFixed(2)} · policy {r.policyVersion}{r.modelVersion ? ` · model ${r.modelVersion}` : ''}</span></div>
          <dl className="mt-4 grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
            <div><dt className="text-gray-500">Requested</dt><dd className="font-semibold"><Money usd={r.requestedAmount} /></dd></div>
            <div><dt className="text-gray-500">Possible amount</dt><dd className="font-semibold"><Money usd={r.possibleAmount} /></dd></div>
            {r.plan && <div><dt className="text-gray-500">Chosen plan</dt><dd className="font-semibold">{termText(r.plan.termUnit, r.plan.installmentCount)}
              <span className="block text-xs font-normal text-gray-500">{(Number(r.plan.annualRate) * 100).toFixed(1)}% a year · total {formatCurrency(Number(r.plan.totalRepayment))}</span></dd></div>}
          </dl>
          <h3 className="mt-5 text-sm font-semibold text-gray-800">What the customer was told</h3>
          {reasons.length === 0 ? <p className="mt-2 text-sm text-gray-600">No reasons were given.</p>
            : <ul className="mt-2 space-y-2 text-sm">{reasons.map((code) => <li key={code} className="flex gap-2 text-gray-700">
              <Icon name="info" className="mt-0.5 h-5 w-5 shrink-0 text-gray-500" /><span>{reasonText(code)} <code className="text-xs text-gray-500">{code}</code></span></li>)}</ul>}
        </Card>
        {r.decision
          ? <Card title="Decision" icon={r.decision.outcome === 'APPROVED' ? 'check' : 'close'}>
              <p className="flex flex-wrap items-center gap-3"><Pill value={r.decision.outcome} />
                {r.decision.approvedAmount != null && <strong>{formatCurrency(r.decision.approvedAmount)} ({formatUsd(r.decision.approvedAmount)})</strong>}</p>
              <p className="mt-3 text-sm text-gray-700"><span className="font-medium">Note:</span> {r.decision.note}</p>
              <p className="mt-2 text-xs text-gray-500">Decided by {r.decision.reviewerEmail} on {dateTime(r.decision.decidedAt)}.
                {r.accepted ? ' The customer has accepted the offer.' : r.decision.outcome === 'APPROVED' ? ' The customer has not accepted the offer yet.' : ''}</p>
            </Card>
          : <DecisionForm review={r} onDecided={setDecided} />}
      </div>
      <Card title="Score breakdown" icon="chart">
        <ul className="space-y-4 text-sm">{Object.entries(r.factors).map(([key, f]) => <li key={key}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-medium text-gray-800">{FACTOR_LABEL[key] ?? key}</span>
            <span className="tabular-nums text-gray-600">{f.score === null ? 'no score' : `${Math.round(f.score * 100)} / 100`}</span>
          </div>
          {f.score !== null && <div className="mt-1 h-2 rounded-full bg-gray-100" aria-hidden="true"><div className="h-2 rounded-full bg-accent" style={{ width: `${Math.round(f.score * 100)}%` }} /></div>}
          {f.weight > 0 && <div className="mt-1 text-xs text-gray-500">weight {Math.round(f.weight * 100)}%</div>}
          {f.reasons && f.reasons.length > 0 && <div className="mt-1 text-xs text-gray-500">{f.reasons.join(', ')}</div>}
        </li>)}</ul>
        <p className="mt-4 text-xs text-gray-500">Income, costs, address, digital footprint and consents are not shown here; the reviewer decides from the result, its reasons and these scores.</p>
      </Card>
    </div>
  </Page>;
};

export default AdminReviewDetailPage;
